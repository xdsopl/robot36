/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#include <math.h>
#include <stdlib.h>
#include <complex.h> 
#include "window.h"
#include "ddc.h"

void do_ddc(ddc_t *ddc, float *input, float complex *output)
{
	// this works only for L <= M
	for (int k = 0, last = ddc->last, in = 0; k < ddc->L; k++) {
		while (ddc->skip < ddc->M) {
			ddc->s[ddc->last] = input[in++];
			last = ddc->last;
			ddc->last = (ddc->last + 1) < ddc->samples ? ddc->last + 1 : 0;
			ddc->skip += ddc->L;
		}

		ddc->skip %= ddc->M;

		float complex sum = 0.0;
		for (int i = ddc->offset; i < ddc->taps; i += ddc->L) {
			sum += ddc->b[i] * ddc->s[last];
			last += last ? - 1 : ddc->samples - 1;
		}

		ddc->offset = (ddc->offset + ddc->M) % ddc->L;

		output[k] = ddc->osc * sum;
		ddc->osc *= ddc->d;
//		ddc->osc /= cabsf(ddc->osc); // not really needed
	}
}
ddc_t *alloc_ddc(float freq, float bw, float step, int taps, int L, int M, float (*window)(float, float, float), float a)
{
	float lstep = step / (float)L;
	float ostep = step * (float)M / (float)L;
	ddc_t *ddc = malloc(sizeof(ddc_t));
	ddc->taps = taps;
	ddc->samples = (taps + L - 1) / L;
	ddc->b = malloc(sizeof(float complex) * ddc->taps);
	ddc->s = malloc(sizeof(float) * ddc->samples);
	ddc->osc = I;
	ddc->d = cexpf(-I * 2.0 * M_PI * freq * ostep);
	ddc->offset = (M - 1) % L;
	ddc->last = 0;
	ddc->skip = 0;
	ddc->L = L;
	ddc->M = M;
	for (int i = 0; i < ddc->samples; i++)
		ddc->s[i] = 0.0;
	float sum = 0.0;
	for (int i = 0; i < ddc->taps; i++) {
		float N = (float)ddc->taps;
		float n = (float)i;
		float x = n - (N - 1.0) / 2.0;
		float l = 2.0 * M_PI * bw * lstep;
		float w = window(n, ddc->taps, a);
		float h = 0.0 == x ? l / M_PI : sinf(l * x) / (x * M_PI);
		float b = w * h;
		sum += b;
		complex float o = cexpf(I * 2.0 * M_PI * freq * lstep * n);
		ddc->b[i] = b * o * (float)L;
	}
	for (int i = 0; i < ddc->taps; i++)
		ddc->b[i] /= sum;
	return ddc;
}
void free_ddc(ddc_t *ddc)
{
	free(ddc->b);
	free(ddc->s);
	free(ddc);
}

