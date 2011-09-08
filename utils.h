
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
	return a - a * x + b * x;
}

char *string_time(char *fmt)
{
	static char s[64];
	time_t now = time(0);
	strftime(s, sizeof(s), fmt, localtime(&now));
	return s;
}
#endif

