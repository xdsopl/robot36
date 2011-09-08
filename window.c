
#include <math.h>
#include "window.h"

float sinc(float x)
{
	return 0 == x ? 1.0 : sinf(M_PI * x) / (M_PI * x);
}
float hann(float n, float N)
{
	return 0.5 * (1.0 - cosf(2.0 * M_PI * n / (N - 1.0)));
}
float hamming(float n, float N)
{
	return 0.54 - 0.46 * cosf(2.0 * M_PI * n / (N - 1.0));
}
float lanczos(float n, float N)
{
	return sinc(2.0 * n / (N - 1.0) - 1.0);
}
float gauss(float n, float N)
{
	float o = 0.35;
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
float kaiser(float n, float N)
{
	float a = 2.0;
	return i0f(M_PI * a * sqrtf(1.0 - powf((2.0 * n) / (N - 1.0) - 1.0, 2.0))) / i0f(M_PI * a);
}

