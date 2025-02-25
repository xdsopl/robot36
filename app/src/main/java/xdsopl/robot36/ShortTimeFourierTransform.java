/*
Short Time Fourier Transform

Copyright 2025 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class ShortTimeFourierTransform {
	private final FastFourierTransform fft;
	private final Complex[] prev, fold, freq;
	private final float[] weight;
	private final Complex temp;
	private int index;

	public final float[] power;

	ShortTimeFourierTransform(int length, int overlap) {
		fft = new FastFourierTransform(length);
		prev = new Complex[length * overlap];
		for (int i = 0; i < length * overlap; ++i)
			prev[i] = new Complex();
		fold = new Complex[length];
		for (int i = 0; i < length; ++i)
			fold[i] = new Complex();
		freq = new Complex[length];
		for (int i = 0; i < length; ++i)
			freq[i] = new Complex();
		temp = new Complex();
		power = new float[length];
		weight = new float[length * overlap];
		for (int i = 0; i < length * overlap; ++i)
			weight[i] = (float)(Filter.lowPass(1, length, i, length * overlap) * Hann.window(i, length * overlap));
	}

	boolean push(Complex input) {
		prev[index].set(input);
		index = (index + 1) % prev.length;
		if (index % fold.length != 0)
			return false;
		for (int i = 0; i < fold.length; ++i, index = (index + 1) % prev.length)
			fold[i].set(prev[index]).mul(weight[i]);
		for (int i = fold.length; i < prev.length; ++i, index = (index + 1) % prev.length)
			fold[i % fold.length].add(temp.set(prev[index]).mul(weight[i]));
		fft.forward(freq, fold);
		for (int i = 0; i < power.length; ++i)
			power[i] = freq[i].norm();
		return true;
	}
}