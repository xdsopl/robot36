/*
Robot 72 Color

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class Robot_72_Color extends BaseMode {
	private final ExponentialMovingAverage lowPassFilter;
	private final int horizontalPixels;
	private final int verticalPixels;
	private final int scanLineSamples;
	private final int luminanceSamples;
	private final int chrominanceSamples;
	private final int beginSamples;
	private final int yBeginSamples;
	private final int vBeginSamples;
	private final int uBeginSamples;
	private final int endSamples;

	@SuppressWarnings("UnnecessaryLocalVariable")
	Robot_72_Color(int sampleRate) {
		horizontalPixels = 320;
		verticalPixels = 240;
		double syncPulseSeconds = 0.009;
		double syncPorchSeconds = 0.003;
		double luminanceSeconds = 0.138;
		double separatorSeconds = 0.0045;
		double porchSeconds = 0.0015;
		double chrominanceSeconds = 0.069;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + luminanceSeconds + 2 * (separatorSeconds + porchSeconds + chrominanceSeconds);
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		luminanceSamples = (int) Math.round(luminanceSeconds * sampleRate);
		chrominanceSamples = (int) Math.round(chrominanceSeconds * sampleRate);
		double yBeginSeconds = syncPorchSeconds;
		yBeginSamples = (int) Math.round(yBeginSeconds * sampleRate);
		beginSamples = yBeginSamples;
		double yEndSeconds = yBeginSeconds + luminanceSeconds;
		double vBeginSeconds = yEndSeconds + separatorSeconds + porchSeconds;
		vBeginSamples = (int) Math.round(vBeginSeconds * sampleRate);
		double vEndSeconds = vBeginSeconds + chrominanceSeconds;
		double uBeginSeconds = vEndSeconds + separatorSeconds + porchSeconds;
		uBeginSamples = (int) Math.round(uBeginSeconds * sampleRate);
		double uEndSeconds = uBeginSeconds + chrominanceSeconds;
		endSamples = (int) Math.round(uEndSeconds * sampleRate);
		lowPassFilter = new ExponentialMovingAverage();
	}

	private float freqToLevel(float frequency, float offset) {
		return 0.5f * (frequency - offset + 1.f);
	}

	@Override
	public String getName() {
		return "Robot 72 Color";
	}

	@Override
	public int getVISCode() {
		return 12;
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
	}

	@Override
	public boolean decodeScanLine(PixelBuffer pixelBuffer, float[] scratchBuffer, float[] scanLineBuffer, int scopeBufferWidth, int syncPulseIndex, int scanLineSamples, float frequencyOffset) {
		if (syncPulseIndex + beginSamples < 0 || syncPulseIndex + endSamples > scanLineBuffer.length)
			return false;
		lowPassFilter.cutoff(horizontalPixels, 2 * luminanceSamples, 2);
		lowPassFilter.reset();
		for (int i = beginSamples; i < endSamples; ++i)
			scratchBuffer[i] = lowPassFilter.avg(scanLineBuffer[syncPulseIndex + i]);
		lowPassFilter.reset();
		for (int i = endSamples - 1; i >= beginSamples; --i)
			scratchBuffer[i] = freqToLevel(lowPassFilter.avg(scratchBuffer[i]), frequencyOffset);
		for (int i = 0; i < horizontalPixels; ++i) {
			int yPos = yBeginSamples + (i * luminanceSamples) / horizontalPixels;
			int uPos = uBeginSamples + (i * chrominanceSamples) / horizontalPixels;
			int vPos = vBeginSamples + (i * chrominanceSamples) / horizontalPixels;
			pixelBuffer.pixels[i] = ColorConverter.YUV2RGB(scratchBuffer[yPos], scratchBuffer[uPos], scratchBuffer[vPos]);
		}
		pixelBuffer.width = horizontalPixels;
		pixelBuffer.height = 1;
		return true;
	}
}
