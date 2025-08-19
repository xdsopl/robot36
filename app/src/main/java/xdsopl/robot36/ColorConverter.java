/*
Color converter

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

import android.graphics.Color;

public final class ColorConverter {

	private static int clamp(int value) {
		return Math.min(Math.max(value, 0), 255);
	}

	private static float clamp(float value) {
		return Math.min(Math.max(value, 0), 1);
	}

	private static int float2int(float level) {
		int intensity = Math.round(255 * level);
		return clamp(intensity);
	}

	private static int compress(float level) {
		float compressed = (float) Math.sqrt(clamp(level));
		return float2int(compressed);
	}

	private static int YUV2RGB(int Y, int U, int V) {
		Y -= 16;
		U -= 128;
		V -= 128;
		int R = clamp((298 * Y + 409 * V + 128) >> 8);
		int G = clamp((298 * Y - 100 * U - 208 * V + 128) >> 8);
		int B = clamp((298 * Y + 516 * U + 128) >> 8);
		return 0xff000000 | (R << 16) | (G << 8) | B;
	}

	private static int[] argb2components(int argb) {
		return new int[] { (argb >> 24) & 0xff, (argb >> 16) & 0xff, (argb >> 8) & 0xff, argb & 0xff };
	}

	public static int blend(int argbLeft, int argbRight, float ratioOfArgbRight) {
		int[] componentsLeft = argb2components(argbLeft);
		int[] componentsRight = argb2components(argbRight);
		int[] output = new int[4];

		ratioOfArgbRight = clamp(ratioOfArgbRight);

		for (int i = 0; i < 4; i++) {
			output[i] = clamp(Math.round(componentsLeft[i] * (1 - ratioOfArgbRight) + componentsRight[i] * ratioOfArgbRight));
		}

		return Color.argb(output[0], output[1], output[2], output[3]);
	}

	public static int GRAY(float level) {
		return 0xff000000 | 0x00010101 * compress(level);
	}

	public static int RGB(float red, float green, float blue) {
		return 0xff000000 | (float2int(red) << 16) | (float2int(green) << 8) | float2int(blue);
	}

	public static int YUV2RGB(float Y, float U, float V) {
		return YUV2RGB(float2int(Y), float2int(U), float2int(V));
	}

	public static int YUV2RGB(int YUV) {
		return YUV2RGB((YUV & 0x00ff0000) >> 16, (YUV & 0x0000ff00) >> 8, YUV & 0x000000ff);
	}
}
