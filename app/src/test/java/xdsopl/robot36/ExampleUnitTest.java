package xdsopl.robot36;

import org.junit.Test;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

/** Regression coverage for borrowed PCM buffers, WAV framing and source cancellation. */
public class ExampleUnitTest {
	private static byte[] chunk(String name, byte[] data) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write(name.getBytes(StandardCharsets.US_ASCII));
		out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.length).array());
		out.write(data);
		if ((data.length & 1) != 0) out.write(0);
		return out.toByteArray();
	}
	private static File wave(int rate, int channels, int encoding, int bits, byte[] pcm) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		body.write("WAVE".getBytes(StandardCharsets.US_ASCII));
		body.write(chunk("JUNK", new byte[] { 1, 2, 3 })); // Odd chunk and mandatory padding.
		byte[] fmt = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
				.putShort((short) encoding).putShort((short) channels).putInt(rate)
				.putInt(rate * channels * bits / 8).putShort((short) (channels * bits / 8))
				.putShort((short) bits).array();
		body.write(chunk("fmt ", fmt));
		body.write(chunk("data", pcm));
		File file = File.createTempFile("robot36-audio-", ".wav");
		file.deleteOnExit();
		try (FileOutputStream out = new FileOutputStream(file)) {
			out.write(chunk("RIFF", body.toByteArray()));
		}
		return file;
	}
	private static class Result implements AudioSource.AudioDataListener {
		final CountDownLatch done = new CountDownLatch(1);
		final List<Float> pcm = new ArrayList<>();
		final List<Integer> blocks = new ArrayList<>();
		final List<AudioSource.AudioSourceState> states = new ArrayList<>();
		IOException error;
		AudioSource source;
		Decoder decoder;
		PixelBuffer scope;
		Result(AudioSource source, boolean decode) {
			this.source = source;
			if (decode) scope = new PixelBuffer(640, 2560);
		}
		@Override public void onAudioDataAvailable(float[] buffer, int frames, int rate, int channels) {
			blocks.add(frames);
			for (int i = 0; i < frames * channels; ++i) pcm.add(buffer[i]);
			if (scope != null) {
				if (decoder == null) decoder = new Decoder(scope, new PixelBuffer(800, 616), "Raw", rate);
				decoder.process(Arrays.copyOf(buffer, frames * channels), channels == 1 ? 0 : 1);
			}
			Arrays.fill(buffer, Float.NaN); // Consumer is explicitly allowed to overwrite a borrowed block.
		}
		@Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
			states.add(state);
			if (state == AudioSource.AudioSourceState.ERROR) error = source.getFailure();
			if (state == AudioSource.AudioSourceState.COMPLETED || state == AudioSource.AudioSourceState.ERROR)
				source.release();
			if (state == AudioSource.AudioSourceState.RELEASED) done.countDown();
		}
	}
	private static Result read(File file, AudioSource.DecodePacing pacing, boolean decode) throws Exception {
		FileAudioSource source = new FileAudioSource(file, pacing);
		Result result = new Result(source, decode);
		source.setAudioDataListener(result);
		try {
			source.prepare();
			source.start();
			assertTrue("Source must terminate", result.done.await(10, TimeUnit.SECONDS));
			assertNull(result.error);
			return result;
		} finally {
			source.release();
		}
	}

	@Test public void pcm16StereoPreservesScaleAndFinalShortBlock() throws Exception {
		ByteBuffer pcm = ByteBuffer.allocate(161 * 4).order(ByteOrder.LITTLE_ENDIAN);
		for (int i = 0; i < 161; ++i) pcm.putShort((short) -32768).putShort((short) 16384);
		Result result = read(wave(8000, 2, 1, 16, pcm.array()), AudioSource.DecodePacing.TURBO, false);
		assertEquals(Arrays.asList(160, 1), result.blocks);
		assertEquals(322, result.pcm.size());
		for (int i = 0; i < result.pcm.size(); i += 2) {
			assertEquals(-1f, result.pcm.get(i), 0);
			assertEquals(0.5f, result.pcm.get(i + 1), 0);
		}
		assertEquals(AudioSource.AudioSourceState.RELEASED, result.states.get(result.states.size() - 1));
	}

	@Test public void pcm8And24And32HandleSignedness() throws Exception {
		Result eight = read(wave(8000, 1, 1, 8, new byte[] { 0, (byte)128, (byte)255 }),
				AudioSource.DecodePacing.TURBO, false);
		assertEquals(Arrays.asList(-1f, 0f, 127f / 128), eight.pcm);
		Result twentyFour = read(wave(8000, 1, 1, 24, new byte[] {0,0,(byte)128, 0,0,64}),
				AudioSource.DecodePacing.TURBO, false);
		assertEquals(Arrays.asList(-1f, 0.5f), twentyFour.pcm);
		byte[] integers = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
				.putInt(Integer.MIN_VALUE).putInt(1073741824).array();
		assertEquals(Arrays.asList(-1f, 0.5f), read(wave(8000,1,1,32,integers),
				AudioSource.DecodePacing.TURBO, false).pcm);
	}

	@Test public void floatWavRemovesNonFiniteSamples() throws Exception {
		byte[] pcm = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
				.putFloat(-0.25f).putFloat(0.5f).putFloat(Float.NaN).putFloat(Float.POSITIVE_INFINITY).array();
		Result result = read(wave(48000, 1, 3, 32, pcm), AudioSource.DecodePacing.TURBO, false);
		assertEquals(Arrays.asList(-0.25f, 0.5f, 0f, 0f), result.pcm);
	}

	@Test public void truncatedWavReportsErrorInsteadOfCompleting() throws Exception {
		File file = wave(8000, 1, 1, 16, new byte[640]);
		try (java.io.RandomAccessFile output = new java.io.RandomAccessFile(file, "rw")) {
			output.setLength(file.length() - 3);
		}
		FileAudioSource source = new FileAudioSource(file, AudioSource.DecodePacing.TURBO);
		Result result = new Result(source, false);
		source.setAudioDataListener(result);
		try {
			source.prepare();
			source.start();
			assertTrue(result.done.await(3, TimeUnit.SECONDS));
			assertNotNull(result.error);
			assertFalse(result.states.contains(AudioSource.AudioSourceState.COMPLETED));
		} finally { source.release(); }
	}

	@Test public void malformedFrameAlignmentFailsPreparation() throws Exception {
		FileAudioSource source = new FileAudioSource(wave(8000, 2, 1, 16, new byte[3]), AudioSource.DecodePacing.TURBO);
		Result result = new Result(source, false);
		source.setAudioDataListener(result);
		try {
			source.prepare();
			fail("An incomplete stereo frame must fail");
		} catch (IOException expected) {
			assertTrue(result.done.await(3, TimeUnit.SECONDS));
			assertNotNull(source.getFailure());
			assertFalse(result.states.contains(AudioSource.AudioSourceState.READY));
		} finally { source.release(); }
	}

	@Test public void turboAndRealtimeProduceIdenticalPcmAndDecodedLines() throws Exception {
		int rate = 48000, channels = 2;
		ByteArrayOutputStream samples = new ByteArrayOutputStream();
		double phase = 0;
		// Twelve Robot36 scan lines, with changing luminance and alternating chroma.
		for (int line = 0; line < 12; ++line) {
			double[] duration = {0.009, 0.003, 0.088, 0.0045, 0.0015, 0.044};
			double[] frequency = {1200,1500,1500 + line * 60, (line & 1) == 0 ? 1500 : 2300,1900,1900};
			for (int segment = 0; segment < duration.length; ++segment) {
				for (int i = 0, n = (int)Math.round(duration[segment] * rate); i < n; ++i) {
					phase += 2 * Math.PI * frequency[segment] / rate;
					short value = (short)Math.round(16000 * Math.sin(phase));
					for (int ch = 0; ch < channels; ++ch) {
						samples.write(value & 255);
						samples.write((value >>> 8) & 255);
					}
				}
			}
		}
		File file = wave(rate, channels, 1, 16, samples.toByteArray());
		Result normal = read(file, AudioSource.DecodePacing.REALTIME, true);
		Result turbo = read(file, AudioSource.DecodePacing.TURBO, true);
		assertEquals(normal.pcm, turbo.pcm);
		assertEquals(normal.blocks, turbo.blocks);
		assertArrayEquals(normal.scope.pixels, turbo.scope.pixels);
		assertTrue("Fixture must actually decode lines", turbo.scope.line != 0);
	}

	@Test public void releaseWaitsForInFlightCallback() throws Exception {
		CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1), released = new CountDownLatch(1);
		boolean[] closed = {false};
		WorkerAudioSource source = new WorkerAudioSource() {
			@Override public AudioSourceType getType() { return AudioSourceType.FILE; }
			@Override protected PcmFormat openInput() { return new PcmFormat(8000, 1); }
			@Override protected void readInput() { emit(new float[] {0.25f}, 1); }
			@Override protected void closeInput() { closed[0] = true; }
		};
		source.setAudioDataListener(new AudioSource.AudioDataListener() {
			@Override public void onAudioDataAvailable(float[] pcm, int frames, int rate, int channels) {
				entered.countDown();
				boolean waiting = true;
				while (waiting) {
					try { resume.await(); waiting = false; } catch (InterruptedException ignored) { }
				}
				assertFalse("Cannot close while callback owns the buffer", closed[0]);
			}
			@Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
				if (state == AudioSource.AudioSourceState.RELEASED) released.countDown();
			}
		});
		source.prepare();
		source.start();
		assertTrue(entered.await(3, TimeUnit.SECONDS));
		source.release();
		assertFalse(released.await(30, TimeUnit.MILLISECONDS));
		resume.countDown();
		assertTrue(released.await(3, TimeUnit.SECONDS));
		assertTrue(closed[0]);
	}

	@Test public void cancelledPreparationNeverPublishesReady() throws Exception {
		CountDownLatch opening = new CountDownLatch(1), resume = new CountDownLatch(1), released = new CountDownLatch(1);
		List<AudioSource.AudioSourceState> states = new ArrayList<>();
		WorkerAudioSource source = new WorkerAudioSource() {
			@Override public AudioSourceType getType() { return AudioSourceType.FILE; }
			@Override protected PcmFormat openInput() {
				opening.countDown();
				boolean waiting = true;
				while (waiting) {
					try { resume.await(); waiting = false; } catch (InterruptedException ignored) { }
				}
				return new PcmFormat(8000, 1);
			}
			@Override protected void readInput() { fail("Cancelled source must never read"); }
			@Override protected void closeInput() { }
		};
		source.setAudioDataListener(new AudioSource.AudioDataListener() {
			@Override public void onAudioDataAvailable(float[] pcm, int frames, int rate, int channels) { fail(); }
			@Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
				states.add(state);
				if (state == AudioSource.AudioSourceState.RELEASED) released.countDown();
			}
		});
		Thread preparing = new Thread(() -> {
			try { source.prepare(); fail("Expected cancellation"); } catch (IOException expected) { }
		});
		preparing.start();
		assertTrue(opening.await(3, TimeUnit.SECONDS));
		source.release();
		resume.countDown();
		preparing.join(3000);
		assertFalse(preparing.isAlive());
		assertTrue(released.await(3, TimeUnit.SECONDS));
		assertFalse(states.contains(AudioSource.AudioSourceState.READY));
		assertFalse(states.contains(AudioSource.AudioSourceState.ERROR));
		assertEquals(AudioSource.AudioSourceState.RELEASED, states.get(states.size() - 1));
	}
}
