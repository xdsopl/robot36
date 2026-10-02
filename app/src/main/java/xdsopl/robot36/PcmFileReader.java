package xdsopl.robot36;

import java.io.Closeable;
import java.io.IOException;

/** File decoding boundary: native file bytes in, interleaved float PCM frames out. */
public interface PcmFileReader extends Closeable {
	/** Actual output format, established when opened and fixed for this reader. */
	AudioSource.PcmFormat getFormat();
	/**
	 * Fills complete frames with finite float PCM using the AudioSource sample scale.
	 * Only the returned frame count times the channel count is valid; never retains the array.
	 * Returns zero only after all decoded PCM has been delivered, never while waiting for output.
	 * Reads must respond to interruption so stopping a source can release its resources.
	 */
	int read(float[] pcm) throws IOException;
}
