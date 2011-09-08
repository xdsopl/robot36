
#ifndef DELAY_H
#define DELAY_H
typedef struct {
	float *s;
	int last;
	int len;
} delay_t;

float do_delay(delay_t *, float);
delay_t *alloc_delay(int);
void free_delay(delay_t *);
#endif

