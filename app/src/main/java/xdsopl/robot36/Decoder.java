/*
SSTV Decoder

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

import java.util.ArrayList;
import java.util.Arrays;

public class Decoder {

	private final SimpleMovingAverage pulseFilter;
	private final Demodulator demodulator;
	private final PixelBuffer pixelBuffer;
	private final PixelBuffer scopeBuffer;
	private final PixelBuffer imageBuffer;
	private final float[] scanLineBuffer;
	private final float[] scratchBuffer;
	private final int[] last5msSyncPulses;
	private final int[] last9msSyncPulses;
	private final int[] last20msSyncPulses;
	private final int[] last5msScanLines;
	private final int[] last9msScanLines;
	private final int[] last20msScanLines;
	private final float[] last5msFrequencyOffsets;
	private final float[] last9msFrequencyOffsets;
	private final float[] last20msFrequencyOffsets;
	private final float[] visCodeBitFrequencies;
	private final int pulseFilterDelay;
	private final int scanLineMinSamples;
	private final int syncPulseToleranceSamples;
	private final int scanLineToleranceSamples;
	private final int leaderToneSamples;
	private final int leaderToneToleranceSamples;
	private final int transitionSamples;
	private final int visCodeBitSamples;
	private final int visCodeSamples;
	private final Mode rawMode;
	private final Mode hfFaxMode;
	private final ArrayList<Mode> syncPulse5msModes;
	private final ArrayList<Mode> syncPulse9msModes;
	private final ArrayList<Mode> syncPulse20msModes;

	protected Mode currentMode;
	private boolean lockMode;
	private int currentSample;
	private int leaderBreakIndex;
	private int lastSyncPulseIndex;
	private int currentScanLineSamples;
	private float lastFrequencyOffset;

	Decoder(PixelBuffer scopeBuffer, PixelBuffer imageBuffer, String rawName, int sampleRate) {
		this.scopeBuffer = scopeBuffer;
		this.imageBuffer = imageBuffer;
		imageBuffer.line = -1;
		pixelBuffer = new PixelBuffer(800, 2);
		demodulator = new Demodulator(sampleRate);
		double pulseFilterSeconds = 0.0025;
		int pulseFilterSamples = (int) Math.round(pulseFilterSeconds * sampleRate) | 1;
		pulseFilterDelay = (pulseFilterSamples - 1) / 2;
		pulseFilter = new SimpleMovingAverage(pulseFilterSamples);
		double scanLineMaxSeconds = 7;
		int scanLineMaxSamples = (int) Math.round(scanLineMaxSeconds * sampleRate);
		scanLineBuffer = new float[scanLineMaxSamples];
		double scratchBufferSeconds = 1.1;
		int scratchBufferSamples = (int) Math.round(scratchBufferSeconds * sampleRate);
		scratchBuffer = new float[scratchBufferSamples];
		double leaderToneSeconds = 0.3;
		leaderToneSamples = (int) Math.round(leaderToneSeconds * sampleRate);
		double leaderToneToleranceSeconds = leaderToneSeconds * 0.2;
		leaderToneToleranceSamples = (int) Math.round(leaderToneToleranceSeconds * sampleRate);
		double transitionSeconds = 0.0005;
		transitionSamples = (int) Math.round(transitionSeconds * sampleRate);
		double visCodeBitSeconds = 0.03;
		visCodeBitSamples = (int) Math.round(visCodeBitSeconds * sampleRate);
		double visCodeSeconds = 0.3;
		visCodeSamples = (int) Math.round(visCodeSeconds * sampleRate);
		visCodeBitFrequencies = new float[10];
		int scanLineCount = 4;
		last5msScanLines = new int[scanLineCount];
		last9msScanLines = new int[scanLineCount];
		last20msScanLines = new int[scanLineCount];
		int syncPulseCount = scanLineCount + 1;
		last5msSyncPulses = new int[syncPulseCount];
		last9msSyncPulses = new int[syncPulseCount];
		last20msSyncPulses = new int[syncPulseCount];
		last5msFrequencyOffsets = new float[syncPulseCount];
		last9msFrequencyOffsets = new float[syncPulseCount];
		last20msFrequencyOffsets = new float[syncPulseCount];
		double scanLineMinSeconds = 0.05;
		scanLineMinSamples = (int) Math.round(scanLineMinSeconds * sampleRate);
		double syncPulseToleranceSeconds = 0.03;
		syncPulseToleranceSamples = (int) Math.round(syncPulseToleranceSeconds * sampleRate);
		double scanLineToleranceSeconds = 0.001;
		scanLineToleranceSamples = (int) Math.round(scanLineToleranceSeconds * sampleRate);
		rawMode = new RawDecoder(rawName, sampleRate);
		hfFaxMode = new HFFax(sampleRate);
		Mode robot36 = new Robot_36_Color(sampleRate);
		currentMode = robot36;
		currentScanLineSamples = robot36.getScanLineSamples();
		syncPulse5msModes = new ArrayList<>();
		syncPulse5msModes.add(RGBModes.Wraase_SC2_180(sampleRate));
		syncPulse5msModes.add(RGBModes.Martin("1", 44, 0.146432, sampleRate));
		syncPulse5msModes.add(RGBModes.Martin("2", 40, 0.073216, sampleRate));
		syncPulse9msModes = new ArrayList<>();
		syncPulse9msModes.add(robot36);
		syncPulse9msModes.add(new Robot_72_Color(sampleRate));
		syncPulse9msModes.add(RGBModes.Scottie("1", 60, 0.138240, sampleRate));
		syncPulse9msModes.add(RGBModes.Scottie("2", 56, 0.088064, sampleRate));
		syncPulse9msModes.add(RGBModes.Scottie("DX", 76, 0.3456, sampleRate));
		syncPulse20msModes = new ArrayList<>();
		syncPulse20msModes.add(new PaulDon("50", 93, 320, 256, 0.09152, sampleRate));
		syncPulse20msModes.add(new PaulDon("90", 99, 320, 256, 0.17024, sampleRate));
		syncPulse20msModes.add(new PaulDon("120", 95, 640, 496, 0.1216, sampleRate));
		syncPulse20msModes.add(new PaulDon("160", 98, 512, 400, 0.195584, sampleRate));
		syncPulse20msModes.add(new PaulDon("180", 96, 640, 496, 0.18304, sampleRate));
		syncPulse20msModes.add(new PaulDon("240", 97, 640, 496, 0.24448, sampleRate));
		syncPulse20msModes.add(new PaulDon("290", 94, 800, 616, 0.2288, sampleRate));
	}

	private double scanLineMean(int[] lines) {
		double mean = 0;
		for (int diff : lines)
			mean += diff;
		mean /= lines.length;
		return mean;
	}

	private double scanLineStdDev(int[] lines, double mean) {
		double stdDev = 0;
		for (int diff : lines)
			stdDev += (diff - mean) * (diff - mean);
		stdDev = Math.sqrt(stdDev / lines.length);
		return stdDev;
	}

	private double frequencyOffsetMean(float[] offsets) {
		double mean = 0;
		for (float diff : offsets)
			mean += diff;
		mean /= offsets.length;
		return mean;
	}

	private Mode detectMode(ArrayList<Mode> modes, int line) {
		Mode bestMode = rawMode;
		int bestDist = Integer.MAX_VALUE;
		for (Mode mode : modes) {
			int dist = Math.abs(line - mode.getScanLineSamples());
			if (dist <= scanLineToleranceSamples && dist < bestDist) {
				bestDist = dist;
				bestMode = mode;
			}
		}
		return bestMode;
	}

	private Mode findMode(ArrayList<Mode> modes, int code) {
		for (Mode mode : modes)
			if (mode.getVISCode() == code)
				return mode;
		return null;
	}

	private Mode findMode(ArrayList<Mode> modes, String name) {
		for (Mode mode : modes)
			if (mode.getName().equals(name))
				return mode;
		return null;
	}

	private void copyUnscaled() {
		int width = Math.min(scopeBuffer.width, pixelBuffer.width);
		for (int row = 0; row < pixelBuffer.height; ++row) {
			int line = scopeBuffer.width * scopeBuffer.line;
			System.arraycopy(pixelBuffer.pixels, row * pixelBuffer.width, scopeBuffer.pixels, line, width);
			Arrays.fill(scopeBuffer.pixels, line + width, line + scopeBuffer.width, 0);
			System.arraycopy(scopeBuffer.pixels, line, scopeBuffer.pixels, scopeBuffer.width * (scopeBuffer.line + scopeBuffer.height / 2), scopeBuffer.width);
			scopeBuffer.line = (scopeBuffer.line + 1) % (scopeBuffer.height / 2);
		}
	}

	private void copyScaled(int scale) {
		for (int row = 0; row < pixelBuffer.height; ++row) {
			int line = scopeBuffer.width * scopeBuffer.line;
			for (int col = 0; col < pixelBuffer.width; ++col)
				for (int i = 0; i < scale; ++i)
					scopeBuffer.pixels[line + col * scale + i] = pixelBuffer.pixels[pixelBuffer.width * row + col];
			Arrays.fill(scopeBuffer.pixels, line + pixelBuffer.width * scale, line + scopeBuffer.width, 0);
			System.arraycopy(scopeBuffer.pixels, line, scopeBuffer.pixels, scopeBuffer.width * (scopeBuffer.line + scopeBuffer.height / 2), scopeBuffer.width);
			scopeBuffer.line = (scopeBuffer.line + 1) % (scopeBuffer.height / 2);
			for (int i = 1; i < scale; ++i) {
				System.arraycopy(scopeBuffer.pixels, line, scopeBuffer.pixels, scopeBuffer.width * scopeBuffer.line, scopeBuffer.width);
				System.arraycopy(scopeBuffer.pixels, line, scopeBuffer.pixels, scopeBuffer.width * (scopeBuffer.line + scopeBuffer.height / 2), scopeBuffer.width);
				scopeBuffer.line = (scopeBuffer.line + 1) % (scopeBuffer.height / 2);
			}
		}
	}

	private void copyLines(boolean okay) {
		if (!okay)
			return;
		boolean finish = false;
		if (imageBuffer.line >= 0 && imageBuffer.line < imageBuffer.height && imageBuffer.width == pixelBuffer.width) {
			int width = imageBuffer.width;
			for (int row = 0; row < pixelBuffer.height && imageBuffer.line < imageBuffer.height; ++row, ++imageBuffer.line)
				System.arraycopy(pixelBuffer.pixels, row * width, imageBuffer.pixels, imageBuffer.line * width, width);
			finish = imageBuffer.line == imageBuffer.height;
		}
		int scale = scopeBuffer.width / pixelBuffer.width;
		if (scale <= 1)
			copyUnscaled();
		else
			copyScaled(scale);
		if (finish)
			drawLines(0xff000000, 10);
	}

	private void drawLines(int color, int count) {
		for (int i = 0; i < count; ++i) {
			Arrays.fill(scopeBuffer.pixels, scopeBuffer.line * scopeBuffer.width, (scopeBuffer.line + 1) * scopeBuffer.width, color);
			Arrays.fill(scopeBuffer.pixels, (scopeBuffer.line + scopeBuffer.height / 2) * scopeBuffer.width, (scopeBuffer.line + 1 + scopeBuffer.height / 2) * scopeBuffer.width, color);
			scopeBuffer.line = (scopeBuffer.line + 1) % (scopeBuffer.height / 2);
		}
	}

	private void adjustSyncPulses(int[] pulses, int shift) {
		for (int i = 0; i < pulses.length; ++i)
			pulses[i] -= shift;
	}

	private void shiftSamples(int shift) {
		if (shift <= 0 || shift > currentSample)
			return;
		currentSample -= shift;
		leaderBreakIndex -= shift;
		lastSyncPulseIndex -= shift;
		adjustSyncPulses(last5msSyncPulses, shift);
		adjustSyncPulses(last9msSyncPulses, shift);
		adjustSyncPulses(last20msSyncPulses, shift);
		System.arraycopy(scanLineBuffer, shift, scanLineBuffer, 0, currentSample);
	}

	private boolean handleHeader() {
		if (leaderBreakIndex < visCodeBitSamples + leaderToneToleranceSamples || currentSample < leaderBreakIndex + leaderToneSamples + leaderToneToleranceSamples + visCodeSamples + visCodeBitSamples)
			return false;
		int breakPulseIndex = leaderBreakIndex;
		leaderBreakIndex = 0;
		float preBreakFreq = 0;
		for (int i = 0; i < leaderToneToleranceSamples; ++i)
			preBreakFreq += scanLineBuffer[breakPulseIndex - visCodeBitSamples - leaderToneToleranceSamples + i];
		float leaderToneFrequency = 1900;
		float centerFrequency = 1900;
		float toleranceFrequency = 50;
		float halfBandWidth = 400;
		preBreakFreq = preBreakFreq * halfBandWidth / leaderToneToleranceSamples + centerFrequency;
		if (Math.abs(preBreakFreq - leaderToneFrequency) > toleranceFrequency)
			return false;
		float leaderFreq = 0;
		for (int i = transitionSamples; i < leaderToneSamples - leaderToneToleranceSamples; ++i)
			leaderFreq += scanLineBuffer[breakPulseIndex + i];
		float leaderFreqOffset = leaderFreq / (leaderToneSamples - transitionSamples - leaderToneToleranceSamples);
		leaderFreq = leaderFreqOffset * halfBandWidth + centerFrequency;
		if (Math.abs(leaderFreq - leaderToneFrequency) > toleranceFrequency)
			return false;
		float stopBitFrequency = 1200;
		float pulseThresholdFrequency = (stopBitFrequency + leaderToneFrequency) / 2;
		float pulseThresholdValue = (pulseThresholdFrequency - centerFrequency) / halfBandWidth;
		int visBeginIndex = breakPulseIndex + leaderToneSamples - leaderToneToleranceSamples;
		int visEndIndex = breakPulseIndex + leaderToneSamples + leaderToneToleranceSamples + visCodeBitSamples;
		for (int i = 0; i < pulseFilter.length; ++i)
			pulseFilter.avg(scanLineBuffer[visBeginIndex++] - leaderFreqOffset);
		while (++visBeginIndex < visEndIndex)
			if (pulseFilter.avg(scanLineBuffer[visBeginIndex] - leaderFreqOffset) < pulseThresholdValue)
				break;
		if (visBeginIndex >= visEndIndex)
			return false;
		visBeginIndex -= pulseFilterDelay;
		visEndIndex = visBeginIndex + visCodeSamples;
		Arrays.fill(visCodeBitFrequencies, 0);
		for (int j = 0; j < 10; ++j)
			for (int i = transitionSamples; i < visCodeBitSamples - transitionSamples; ++i)
				visCodeBitFrequencies[j] += scanLineBuffer[visBeginIndex + visCodeBitSamples * j + i] - leaderFreqOffset;
		for (int i = 0; i < 10; ++i)
			visCodeBitFrequencies[i] = visCodeBitFrequencies[i] * halfBandWidth / (visCodeBitSamples - 2 * transitionSamples) + centerFrequency;
		if (Math.abs(visCodeBitFrequencies[0] - stopBitFrequency) > toleranceFrequency || Math.abs(visCodeBitFrequencies[9] - stopBitFrequency) > toleranceFrequency)
			return false;
		float oneBitFrequency = 1100;
		float zeroBitFrequency = 1300;
		for (int i = 1; i < 9; ++i)
			if (Math.abs(visCodeBitFrequencies[i] - oneBitFrequency) > toleranceFrequency && Math.abs(visCodeBitFrequencies[i] - zeroBitFrequency) > toleranceFrequency)
				return false;
		int visCode = 0;
		for (int i = 0; i < 8; ++i)
			visCode |= (visCodeBitFrequencies[i + 1] < stopBitFrequency ? 1 : 0) << i;
		boolean check = true;
		for (int i = 0; i < 8; ++i)
			check ^= (visCode & 1 << i) != 0;
		visCode &= 127;
		if (!check)
			return false;
		float syncPorchFrequency = 1500;
		float syncPulseFrequency = 1200;
		float syncThresholdFrequency = (syncPulseFrequency + syncPorchFrequency) / 2;
		float syncThresholdValue = (syncThresholdFrequency - centerFrequency) / halfBandWidth;
		int syncPulseIndex = visEndIndex - visCodeBitSamples;
		int syncPulseMaxIndex = visEndIndex + visCodeBitSamples;
		for (int i = 0; i < pulseFilter.length; ++i)
			pulseFilter.avg(scanLineBuffer[syncPulseIndex++] - leaderFreqOffset);
		while (++syncPulseIndex < syncPulseMaxIndex)
			if (pulseFilter.avg(scanLineBuffer[syncPulseIndex] - leaderFreqOffset) > syncThresholdValue)
				break;
		if (syncPulseIndex >= syncPulseMaxIndex)
			return false;
		syncPulseIndex -= pulseFilterDelay;
		Mode mode;
		int[] pulses;
		int[] lines;
		if ((mode = findMode(syncPulse5msModes, visCode)) != null) {
			pulses = last5msSyncPulses;
			lines = last5msScanLines;
		} else if ((mode = findMode(syncPulse9msModes, visCode)) != null) {
			pulses = last9msSyncPulses;
			lines = last9msScanLines;
		} else if ((mode = findMode(syncPulse20msModes, visCode)) != null) {
			pulses = last20msSyncPulses;
			lines = last20msScanLines;
		} else {
			if (!lockMode)
				drawLines(0xffff0000, 8);
			return false;
		}
		if (lockMode && mode != currentMode)
			return false;
		mode.resetState();
		imageBuffer.width = mode.getWidth();
		imageBuffer.height = mode.getHeight();
		imageBuffer.line = 0;
		currentMode = mode;
		lastSyncPulseIndex = syncPulseIndex + mode.getFirstSyncPulseIndex();
		currentScanLineSamples = mode.getScanLineSamples();
		lastFrequencyOffset = leaderFreqOffset;
		int oldestSyncPulseIndex = lastSyncPulseIndex - (pulses.length - 1) * currentScanLineSamples;
		if (mode.getFirstSyncPulseIndex() > 0)
			oldestSyncPulseIndex -= currentScanLineSamples;
		for (int i = 0; i < pulses.length; ++i)
			pulses[i] = oldestSyncPulseIndex + i * currentScanLineSamples;
		Arrays.fill(lines, currentScanLineSamples);
		shiftSamples(lastSyncPulseIndex + mode.getFirstPixelSampleIndex());
		drawLines(0xff00ff00, 8);
		drawLines(0xff000000, 10);
		return true;
	}

	private boolean processSyncPulse(ArrayList<Mode> modes, float[] freqOffs, int[] syncIndexes, int[] lineLengths, int latestSyncIndex) {
		for (int i = 1; i < syncIndexes.length; ++i)
			syncIndexes[i - 1] = syncIndexes[i];
		syncIndexes[syncIndexes.length - 1] = latestSyncIndex;
		for (int i = 1; i < lineLengths.length; ++i)
			lineLengths[i - 1] = lineLengths[i];
		lineLengths[lineLengths.length - 1] = syncIndexes[syncIndexes.length - 1] - syncIndexes[syncIndexes.length - 2];
		for (int i = 1; i < freqOffs.length; ++i)
			freqOffs[i - 1] = freqOffs[i];
		freqOffs[syncIndexes.length - 1] = demodulator.frequencyOffset;
		if (lineLengths[0] == 0)
			return false;
		double mean = scanLineMean(lineLengths);
		int scanLineSamples = (int) Math.round(mean);
		if (scanLineSamples < scanLineMinSamples || scanLineSamples > scratchBuffer.length)
			return false;
		if (scanLineStdDev(lineLengths, mean) > scanLineToleranceSamples)
			return false;
		boolean pictureChanged = false;
		if (lockMode || imageBuffer.line >= 0 && imageBuffer.line < imageBuffer.height) {
			if (currentMode != rawMode && Math.abs(scanLineSamples - currentMode.getScanLineSamples()) > scanLineToleranceSamples)
				return false;
		} else {
			Mode prevMode = currentMode;
			currentMode = detectMode(modes, scanLineSamples);
			pictureChanged = currentMode != prevMode
				|| Math.abs(currentScanLineSamples - scanLineSamples) > scanLineToleranceSamples
				|| Math.abs(lastSyncPulseIndex + scanLineSamples - syncIndexes[syncIndexes.length - 1]) > syncPulseToleranceSamples;
		}
		if (pictureChanged) {
			drawLines(0xff000000, 10);
			drawLines(0xff00ffff, 8);
			drawLines(0xff000000, 10);
		}
		float frequencyOffset = (float) frequencyOffsetMean(freqOffs);
		if (syncIndexes[0] >= scanLineSamples && pictureChanged) {
			int endPulse = syncIndexes[0];
			int extrapolate = endPulse / scanLineSamples;
			int firstPulse = endPulse - extrapolate * scanLineSamples;
			for (int pulseIndex = firstPulse; pulseIndex < endPulse; pulseIndex += scanLineSamples)
				copyLines(currentMode.decodeScanLine(pixelBuffer, scratchBuffer, scanLineBuffer, scopeBuffer.width, pulseIndex, scanLineSamples, frequencyOffset));
		}
		for (int i = pictureChanged ? 0 : lineLengths.length - 1; i < lineLengths.length; ++i)
			copyLines(currentMode.decodeScanLine(pixelBuffer, scratchBuffer, scanLineBuffer, scopeBuffer.width, syncIndexes[i], lineLengths[i], frequencyOffset));
		lastSyncPulseIndex = syncIndexes[syncIndexes.length - 1];
		currentScanLineSamples = scanLineSamples;
		lastFrequencyOffset = frequencyOffset;
		shiftSamples(lastSyncPulseIndex + currentMode.getFirstPixelSampleIndex());
		return true;
	}

	public boolean process(float[] recordBuffer, int channelSelect) {
		boolean newLinesPresent = false;
		boolean syncPulseDetected = demodulator.process(recordBuffer, channelSelect);
		int syncPulseIndex = currentSample + demodulator.syncPulseOffset;
		int channels = channelSelect > 0 ? 2 : 1;
		for (int j = 0; j < recordBuffer.length / channels; ++j) {
			scanLineBuffer[currentSample++] = recordBuffer[j];
			if (currentSample >= scanLineBuffer.length) {
				shiftSamples(currentScanLineSamples);
				syncPulseIndex -= currentScanLineSamples;
			}
		}
		if (syncPulseDetected) {
			switch (demodulator.syncPulseWidth) {
				case FiveMilliSeconds:
					newLinesPresent = processSyncPulse(syncPulse5msModes, last5msFrequencyOffsets, last5msSyncPulses, last5msScanLines, syncPulseIndex);
					break;
				case NineMilliSeconds:
					leaderBreakIndex = syncPulseIndex;
					newLinesPresent = processSyncPulse(syncPulse9msModes, last9msFrequencyOffsets, last9msSyncPulses, last9msScanLines, syncPulseIndex);
					break;
				case TwentyMilliSeconds:
					leaderBreakIndex = syncPulseIndex;
					newLinesPresent = processSyncPulse(syncPulse20msModes, last20msFrequencyOffsets, last20msSyncPulses, last20msScanLines, syncPulseIndex);
					break;
				default:
					break;
			}
		} else if (handleHeader()) {
			newLinesPresent = true;
		} else if (currentSample > lastSyncPulseIndex + (currentScanLineSamples * 5) / 4) {
			copyLines(currentMode.decodeScanLine(pixelBuffer, scratchBuffer, scanLineBuffer, scopeBuffer.width, lastSyncPulseIndex, currentScanLineSamples, lastFrequencyOffset));
			lastSyncPulseIndex += currentScanLineSamples;
			newLinesPresent = true;
		}

		return newLinesPresent;
	}

	public void setMode(String name) {
		if (rawMode.getName().equals(name)) {
			lockMode = true;
			imageBuffer.line = -1;
			currentMode = rawMode;
			return;
		}
		Mode mode = findMode(syncPulse5msModes, name);
		if (mode == null)
			mode = findMode(syncPulse9msModes, name);
		if (mode == null)
			mode = findMode(syncPulse20msModes, name);
		if (mode == null && hfFaxMode.getName().equals(name))
			mode = hfFaxMode;
		if (mode == currentMode) {
			lockMode = true;
			return;
		}
		if (mode != null) {
			lockMode = true;
			imageBuffer.line = -1;
			currentMode = mode;
			currentScanLineSamples = mode.getScanLineSamples();
			return;
		}
		lockMode = false;
	}
}
