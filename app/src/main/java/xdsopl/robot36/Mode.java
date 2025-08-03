/*
Mode interface

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public interface Mode {
	/**
	 * @return mode name
	 */
	String getName();

	/**
	 * @return VIS code
	 */
	int getCode();

	/**
	 * @return image width
	 */
	int getWidth();

	/**
	 * @return image height
	 */
	int getHeight();

	/**
	 * @return number of samples from sync pulse to start of image data
	 */
	int getBegin();

	/**
	 * @return number of samples from start of scanline to sync pulse??? nonzero for Scottie only?
	 */
	int getFirstSyncPulseIndex();

	/**
	 * @return number of samples in a scanline
	 */
	int getScanLineSamples();

	/**
	 * Reset internal state.
	 */
	void reset();

	/**
	 * @param pixelBuffer buffer to store decoded pixels
	 * @param scratchBuffer buffer for temporary data
	 * @param scanLineBuffer raw samples to be decoded, can contain more than one scanline
	 * @param scopeBufferWidth ??? used in RawDecoder, initializes width?
	 * @param syncPulseIndex number of samples from array start to sync pulse
	 * @param scanLineSamples number of samples per scanline
	 * @param frequencyOffset correction of frequency of expected vs actual sync pulse (normalized to range (-1, 1))
	 * @return true if scanline was decoded
	 */
	boolean decodeScanLine(PixelBuffer pixelBuffer, float[] scratchBuffer, float[] scanLineBuffer, int scopeBufferWidth, int syncPulseIndex, int scanLineSamples, float frequencyOffset);
}
