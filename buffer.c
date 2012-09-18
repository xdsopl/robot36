/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/

#include "buffer.h"
#include <stdlib.h>

float *do_buffer(struct buffer *d, float input)
{
	d->s[d->last0] = input;
	d->s[d->last1] = input;
	int last = d->last0 < d->last1 ? d->last0 : d->last1;
	d->last0 = (d->last0 - 1) < 0 ? d->len - 1 : d->last0 - 1;
	d->last1 = (d->last1 - 1) < 0 ? d->len - 1 : d->last1 - 1;
	return d->s + last;
}

struct buffer *alloc_buffer(int samples)
{
	int len = 2 * samples;
	struct buffer *d = malloc(sizeof(struct buffer));
	d->s = malloc(sizeof(float) * len);
	d->last0 = 0;
	d->last1 = samples;
	d->len = len;
	for (int i = 0; i < len; i++)
		d->s[i] = 0.0;
	return d;
}
void free_buffer(struct buffer *buffer)
{
	free(buffer->s);
	free(buffer);
}

