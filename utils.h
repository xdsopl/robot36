/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#ifndef UTILS_H
#define UTILS_H
#include <stdint.h>
#include <time.h>

int64_t gcd(int64_t a, int64_t b)
{
	int64_t c;
	while ((c = a % b)) {
		a = b;
		b = c;
	}
	return b;
}

float fclampf(float x, float min, float max)
{
	float tmp = x < min ? min : x;
	return tmp > max ? max : tmp;
}

float flerpf(float a, float b, float x)
{
	return a * (1.0 - x) + b * x;
}

char *string_time(char *fmt)
{
	static char s[64];
	time_t now = time(0);
	strftime(s, sizeof(s), fmt, localtime(&now));
	return s;
}
#endif

