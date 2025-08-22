/*
Decoder for RGB modes

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class RGBDecoder extends BaseMode {
	private final ExponentialMovingAverage lowPassFilter;
	private final int horizontalPixels;
	private final int verticalPixels;
	private final int firstSyncPulseIndex;
	private final int scanLineSamples;
	private final int beginSamples;
	private final int redBeginSamples;
	private final int redSamples;
	private final int greenBeginSamples;
	private final int greenSamples;
	private final int blueBeginSamples;
	private final int blueSamples;
	private final int endSamples;
	private final String name;
	private final int code;

	RGBDecoder(String name, int code, int horizontalPixels, int verticalPixels, double firstSyncPulseSeconds, double scanLineSeconds, double beginSeconds, double redBeginSeconds, double redEndSeconds, double greenBeginSeconds, double greenEndSeconds, double blueBeginSeconds, double blueEndSeconds, double endSeconds, int sampleRate) {
		this.name = name;
		this.code = code;
		this.horizontalPixels = horizontalPixels;
		this.verticalPixels = verticalPixels;
		firstSyncPulseIndex = (int) Math.round(firstSyncPulseSeconds * sampleRate);
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		beginSamples = (int) Math.round(beginSeconds * sampleRate);
		redBeginSamples = (int) Math.round(redBeginSeconds * sampleRate) - beginSamples;
		redSamples = (int) Math.round((redEndSeconds - redBeginSeconds) * sampleRate);
		greenBeginSamples = (int) Math.round(greenBeginSeconds * sampleRate) - beginSamples;
		greenSamples = (int) Math.round((greenEndSeconds - greenBeginSeconds) * sampleRate);
		blueBeginSamples = (int) Math.round(blueBeginSeconds * sampleRate) - beginSamples;
		blueSamples = (int) Math.round((blueEndSeconds - blueBeginSeconds) * sampleRate);
		endSamples = (int) Math.round(endSeconds * sampleRate);
		lowPassFilter = new ExponentialMovingAverage();
	}

	private float freqToLevel(float frequency, float offset) {
		return 0.5f * (frequency - offset + 1.f);
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public int getVISCode() {
		return code;
	}

	@Override
	public int getWidth() {
		return horizontalPixels;
	}

	@Override
	public int getHeight() {
		return verticalPixels;
	}

	@Override
	public int getFirstPixelSampleIndex() {
		return beginSamples;
	}

	@Override
	public int getFirstSyncPulseIndex() {
		return firstSyncPulseIndex;
	}

	@Override
	public int getScanLineSamples() {
		return scanLineSamples;
	}

	@Override
	public void resetState() {
	}

	@Override
	public boolean decodeScanLine(PixelBuffer pixelBuffer, float[] scratchBuffer, float[] scanLineBuffer, int scopeBufferWidth, int syncPulseIndex, int scanLineSamples, float frequencyOffset) {
		if (syncPulseIndex + beginSamples < 0 || syncPulseIndex + endSamples > scanLineBuffer.length)
			return false;
		lowPassFilter.cutoff(horizontalPixels, 2 * greenSamples, 2);
		lowPassFilter.reset();
		for (int i = 0; i < endSamples - beginSamples; ++i)
			scratchBuffer[i] = lowPassFilter.avg(scanLineBuffer[syncPulseIndex + beginSamples + i]);
		lowPassFilter.reset();
		for (int i = endSamples - beginSamples - 1; i >= 0; --i)
			scratchBuffer[i] = freqToLevel(lowPassFilter.avg(scratchBuffer[i]), frequencyOffset);
		for (int i = 0; i < horizontalPixels; ++i) {
			int redPos = redBeginSamples + (i * redSamples) / horizontalPixels;
			int greenPos = greenBeginSamples + (i * greenSamples) / horizontalPixels;
			int bluePos = blueBeginSamples + (i * blueSamples) / horizontalPixels;
			pixelBuffer.pixels[i] = ColorConverter.RGB(scratchBuffer[redPos], scratchBuffer[greenPos], scratchBuffer[bluePos]);
		}
		pixelBuffer.width = horizontalPixels;
		pixelBuffer.height = 1;
		return true;
	}
}
