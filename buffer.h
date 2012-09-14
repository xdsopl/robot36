/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/

#ifndef BUFFER_H
#define BUFFER_H
struct buffer {
	float *s;
	int last0;
	int last1;
	int len;
};

float *do_buffer(struct buffer *d, float input);
struct buffer *alloc_buffer(int samples);
void free_buffer(struct buffer *buffer);
#endif

