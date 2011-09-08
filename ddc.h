
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

