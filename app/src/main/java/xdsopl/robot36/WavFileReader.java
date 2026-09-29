package xdsopl.robot36;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

/** RIFF/WAVE PCM (8/16/24/32-bit) and IEEE float (32/64-bit), mono or stereo. */
final class WavFileReader implements PcmFileReader {
	private final InputStream input;
	private AudioSource.PcmFormat format;
	private int encoding, bits, blockAlign;
	private long remainingData;
	private byte[] bytes = new byte[0];

	WavFileReader(InputStream input) throws IOException {
		this.input = input;
		byte[] header = new byte[12];
		readFully(header, 12);
		if (!id(header, 0, "RIFF") || !id(header, 8, "WAVE"))
			throw new IOException("Expected RIFF/WAVE");
		long remaining = u32(header, 4) - 4;
		byte[] chunk = new byte[8];
		while (remaining >= 8) {
			readFully(chunk, 8);
			remaining -= 8;
			long size = u32(chunk, 4), padded = size + (size & 1);
			if (padded > remaining) throw new EOFException("WAV chunk exceeds RIFF size");
			if (id(chunk, 0, "fmt ")) {
				if (format != null || size < 16) throw new IOException("Invalid WAV format chunk");
				byte[] fmt = new byte[(int) Math.min(size, 40)];
				readFully(fmt, fmt.length);
				parseFormat(fmt, size);
				discard(padded - fmt.length);
			} else if (id(chunk, 0, "data")) {
				if (format == null) throw new IOException("WAV data precedes its format");
				if (size % blockAlign != 0) throw new IOException("WAV data ends inside a PCM frame");
				remainingData = size;
				return;
			} else discard(padded);
			remaining -= padded;
		}
		throw new IOException("Missing WAV data chunk");
	}

	private void parseFormat(byte[] fmt, long size) throws IOException {
		encoding = u16(fmt, 0);
		int channels = u16(fmt, 2);
		long rate = u32(fmt, 4);
		blockAlign = u16(fmt, 12);
		bits = u16(fmt, 14);
		if (encoding == 0xfffe) {
			if (size < 40 || u16(fmt, 16) < 22 || 18L + u16(fmt, 16) > size)
				throw new IOException("Invalid WAVE_FORMAT_EXTENSIBLE header");
			long subFormat = u32(fmt, 24);
			int[] guidTail = {0, 0, 0x10, 0, 0x80, 0, 0, 0xaa, 0, 0x38, 0x9b, 0x71};
			for (int i = 0; i < guidTail.length; ++i)
				if ((fmt[28 + i] & 255) != guidTail[i]) throw new IOException("Unsupported WAV subformat");
			if (subFormat != 1 && subFormat != 3) throw new IOException("Unsupported WAV subformat");
			encoding = (int) subFormat;
			int validBits = u16(fmt, 18);
			if (validBits < 1 || validBits > bits || (encoding == 3 && validBits != bits))
				throw new IOException("Invalid WAV valid-bit count");
			long mask = u32(fmt, 20);
			if (mask != 0 && ((channels == 1 && Long.bitCount(mask) != 1) || (channels == 2 && mask != 3)))
				throw new IOException("Unsupported WAV channel layout");
		}
		if (channels != 1 && channels != 2) throw new IOException("WAV must be mono or stereo");
		// Bound decoder allocations and exclude rates below the SSTV audio bandwidth.
		if (rate < 8000 || rate > 192000) throw new IOException("WAV sample rate must be 8-192 kHz");
		boolean integer = encoding == 1 && (bits == 8 || bits == 16 || bits == 24 || bits == 32);
		boolean floating = encoding == 3 && (bits == 32 || bits == 64);
		if (!integer && !floating) throw new IOException("Unsupported WAV encoding or bit depth");
		if (blockAlign != channels * (bits / 8) || u32(fmt, 8) != rate * blockAlign)
			throw new IOException("Inconsistent WAV frame size or byte rate");
		format = new AudioSource.PcmFormat((int) rate, channels);
	}

	@Override public AudioSource.PcmFormat getFormat() { return format; }

	@Override public int read(float[] pcm) throws IOException {
		int capacity = pcm.length / format.getChannels();
		if (capacity == 0) throw new IllegalArgumentException("PCM buffer holds no frames");
		int frames = (int) Math.min(capacity, remainingData / blockAlign);
		if (frames == 0) return 0;
		int count = frames * blockAlign;
		if (bytes.length < count) bytes = new byte[count];
		int received = readUpTo(bytes, count);
		if (received < count) {
			// Some SSTV encoders overstate data/RIFF lengths by one frame. Accept
			// only that small, frame-aligned discrepancy, without inventing samples.
			if (remainingData - received != blockAlign || received % blockAlign != 0)
				throw new EOFException("Truncated WAV file");
			remainingData = 0;
			frames = received / blockAlign;
		} else remainingData -= received;
		int width = bits / 8;
		for (int i = 0, p = 0; i < frames * format.getChannels(); ++i, p += width) {
			float sample;
			if (encoding == 3) {
				if (bits == 32) sample = Float.intBitsToFloat((int) u32(bytes, p));
				else sample = (float) Double.longBitsToDouble(u32(bytes, p) | (u32(bytes, p + 4) << 32));
				if (Float.isNaN(sample) || Float.isInfinite(sample)) throw new IOException("Non-finite WAV sample");
			} else if (bits == 8) sample = ((bytes[p] & 255) - 128) / 128.0f;
			else if (bits == 16) sample = (short) u16(bytes, p) / 32768.0f;
			else if (bits == 24) sample = ((bytes[p] & 255) | ((bytes[p + 1] & 255) << 8) | (bytes[p + 2] << 16)) / 8388608.0f;
			else sample = (float) ((int) u32(bytes, p) / 2147483648.0);
			pcm[i] = sample;
		}
		return frames;
	}

	private void discard(long count) throws IOException {
		byte[] scratch = new byte[4096];
		while (count > 0) {
			int n = (int) Math.min(count, scratch.length);
			readFully(scratch, n);
			count -= n;
		}
	}
	private void readFully(byte[] buffer, int count) throws IOException {
		if (readUpTo(buffer, count) != count) throw new EOFException("Truncated WAV file");
	}
	private int readUpTo(byte[] buffer, int count) throws IOException {
		int offset = 0;
		while (offset < count) {
			if (Thread.currentThread().isInterrupted()) throw new java.io.InterruptedIOException("WAV read cancelled");
			int n = input.read(buffer, offset, count - offset);
			if (n < 0) break;
			if (n == 0) {
				int value = input.read();
				if (value < 0) break;
				buffer[offset++] = (byte) value;
			} else offset += n;
		}
		return offset;
	}
	private static boolean id(byte[] b, int p, String id) {
		for (int i = 0; i < 4; ++i) if (b[p + i] != id.charAt(i)) return false;
		return true;
	}
	private static int u16(byte[] b, int p) { return (b[p] & 255) | ((b[p + 1] & 255) << 8); }
	private static long u32(byte[] b, int p) { return u16(b, p) | ((long) u16(b, p + 2) << 16); }
	@Override public void close() throws IOException { input.close(); }
}
