/*
HF Fax mode

Copyright 2025 Marek Ossowski <marek0ossowski@gmail.com>
*/

package xdsopl.robot36;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;

/**
 * HF Fax, IOC 576, 120 lines per minute
 */
public class HFFax extends BaseMode {
	private final ExponentialMovingAverage lowPassFilter;
	private final String name;
	private final int sampleRate;
	private final float[] cumulated;
	private int horizontalShift = 0;

	HFFax(int sampleRate) {
		this.name = "HF Fax";
		lowPassFilter = new ExponentialMovingAverage();
		this.sampleRate = sampleRate;
		cumulated = new float[getWidth()];
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
		return -1;
	}

	@Override
	public int getWidth() {
		return 640;
	}

	@Override
	public int getHeight() {
		return 1200;
	}

	@Override
	public int getFirstPixelSampleIndex() {
		return 0;
	}

	@Override
	public int getFirstSyncPulseIndex() {
		return -1;
	}

	@Override
	public int getScanLineSamples() {
		return sampleRate / 2;
	}

	@Override
	public void resetState() {
	}

	@Override
	public Bitmap postProcessScopeImage(Bitmap bmp) {
		int realWidth = 1808;
		int realHorizontalShift = horizontalShift * realWidth / getWidth();
		Bitmap bmpMutable = Bitmap.createBitmap(realWidth, bmp.getHeight(), Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bmpMutable);
		if (horizontalShift > 0) {
			canvas.drawBitmap(
					bmp,
					new Rect(0, 0, horizontalShift, bmp.getHeight()),
					new Rect(realWidth - realHorizontalShift, 0, realWidth, bmp.getHeight()),
					null);
		}
		canvas.drawBitmap(
				bmp,
				new Rect(horizontalShift, 0, getWidth(), bmp.getHeight()),
				new Rect(0, 1, realWidth - realHorizontalShift, bmp.getHeight() + 1),
				null);

		return bmpMutable;
	}

	@Override
	public boolean decodeScanLine(PixelBuffer pixelBuffer, float[] scratchBuffer, float[] scanLineBuffer, int scopeBufferWidth, int syncPulseIndex, int scanLineSamples, float frequencyOffset) {
		if (syncPulseIndex < 0 || syncPulseIndex + scanLineSamples > scanLineBuffer.length)
			return false;
		int horizontalPixels = getWidth();
		lowPassFilter.cutoff(horizontalPixels, 2 * scanLineSamples, 2);
		lowPassFilter.reset();
		for (int i = 0; i < scanLineSamples; ++i)
			scratchBuffer[i] = lowPassFilter.avg(scanLineBuffer[i]);
		lowPassFilter.reset();
		for (int i = scanLineSamples - 1; i >= 0; --i)
			scratchBuffer[i] = freqToLevel(lowPassFilter.avg(scratchBuffer[i]), frequencyOffset);
		for (int i = 0; i < horizontalPixels; ++i) {
			int position = (i * scanLineSamples) / horizontalPixels;
			int color = ColorConverter.GRAY(scratchBuffer[position]);
			pixelBuffer.pixels[i] = color;

			//accumulate recent values, forget old
			float decay = 0.99f;
			cumulated[i] = cumulated[i] * decay + Color.luminance(color) * (1 - decay);
		}

		//try to detect "sync": thick white margin
		int bestIndex = 0;
		float bestValue = 0;
		for (int x = 0; x < getWidth(); ++x)
		{
			float val = cumulated[x];
			if (val > bestValue)
			{
				bestIndex = x;
				bestValue = val;
			}
		}

		horizontalShift = bestIndex;

		pixelBuffer.width = horizontalPixels;
		pixelBuffer.height = 1;
		return true;
	}
}
