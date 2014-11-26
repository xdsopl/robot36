/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/

#include <complex.h>
#include <math.h>
#include <stdlib.h>
#include "window.h"
#include "ddc.h"

void do_ddc(struct ddc *ddc, float *input, complex float *output)
{
	int N = ddc->N;
	int M = ddc->M;
	int L = ddc->L;
	int offset = 0;
	for (int k = 0; k < L; k++) {
		float *x = input + (((L * M - 1) - offset) / L);
		complex float *b = ddc->b + (N * (offset % L));
		offset += M;

		complex float sum = 0.0;
		for (int i = 0; i < N; i++)
			sum += b[i] * x[i];

		output[k] = ddc->osc * sum;
		ddc->osc *= ddc->d;
		ddc->osc /= cabsf(ddc->osc);
	}
}
struct ddc *alloc_ddc(int L, int M, float carrier, float bw, float rate, int taps, float (*window)(float, float, float), float a)
{
	float lstep = 1.0 / ((float)L * rate);
	float ostep = (float)M * lstep;
	struct ddc *ddc = malloc(sizeof(struct ddc));
	ddc->N = (taps + L - 1) / L;
	ddc->b = malloc(sizeof(complex float) * ddc->N * L);
	ddc->osc = I;
	ddc->d = cexpf(-I * 2.0 * M_PI * carrier * ostep);
	ddc->L = L;
	ddc->M = M;
	complex float *b = malloc(sizeof(complex float) * taps);
	float sum = 0.0;
	for (int i = 0; i < taps; i++) {
		float N = taps;
		float n = i;
		float x = n - (N - 1.0) / 2.0;
		float l = 2.0 * bw * lstep;
		float h = l * sinc(l * x);
		float w = window(n, N, a);
		float t = w * h;
		sum += t;
		complex float o = cexpf(I * 2.0 * M_PI * carrier * lstep * n);
		b[i] = t * o * (float)L;
	}
	for (int i = 0; i < taps; i++)
		b[i] /= sum;
	for (int i = 0; i < ddc->N * L; i++)
		ddc->b[i] = 0.0;
	for (int i = 0; i < L; i++)
		for (int j = i, k = 0; j < taps; j += L, k++)
			ddc->b[i * ddc->N + k] = b[j];
	free(b);
	return ddc;
}
void free_ddc(struct ddc *ddc)
{
	free(ddc->b);
	free(ddc);
}

