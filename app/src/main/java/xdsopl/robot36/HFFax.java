package xdsopl.robot36;

import android.graphics.Color;

/**
 * HF Fax, IOC 576, 120 lines per minute
 */
public class HFFax extends BaseMode {
    private final ExponentialMovingAverage lowPassFilter;
    private final String name;
    private final int sampleRate;
    private final float[] cumulated;
    private int horizontalShift = 0;

    HFFax(String name, int sampleRate) {
        this.name = name;
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
    public int getCode() {
        return -1;
    }

    @Override
    public int getWidth() {
        return 1808;
    }

    @Override
    public int getHeight() {
        return 1200;
    }

    @Override
    public int getBegin() {
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
    public int getEstimatedHorizontalShift() {
        return horizontalShift;
    }

    @Override
    public void reset() {
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

            cumulated[i] *= 0.99f; //decay old data
            cumulated[i] += Color.luminance(color);
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
