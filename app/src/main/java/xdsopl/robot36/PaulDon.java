/*
PD modes

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class PaulDon extends BaseMode {
	private final ExponentialMovingAverage lowPassFilter;
	private final int horizontalPixels;
	private final int verticalPixels;
	private final int scanLineSamples;
	private final int channelSamples;
	private final int beginSamples;
	private final int yEvenBeginSamples;
	private final int vAvgBeginSamples;
	private final int uAvgBeginSamples;
	private final int yOddBeginSamples;
	private final int endSamples;
	private final String name;
	private final int code;

	@SuppressWarnings("UnnecessaryLocalVariable")
	PaulDon(String name, int code, int horizontalPixels, int verticalPixels, double channelSeconds, int sampleRate) {
		this.name = "PD " + name;
		this.code = code;
		this.horizontalPixels = horizontalPixels;
		this.verticalPixels = verticalPixels;
		double syncPulseSeconds = 0.02;
		double syncPorchSeconds = 0.00208;
		double scanLineSeconds = syncPulseSeconds + syncPorchSeconds + 4 * (channelSeconds);
		scanLineSamples = (int) Math.round(scanLineSeconds * sampleRate);
		channelSamples = (int) Math.round(channelSeconds * sampleRate);
		double yEvenBeginSeconds = syncPorchSeconds;
		yEvenBeginSamples = (int) Math.round(yEvenBeginSeconds * sampleRate);
		beginSamples = yEvenBeginSamples;
		double vAvgBeginSeconds = yEvenBeginSeconds + channelSeconds;
		vAvgBeginSamples = (int) Math.round(vAvgBeginSeconds * sampleRate);
		double uAvgBeginSeconds = vAvgBeginSeconds + channelSeconds;
		uAvgBeginSamples = (int) Math.round(uAvgBeginSeconds * sampleRate);
		double yOddBeginSeconds = uAvgBeginSeconds + channelSeconds;
		yOddBeginSamples = (int) Math.round(yOddBeginSeconds * sampleRate);
		double yOddEndSeconds = yOddBeginSeconds + channelSeconds;
		endSamples = (int) Math.round(yOddEndSeconds * sampleRate);
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
		lowPassFilter.cutoff(horizontalPixels, 2 * channelSamples, 2);
		lowPassFilter.reset();
		for (int i = beginSamples; i < endSamples; ++i)
			scratchBuffer[i] = lowPassFilter.avg(scanLineBuffer[syncPulseIndex + i]);
		lowPassFilter.reset();
		for (int i = endSamples - 1; i >= beginSamples; --i)
			scratchBuffer[i] = freqToLevel(lowPassFilter.avg(scratchBuffer[i]), frequencyOffset);
		for (int i = 0; i < horizontalPixels; ++i) {
			int position = (i * channelSamples) / horizontalPixels;
			int yEvenPos = position + yEvenBeginSamples;
			int vAvgPos = position + vAvgBeginSamples;
			int uAvgPos = position + uAvgBeginSamples;
			int yOddPos = position + yOddBeginSamples;
			pixelBuffer.pixels[i] =
				ColorConverter.YUV2RGB(scratchBuffer[yEvenPos], scratchBuffer[uAvgPos], scratchBuffer[vAvgPos]);
			pixelBuffer.pixels[i + horizontalPixels] =
				ColorConverter.YUV2RGB(scratchBuffer[yOddPos], scratchBuffer[uAvgPos], scratchBuffer[vAvgPos]);
		}
		pixelBuffer.width = horizontalPixels;
		pixelBuffer.height = 2;
		return true;
	}
}
