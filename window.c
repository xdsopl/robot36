/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#include <math.h>
#include "window.h"

float sinc(float x)
{
	return 0 == x ? 1.0 : sinf(M_PI * x) / (M_PI * x);
}
float hann(float n, float N, float a)
{
	(void)a;
	return 0.5 * (1.0 - cosf(2.0 * M_PI * n / (N - 1.0)));
}
float hamming(float n, float N, float a)
{
	(void)a;
	return 0.54 - 0.46 * cosf(2.0 * M_PI * n / (N - 1.0));
}
float lanczos(float n, float N, float a)
{
	(void)a;
	return sinc(2.0 * n / (N - 1.0) - 1.0);
}
float gauss(float n, float N, float o)
{
	return expf(- 1.0/2.0 * powf((n - (N - 1.0) / 2.0) / (o * (N - 1.0) / 2.0), 2.0));
}

float i0f(float x)
{
	// converges for -3*M_PI:3*M_PI in less than 20 iterations
	float sum = 1.0, val = 1.0, c = 0.0;
	for (int n = 1; n < 20; n++) {
		float tmp = x / (2 * n);
		val *= tmp * tmp;
		float y = val - c;
		float t = sum + y;
		c = (t - sum) - y;
		sum = t;
	}
	return sum;
}
float kaiser(float n, float N, float a)
{
	return i0f(M_PI * a * sqrtf(1.0 - powf((2.0 * n) / (N - 1.0) - 1.0, 2.0))) / i0f(M_PI * a);
}

