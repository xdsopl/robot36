/*
Fast Fourier Transform

Copyright 2025 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

public class FastFourierTransform {
	private final Complex[] tf;
	private final Complex tmpA, tmpB, tmpC, tmpD, tmpE, tmpF, tmpG, tmpH, tmpI, tmpJ, tmpK, tmpL, tmpM;
	private final Complex tin0, tin1, tin2, tin3, tin4, tin5, tin6;

	FastFourierTransform(int length) {
		int rest = length;
		while (rest > 1) {
			if (rest % 2 == 0)
				rest /= 2;
			else if (rest % 3 == 0)
				rest /= 3;
			else if (rest % 5 == 0)
				rest /= 5;
			else if (rest % 7 == 0)
				rest /= 7;
			else
				break;
		}
		if (rest != 1)
			throw new IllegalArgumentException(
				"Transform length must be a composite of 2, 3, 5 and 7, but was: "
				+ length);
		tf = new Complex[length];
		for (int i = 0; i < length; ++i) {
			double x = -(2.0 * Math.PI * i) / length;
			float a = (float) Math.cos(x);
			float b = (float) Math.sin(x);
			tf[i] = new Complex(a, b);
		}
		tmpA = new Complex();
		tmpB = new Complex();
		tmpC = new Complex();
		tmpD = new Complex();
		tmpE = new Complex();
		tmpF = new Complex();
		tmpG = new Complex();
		tmpH = new Complex();
		tmpI = new Complex();
		tmpJ = new Complex();
		tmpK = new Complex();
		tmpL = new Complex();
		tmpM = new Complex();
		tin0 = new Complex();
		tin1 = new Complex();
		tin2 = new Complex();
		tin3 = new Complex();
		tin4 = new Complex();
		tin5 = new Complex();
		tin6 = new Complex();
	}

	private boolean isPowerOfTwo(int n) {
		return n > 0 && (n & (n - 1)) == 0;
	}

	private boolean isPowerOfFour(int n) {
		return isPowerOfTwo(n) && (n & 0x55555555) != 0;
	}

	private float cos(int n, int N) {
		return (float) Math.cos(n * 2.0 * Math.PI / N);
	}

	private float sin(int n, int N) {
		return (float) Math.sin(n * 2.0 * Math.PI / N);
	}

	private void dft2(Complex out0, Complex out1, Complex in0, Complex in1) {
		out0.set(in0).add(in1);
		out1.set(in0).sub(in1);
	}

	private void radix2(Complex[] out, Complex[] in, int O, int I, int N, int S, boolean F) {
		if (N == 2) {
			dft2(out[O], out[O + 1], in[I], in[I + S]);
		} else {
			int Q = N / 2;
			dit(out, in, O, I, Q, 2 * S, F);
			dit(out, in, O + Q, I + S, Q, 2 * S, F);
			for (int k0 = O, k1 = O + Q, l1 = 0; k0 < O + Q; ++k0, ++k1, l1 += S) {
				tin1.set(tf[l1]);
				if (!F)
					tin1.conj();
				tin0.set(out[k0]);
				tin1.mul(out[k1]);
				dft2(out[k0], out[k1], tin0, tin1);
			}
		}
	}

	private void fwd3(Complex out0, Complex out1, Complex out2, Complex in0, Complex in1, Complex in2) {
		tmpA.set(in1).add(in2);
		tmpB.set(in1.imag - in2.imag, in2.real - in1.real);
		tmpC.set(tmpA).mul(cos(1, 3));
		tmpD.set(tmpB).mul(sin(1, 3));
		out0.set(in0).add(tmpA);
		out1.set(in0).add(tmpC).add(tmpD);
		out2.set(in0).add(tmpC).sub(tmpD);
	}

	private void radix3(Complex[] out, Complex[] in, int O, int I, int N, int S, boolean F) {
		if (N == 3) {
			if (F)
				fwd3(out[O], out[O + 1], out[O + 2],
					in[I], in[I + S], in[I + 2 * S]);
			else
				fwd3(out[O], out[O + 2], out[O + 1],
					in[I], in[I + S], in[I + 2 * S]);
		} else {
			int Q = N / 3;
			dit(out, in, O, I, Q, 3 * S, F);
			dit(out, in, O + Q, I + S, Q, 3 * S, F);
			dit(out, in, O + 2 * Q, I + 2 * S, Q, 3 * S, F);
			for (int k0 = O, k1 = O + Q, k2 = O + 2 * Q, l1 = 0, l2 = 0;
					k0 < O + Q; ++k0, ++k1, ++k2, l1 += S, l2 += 2 * S) {
				tin1.set(tf[l1]);
				tin2.set(tf[l2]);
				if (!F) {
					tin1.conj();
					tin2.conj();
				}
				tin0.set(out[k0]);
				tin1.mul(out[k1]);
				tin2.mul(out[k2]);
				if (F)
					fwd3(out[k0], out[k1], out[k2], tin0, tin1, tin2);
				else
					fwd3(out[k0], out[k2], out[k1], tin0, tin1, tin2);
			}
		}
	}

	private void fwd4(Complex out0, Complex out1, Complex out2, Complex out3,
			Complex in0, Complex in1, Complex in2, Complex in3) {
		tmpA.set(in0).add(in2);
		tmpB.set(in0).sub(in2);
		tmpC.set(in1).add(in3);
		tmpD.set(in1.imag - in3.imag, in3.real - in1.real);
		out0.set(tmpA).add(tmpC);
		out1.set(tmpB).add(tmpD);
		out2.set(tmpA).sub(tmpC);
		out3.set(tmpB).sub(tmpD);
	}

	private void radix4(Complex[] out, Complex[] in, int O, int I, int N, int S, boolean F) {
		if (N == 4) {
			if (F)
				fwd4(out[O], out[O + 1], out[O + 2], out[O + 3],
					in[I], in[I + S], in[I + 2 * S], in[I + 3 * S]);
			else
				fwd4(out[O], out[O + 3], out[O + 2], out[O + 1],
					in[I], in[I + S], in[I + 2 * S], in[I + 3 * S]);
		} else {
			int Q = N / 4;
			radix4(out, in, O, I, Q, 4 * S, F);
			radix4(out, in, O + Q, I + S, Q, 4 * S, F);
			radix4(out, in, O + 2 * Q, I + 2 * S, Q, 4 * S, F);
			radix4(out, in, O + 3 * Q, I + 3 * S, Q, 4 * S, F);
			for (int k0 = O, k1 = O + Q, k2 = O + 2 * Q, k3 = O + 3 * Q, l1 = 0, l2 = 0, l3 = 0;
					k0 < O + Q; ++k0, ++k1, ++k2, ++k3, l1 += S, l2 += 2 * S, l3 += 3 * S) {
				tin1.set(tf[l1]);
				tin2.set(tf[l2]);
				tin3.set(tf[l3]);
				if (!F) {
					tin1.conj();
					tin2.conj();
					tin3.conj();
				}
				tin0.set(out[k0]);
				tin1.mul(out[k1]);
				tin2.mul(out[k2]);
				tin3.mul(out[k3]);
				if (F)
					fwd4(out[k0], out[k1], out[k2], out[k3], tin0, tin1, tin2, tin3);
				else
					fwd4(out[k0], out[k3], out[k2], out[k1], tin0, tin1, tin2, tin3);
			}
		}
	}

	private void fwd5(Complex out0, Complex out1, Complex out2, Complex out3, Complex out4,
			Complex in0, Complex in1, Complex in2, Complex in3, Complex in4) {
		tmpA.set(in1).add(in4);
		tmpB.set(in2).add(in3);
		tmpC.set(in1.imag - in4.imag, in4.real - in1.real);
		tmpD.set(in2.imag - in3.imag, in3.real - in2.real);
		tmpF.set(tmpA).mul(cos(1, 5)).add(tmpE.set(tmpB).mul(cos(2, 5)));
		tmpG.set(tmpC).mul(sin(1, 5)).add(tmpE.set(tmpD).mul(sin(2, 5)));
		tmpH.set(tmpA).mul(cos(2, 5)).add(tmpE.set(tmpB).mul(cos(1, 5)));
		tmpI.set(tmpC).mul(sin(2, 5)).sub(tmpE.set(tmpD).mul(sin(1, 5)));
		out0.set(in0).add(tmpA).add(tmpB);
		out1.set(in0).add(tmpF).add(tmpG);
		out2.set(in0).add(tmpH).add(tmpI);
		out3.set(in0).add(tmpH).sub(tmpI);
		out4.set(in0).add(tmpF).sub(tmpG);
	}

	private void radix5(Complex[] out, Complex[] in, int O, int I, int N, int S, boolean F) {
		if (N == 5) {
			if (F)
				fwd5(out[O], out[O + 1], out[O + 2], out[O + 3], out[O + 4],
					in[I], in[I + S], in[I + 2 * S], in[I + 3 * S], in[I + 4 * S]);
			else
				fwd5(out[O], out[O + 4], out[O + 3], out[O + 2], out[O + 1],
					in[I], in[I + S], in[I + 2 * S], in[I + 3 * S], in[I + 4 * S]);
		} else {
			int Q = N / 5;
			dit(out, in, O, I, Q, 5 * S, F);
			dit(out, in, O + Q, I + S, Q, 5 * S, F);
			dit(out, in, O + 2 * Q, I + 2 * S, Q, 5 * S, F);
			dit(out, in, O + 3 * Q, I + 3 * S, Q, 5 * S, F);
			dit(out, in, O + 4 * Q, I + 4 * S, Q, 5 * S, F);
			for (int k0 = O, k1 = O + Q, k2 = O + 2 * Q, k3 = O + 3 * Q, k4 = O + 4 * Q, l1 = 0, l2 = 0, l3 = 0, l4 = 0;
					k0 < O + Q; ++k0, ++k1, ++k2, ++k3, ++k4, l1 += S, l2 += 2 * S, l3 += 3 * S, l4 += 4 * S) {
				tin1.set(tf[l1]);
				tin2.set(tf[l2]);
				tin3.set(tf[l3]);
				tin4.set(tf[l4]);
				if (!F) {
					tin1.conj();
					tin2.conj();
					tin3.conj();
					tin4.conj();
				}
				tin0.set(out[k0]);
				tin1.mul(out[k1]);
				tin2.mul(out[k2]);
				tin3.mul(out[k3]);
				tin4.mul(out[k4]);
				if (F)
					fwd5(out[k0], out[k1], out[k2], out[k3], out[k4], tin0, tin1, tin2, tin3, tin4);
				else
					fwd5(out[k0], out[k4], out[k3], out[k2], out[k1], tin0, tin1, tin2, tin3, tin4);
			}
		}
	}

	private void fwd7(Complex out0, Complex out1, Complex out2, Complex out3, Complex out4, Complex out5, Complex out6,
			Complex in0, Complex in1, Complex in2, Complex in3, Complex in4, Complex in5, Complex in6) {
		tmpA.set(in1).add(in6);
		tmpB.set(in2).add(in5);
		tmpC.set(in3).add(in4);
		tmpD.set(in1.imag - in6.imag, in6.real - in1.real);
		tmpE.set(in2.imag - in5.imag, in5.real - in2.real);
		tmpF.set(in3.imag - in4.imag, in4.real - in3.real);
		tmpH.set(tmpA).mul(cos(1, 7)).add(tmpG.set(tmpB).mul(cos(2, 7))).add(tmpG.set(tmpC).mul(cos(3, 7)));
		tmpI.set(tmpD).mul(sin(1, 7)).add(tmpG.set(tmpE).mul(sin(2, 7))).add(tmpG.set(tmpF).mul(sin(3, 7)));
		tmpJ.set(tmpA).mul(cos(2, 7)).add(tmpG.set(tmpB).mul(cos(3, 7))).add(tmpG.set(tmpC).mul(cos(1, 7)));
		tmpK.set(tmpD).mul(sin(2, 7)).sub(tmpG.set(tmpE).mul(sin(3, 7))).sub(tmpG.set(tmpF).mul(sin(1, 7)));
		tmpL.set(tmpA).mul(cos(3, 7)).add(tmpG.set(tmpB).mul(cos(1, 7))).add(tmpG.set(tmpC).mul(cos(2, 7)));
		tmpM.set(tmpD).mul(sin(3, 7)).sub(tmpG.set(tmpE).mul(sin(1, 7))).add(tmpG.set(tmpF).mul(sin(2, 7)));
		out0.set(in0).add(tmpA).add(tmpB).add(tmpC);
		out1.set(in0).add(tmpH).add(tmpI);
		out2.set(in0).add(tmpJ).add(tmpK);
		out3.set(in0).add(tmpL).add(tmpM);
		out4.set(in0).add(tmpL).sub(tmpM);
		out5.set(in0).add(tmpJ).sub(tmpK);
		out6.set(in0).add(tmpH).sub(tmpI);
	}

	private void radix7(Complex[] out, Complex[] in, int O, int I, int N, int S, boolean F) {
		if (N == 7) {
			if (F)
				fwd7(out[O], out[O + 1], out[O + 2], out[O + 3], out[O + 4], out[O + 5], out[O + 6],
					in[I], in[I + S], in[I + 2 * S], in[I + 3 * S], in[I + 4 * S], in[I + 5 * S], in[I + 6 * S]);
			else
				fwd7(out[O], out[O + 6], out[O + 5], out[O + 4], out[O + 3], out[O + 2], out[O + 1],
					in[I], in[I + S], in[I + 2 * S], in[I + 3 * S], in[I + 4 * S], in[I + 5 * S], in[I + 6 * S]);
		} else {
			int Q = N / 7;
			dit(out, in, O, I, Q, 7 * S, F);
			dit(out, in, O + Q, I + S, Q, 7 * S, F);
			dit(out, in, O + 2 * Q, I + 2 * S, Q, 7 * S, F);
			dit(out, in, O + 3 * Q, I + 3 * S, Q, 7 * S, F);
			dit(out, in, O + 4 * Q, I + 4 * S, Q, 7 * S, F);
			dit(out, in, O + 5 * Q, I + 5 * S, Q, 7 * S, F);
			dit(out, in, O + 6 * Q, I + 6 * S, Q, 7 * S, F);
			for (int k0 = O, k1 = O + Q, k2 = O + 2 * Q, k3 = O + 3 * Q, k4 = O + 4 * Q, k5 = O + 5 * Q, k6 = O + 6 * Q, l1 = 0, l2 = 0, l3 = 0, l4 = 0, l5 = 0, l6 = 0;
					k0 < O + Q; ++k0, ++k1, ++k2, ++k3, ++k4, ++k5, ++k6, l1 += S, l2 += 2 * S, l3 += 3 * S, l4 += 4 * S, l5 += 5 * S, l6 += 6 * S) {
				tin1.set(tf[l1]);
				tin2.set(tf[l2]);
				tin3.set(tf[l3]);
				tin4.set(tf[l4]);
				tin5.set(tf[l5]);
				tin6.set(tf[l6]);
				if (!F) {
					tin1.conj();
					tin2.conj();
					tin3.conj();
					tin4.conj();
					tin5.conj();
					tin6.conj();
				}
				tin0.set(out[k0]);
				tin1.mul(out[k1]);
				tin2.mul(out[k2]);
				tin3.mul(out[k3]);
				tin4.mul(out[k4]);
				tin5.mul(out[k5]);
				tin6.mul(out[k6]);
				if (F)
					fwd7(out[k0], out[k1], out[k2], out[k3], out[k4], out[k5], out[k6], tin0, tin1, tin2, tin3, tin4, tin5, tin6);
				else
					fwd7(out[k0], out[k6], out[k5], out[k4], out[k3], out[k2], out[k1], tin0, tin1, tin2, tin3, tin4, tin5, tin6);
			}
		}
	}

	private void dit(Complex[] out, Complex[] in, int O, int I, int N, int S, boolean F) {
		if (N == 1)
			out[O].set(in[I]);
		else if (isPowerOfFour(N))
			radix4(out, in, O, I, N, S, F);
		else if (N % 7 == 0)
			radix7(out, in, O, I, N, S, F);
		else if (N % 5 == 0)
			radix5(out, in, O, I, N, S, F);
		else if (N % 3 == 0)
			radix3(out, in, O, I, N, S, F);
		else if (N % 2 == 0)
			radix2(out, in, O, I, N, S, F);
	}

	void forward(Complex[] out, Complex[] in) {
		if (in.length != tf.length)
			throw new IllegalArgumentException("Input array length (" + in.length
				+ ") must be equal to Transform length (" + tf.length + ")");
		if (out.length != tf.length)
			throw new IllegalArgumentException("Output array length (" + out.length
				+ ") must be equal to Transform length (" + tf.length + ")");
		dit(out, in, 0, 0, tf.length, 1, true);
	}

	@SuppressWarnings("unused")
	void backward(Complex[] out, Complex[] in) {
		if (in.length != tf.length)
			throw new IllegalArgumentException("Input array length (" + in.length
				+ ") must be equal to Transform length (" + tf.length + ")");
		if (out.length != tf.length)
			throw new IllegalArgumentException("Output array length (" + out.length
				+ ") must be equal to Transform length (" + tf.length + ")");
		dit(out, in, 0, 0, tf.length, 1, false);
	}
}
