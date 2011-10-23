/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#ifndef DDC_H
#define DDC_H
#include "window.h"

typedef struct {
	float complex *b;
	float *s;
	float complex osc;
	float complex d;
	int offset;
	int skip;
	int last;
	int taps;
	int samples;
	int L;
	int M;
} ddc_t;

void do_ddc(ddc_t *, float *, float complex *);
ddc_t *alloc_ddc(float, float, float, int, int, int, float (*)(float, float, float), float);
void free_ddc(ddc_t *);

#endif

