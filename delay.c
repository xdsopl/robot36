/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#include "delay.h"
#include <stdlib.h>

float do_delay(delay_t *d, float input)
{
	d->s[d->last] = input;
	d->last = (d->last + 1) < d->len ? d->last + 1 : 0;
	return d->s[d->last];
}

delay_t *alloc_delay(int samples)
{
	int len = samples + 1;
	delay_t *d = malloc(sizeof(delay_t));
	d->s = malloc(sizeof(float) * len);
	d->last = 0;
	d->len = len;
	for (int i = 0; i < len; i++)
		d->s[i] = 0.0;
	return d;
}
void free_delay(delay_t *delay)
{
	free(delay->s);
	free(delay);
}

