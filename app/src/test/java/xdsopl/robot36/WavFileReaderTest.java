package xdsopl.robot36;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import static org.junit.Assert.*;

public class WavFileReaderTest {
	static byte[] format(int encoding, int bits, int channels, int rate) {
		return ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
				.putShort((short) encoding).putShort((short) channels).putInt(rate)
				.putInt(rate * channels * bits / 8).putShort((short) (channels * bits / 8)).putShort((short) bits).array();
	}
	static byte[] chunk(String name, byte[] data) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(name.getBytes("US-ASCII"));
		out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.length).array());
		out.write(data);
		if ((data.length & 1) != 0) out.write(0);
		return out.toByteArray();
	}
	static byte[] riff(byte[]... chunks) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write("WAVE".getBytes("US-ASCII"));
		for (byte[] chunk : chunks) body.write(chunk);
		return chunk("RIFF", body.toByteArray());
	}
	static byte[] wav(int encoding, int bits, int channels, int rate, byte[] data) throws IOException {
		return riff(chunk("fmt ", format(encoding, bits, channels, rate)), chunk("data", data));
	}
	private float[] decode(byte[] wav, int samples) throws IOException {
		try (PcmFileReader reader = PcmFileReader.open(new ByteArrayInputStream(wav))) {
			float[] pcm = new float[samples];
			assertEquals(samples / reader.getFormat().getChannels(), reader.read(pcm));
			assertEquals(0, reader.read(new float[4]));
			return pcm;
		}
	}

	@Test public void integerPcmUsesFixedFullScaleForAllDepths() throws Exception {
		for (int bits : new int[] {8, 16, 24, 32}) {
			ByteArrayOutputStream data = new ByteArrayOutputStream();
			long[] values = bits == 8 ? new long[] {0, 128, 160} : new long[] {-(1L << (bits - 1)), 0, 1L << (bits - 3)};
			for (long value : values)
				for (int b = 0; b < bits / 8; ++b) data.write((int) (value >> (8 * b)));
			assertArrayEquals(new float[] {-1, 0, 0.25f}, decode(wav(1, bits, 1, 44100, data.toByteArray()), 3), 0);
		}
	}
	@Test public void floatingPcmKeepsScaleAndStereoLayout() throws Exception {
		for (int bits : new int[] {32, 64}) {
			ByteBuffer data = ByteBuffer.allocate(bits / 8 * 4).order(ByteOrder.LITTLE_ENDIAN);
			for (float value : new float[] {-0.75f, 0.125f, 0, 0.5f})
				if (bits == 32) data.putFloat(value); else data.putDouble(value);
			assertArrayEquals(new float[] {-0.75f, 0.125f, 0, 0.5f}, decode(wav(3, bits, 2, 48000, data.array()), 4), 0);
		}
	}
	@Test public void skipsOddUnknownChunksAndStopsAtDataBoundary() throws Exception {
		byte[] file = riff(chunk("JUNK", new byte[] {1, 2, 3}), chunk("fmt ", format(1, 8, 1, 16000)),
				chunk("LIST", new byte[9]), chunk("data", new byte[] {(byte) 128, (byte) 160, 0}), chunk("JUNK", new byte[40]));
		assertArrayEquals(new float[] {0, 0.25f, -1}, decode(file, 3), 0);
	}
	@Test public void extensiblePcmAllowsLeftAlignedValidBits() throws Exception {
		ByteBuffer fmt = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
		fmt.put(format(0xfffe, 32, 2, 48000)).putShort((short) 22).putShort((short) 24).putInt(3).putInt(1);
		fmt.put(new byte[] {0, 0, 0x10, 0, (byte) 0x80, 0, 0, (byte) 0xaa, 0, 0x38, (byte) 0x9b, 0x71});
		byte[] data = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(Integer.MIN_VALUE).putInt(1 << 29).array();
		assertArrayEquals(new float[] {-1, 0.25f}, decode(riff(chunk("fmt ", fmt.array()), chunk("data", data)), 2), 0);
	}
	@Test public void shortReadsAndPartialLastBlockNeverExposeStaleSamples() throws Exception {
		byte[] file = wav(1, 16, 2, 32000, new byte[12]);
		ByteArrayInputStream slow = new ByteArrayInputStream(file) {
			@Override public synchronized int read(byte[] b, int off, int len) { return super.read(b, off, Math.min(3, len)); }
		};
		try (PcmFileReader reader = PcmFileReader.open(slow)) {
			assertEquals(32000, reader.getFormat().getSampleRate());
			assertEquals(2, reader.getFormat().getChannels());
			float[] pcm = new float[4];
			assertEquals(2, reader.read(pcm));
			Arrays.fill(pcm, Float.NaN);
			assertEquals(1, reader.read(pcm));
			assertEquals(0, pcm[0], 0);
			assertEquals(0, pcm[1], 0);
			assertTrue(Float.isNaN(pcm[2]));
			assertEquals(0, reader.read(pcm));
		}
	}
	@Test public void rejectsPhysicalTruncationWithoutGeneratingSilence() throws Exception {
		byte[] file = wav(1, 16, 1, 8000, new byte[8]);
		try (PcmFileReader reader = PcmFileReader.open(new ByteArrayInputStream(Arrays.copyOf(file, file.length - 1)))) {
			assertThrows(EOFException.class, () -> reader.read(new float[4]));
		}
	}
	@Test public void toleratesOneOverstatedFrameAndDeliversEveryActualSample() throws Exception {
		byte[] data = ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN)
				.putShort((short) -32768).putShort((short) 0).putShort((short) 8192)
				.putShort((short) 16384).putShort((short) 0).array();
		byte[] declared = wav(1, 16, 1, 44100, data);
		byte[] file = Arrays.copyOf(declared, declared.length - 2);
		// EOF can fall within a read or immediately after a full output block.
		for (int capacity : new int[] {2, 3, 8}) {
			try (PcmFileReader reader = PcmFileReader.open(new ByteArrayInputStream(file))) {
				float[] expected = {-1, 0, 0.25f, 0.5f}, pcm = new float[capacity];
				int total = 0, frames;
				Arrays.fill(pcm, Float.NaN);
				while ((frames = reader.read(pcm)) != 0) {
					for (int i = 0; i < frames; ++i) assertEquals(expected[total++], pcm[i], 0);
					for (int i = frames; i < pcm.length; ++i) assertTrue(Float.isNaN(pcm[i]));
					Arrays.fill(pcm, Float.NaN);
				}
				assertEquals(4, total);
				assertEquals(0, reader.read(pcm));
			}
		}
	}
	@Test public void rejectsLargerTruncationAndIncompleteStereoFrames() throws Exception {
		for (int channels : new int[] {1, 2}) {
			byte[] file = wav(1, 16, channels, 44100, new byte[16]);
			int missing = channels == 1 ? 4 : 2;
			try (PcmFileReader reader = PcmFileReader.open(new ByteArrayInputStream(Arrays.copyOf(file, file.length - missing)))) {
				assertThrows(EOFException.class, () -> reader.read(new float[16]));
			}
		}
	}
	@Test public void rejectsMalformedHeadersAndUnsupportedFormats() throws Exception {
		byte[] wrongAlign = format(1, 16, 2, 48000);
		wrongAlign[12] = 2;
		byte[][] files = {
				"ID3compressed audio".getBytes("US-ASCII"),
				wav(6, 8, 1, 8000, new byte[8]),
				wav(1, 16, 3, 48000, new byte[12]),
				wav(1, 16, 1, 1, new byte[8]),
				riff(chunk("fmt ", wrongAlign), chunk("data", new byte[8])),
				wav(1, 16, 2, 48000, new byte[3]),
				riff(chunk("data", new byte[4]), chunk("fmt ", format(1, 16, 1, 8000)))
		};
		for (byte[] file : files) assertThrows(IOException.class, () -> PcmFileReader.open(new ByteArrayInputStream(file)));
	}
	@Test public void rejectsNonFiniteFloatSamples() throws Exception {
		byte[] data = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putFloat(Float.NaN).array();
		assertThrows(IOException.class, () -> decode(wav(3, 32, 1, 48000, data), 1));
	}
	@Test public void closesStreamOnUnsupportedFormat() {
		boolean[] closed = {false};
		ByteArrayInputStream stream = new ByteArrayInputStream(new byte[20]) {
			@Override public void close() { closed[0] = true; }
		};
		assertThrows(IOException.class, () -> PcmFileReader.open(stream));
		assertTrue(closed[0]);
	}
}
