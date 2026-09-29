package xdsopl.robot36;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/** File decoding boundary: native file bytes in, interleaved float PCM frames out. */
interface PcmFileReader extends Closeable {
	AudioSource.PcmFormat getFormat();
	/** Fills complete frames only; returns zero at EOF and never retains the array. */
	int read(float[] pcm) throws IOException;

	/** Owns the stream, including on failure. Add future format readers here. */
	static PcmFileReader open(InputStream input) throws IOException {
		if (input == null) throw new IOException("Cannot open audio file");
		BufferedInputStream stream = new BufferedInputStream(input);
		try {
			stream.mark(12);
			byte[] signature = new byte[12];
			int count = 0, value;
			while (count < signature.length && (value = stream.read()) >= 0)
				signature[count++] = (byte) value;
			stream.reset();
			if (count == 12 && signature[0] == 'R' && signature[1] == 'I'
					&& signature[2] == 'F' && signature[3] == 'F'
					&& signature[8] == 'W' && signature[9] == 'A'
					&& signature[10] == 'V' && signature[11] == 'E')
				return new WavFileReader(stream);
			throw new IOException("Unsupported audio file. Choose a PCM or IEEE-float WAV file.");
		} catch (IOException | RuntimeException e) {
			try { stream.close(); } catch (IOException closeError) { e.addSuppressed(closeError); }
			throw e;
		}
	}
}
