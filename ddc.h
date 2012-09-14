/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/

#ifndef DDC_H
#define DDC_H
#include "window.h"

struct ddc {
	complex float *b;
	complex float osc;
	complex float d;
	int N;
	int L;
	int M;
};

void do_ddc(struct ddc *ddc, float *input, complex float *output);
struct ddc *alloc_ddc(int L, int M, float carrier, float bw, float rate, int taps, float (*window)(float, float, float), float a);
void free_ddc(struct ddc *ddc);

#endif

