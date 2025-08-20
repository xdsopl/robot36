/*
Robot 36 Color

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Robot_36_Color extends BaseMode {
	private final ExponentialMovingAverage lowPassFilter;
	private final int horizontalPixels;
	private final int verticalPixels;
	private final int scanLineSamples;
	private final int luminanceSamples;
	private final int separatorSamples;
	private final int chrominanceSamples;
	private final int beginSamples;
	private final int luminanceBeginSamples;
	private final int separatorBeginSamples;
	private final int chrominanceBeginSamples;
	private final int endSamples;
	private boolean lastEven;

	@SuppressWarnings("UnnecessaryLocalVariable")
	Robot_36_Color(int sampleRate) {
		horizontalPixels = 320;
		verticalPixels = 240;
		double syncPulseSeconds = 0.009;
		double syncPorchSeconds = 0.003;
		double luminanceSeconds = 0.088;
		double separatorSeconds = 0.0045;
		double porchSeconds = 0.0015;
		double chrominanceSeconds = 0.044;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + luminanceSeconds + separatorSeconds + porchSeconds + chrominanceSeconds;
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		luminanceSamples = (int) Math.round(luminanceSeconds * sampleRate);
		separatorSamples = (int) Math.round(separatorSeconds * sampleRate);
		chrominanceSamples = (int) Math.round(chrominanceSeconds * sampleRate);
		double luminanceBeginSeconds = syncPorchSeconds;
		luminanceBeginSamples = (int) Math.round(luminanceBeginSeconds * sampleRate);
		beginSamples = luminanceBeginSamples;
		double separatorBeginSeconds = luminanceBeginSeconds + luminanceSeconds;
		separatorBeginSamples = (int) Math.round(separatorBeginSeconds * sampleRate);
		double separatorEndSeconds = separatorBeginSeconds + separatorSeconds;
		double chrominanceBeginSeconds = separatorEndSeconds + porchSeconds;
		chrominanceBeginSamples = (int) Math.round(chrominanceBeginSeconds * sampleRate);
		double chrominanceEndSeconds = chrominanceBeginSeconds + chrominanceSeconds;
		endSamples = (int) Math.round(chrominanceEndSeconds * sampleRate);
		lowPassFilter = new ExponentialMovingAverage();
	}

	private float freqToLevel(float frequency, float offset) {
		return 0.5f * (frequency - offset + 1.f);
	}

	@Override
	public String getName() {
		return "Robot 36 Color";
	}

	@Override
	public int getVISCode() {
		return 8;
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
		return 0;
	}

	@Override
	public int getScanLineSamples() {
		return scanLineSamples;
	}

	@Override
	public void resetState() {
		lastEven = false;
	}

	@Override
	public boolean decodeScanLine(PixelBuffer pixelBuffer, float[] scratchBuffer, float[] scanLineBuffer, int scopeBufferWidth, int syncPulseIndex, int scanLineSamples, float frequencyOffset) {
		if (syncPulseIndex + beginSamples < 0 || syncPulseIndex + endSamples > scanLineBuffer.length)
			return false;
		float separator = 0;
		for (int i = 0; i < separatorSamples; ++i)
			separator += scanLineBuffer[syncPulseIndex + separatorBeginSamples + i];
		separator /= separatorSamples;
		separator -= frequencyOffset;
		boolean even = separator < 0;
		if (separator < -1.1 || separator > -0.9 && separator < 0.9 || separator > 1.1)
			even = !lastEven;
		lastEven = even;
		lowPassFilter.cutoff(horizontalPixels, 2 * luminanceSamples, 2);
		lowPassFilter.reset();
		for (int i = beginSamples; i < endSamples; ++i)
			scratchBuffer[i] = lowPassFilter.avg(scanLineBuffer[syncPulseIndex + i]);
		lowPassFilter.reset();
		for (int i = endSamples - 1; i >= beginSamples; --i)
			scratchBuffer[i] = freqToLevel(lowPassFilter.avg(scratchBuffer[i]), frequencyOffset);
		for (int i = 0; i < horizontalPixels; ++i) {
			int luminancePos = luminanceBeginSamples + (i * luminanceSamples) / horizontalPixels;
			int chrominancePos = chrominanceBeginSamples + (i * chrominanceSamples) / horizontalPixels;
			if (even) {
				pixelBuffer.pixels[i] = ColorConverter.RGB(scratchBuffer[luminancePos], 0, scratchBuffer[chrominancePos]);
			} else {
				int evenYUV = pixelBuffer.pixels[i];
				int oddYUV = ColorConverter.RGB(scratchBuffer[luminancePos], scratchBuffer[chrominancePos], 0);
				pixelBuffer.pixels[i] =
					ColorConverter.YUV2RGB((evenYUV & 0x00ff00ff) | (oddYUV & 0x0000ff00));
				pixelBuffer.pixels[i + horizontalPixels] =
					ColorConverter.YUV2RGB((oddYUV & 0x00ffff00) | (evenYUV & 0x000000ff));
			}
		}
		pixelBuffer.width = horizontalPixels;
		pixelBuffer.height = 2;
		return !even;
	}
}
