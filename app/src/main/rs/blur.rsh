/*
Copyright 2014 Ahmet Inan <xdsopl@googlemail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

#ifndef BLUR_RSH
#define BLUR_RSH

#include "exports.rsh"

/* used the following code generator:
#include <math.h>
#include <stdio.h>

double gauss(double x, double radius)
{
	double sigma = radius / 3.0;
	return radius ? exp(- x * x / (2.0 * sigma * sigma)) / sqrt(2.0 * M_PI * sigma * sigma) : 1.0;
}
void emit(int radius)
{
	printf("\t\tif (i < %d || (buffer_length - %d) <= i)\n\t\t\treturn 0;\n", radius, radius);
	int sum = 0;
	for (int i = -radius; i <= radius; ++i)
		sum += 16384 * gauss(i, radius);
	int factor = (16384 * 16384) / (sum + 1);
	for (int i = -radius; i <= radius; ++i)
		printf("\t\t%s%d * value_buffer[i%s%d]%s\n",
			i != -radius ? "\t" : "return (",
			(int)(factor * gauss(i, radius)),
			i < 0 ? "" : "+",
			i,
			i != radius ? " +" : ") >> 14;"
		);
}
int main()
{
	printf("static uchar value_blur(int i)\n{\n\tswitch (blur_power) {\n");
	for (int i = 0; i < 7; ++i) {
		printf("\tcase %d:\n", i);
		emit((1 << i) | 1);
	}
	printf("\tdefault:\n\t\treturn value_buffer[i];\n\t}\n\treturn 0;\n}\n");
	return 0;
}
*/

static uchar value_blur(int i)
{
	switch (blur_power) {
	case 0:
		if (i < 1 || (buffer_length - 1) <= i)
			return 0;
		return (178 * value_buffer[i-1] +
			16027 * value_buffer[i+0] +
			178 * value_buffer[i+1]) >> 14;
	case 1:
		if (i < 3 || (buffer_length - 3) <= i)
			return 0;
		return (72 * value_buffer[i-3] +
			884 * value_buffer[i-2] +
			3966 * value_buffer[i-1] +
			6539 * value_buffer[i+0] +
			3966 * value_buffer[i+1] +
			884 * value_buffer[i+2] +
			72 * value_buffer[i+3]) >> 14;
	case 2:
		if (i < 5 || (buffer_length - 5) <= i)
			return 0;
		return (43 * value_buffer[i-5] +
			220 * value_buffer[i-4] +
			776 * value_buffer[i-3] +
			1911 * value_buffer[i-2] +
			3279 * value_buffer[i-1] +
			3926 * value_buffer[i+0] +
			3279 * value_buffer[i+1] +
			1911 * value_buffer[i+2] +
			776 * value_buffer[i+3] +
			220 * value_buffer[i+4] +
			43 * value_buffer[i+5]) >> 14;
	case 3:
		if (i < 9 || (buffer_length - 9) <= i)
			return 0;
		return (24 * value_buffer[i-9] +
			62 * value_buffer[i-8] +
			143 * value_buffer[i-7] +
			295 * value_buffer[i-6] +
			544 * value_buffer[i-5] +
			897 * value_buffer[i-4] +
			1323 * value_buffer[i-3] +
			1747 * value_buffer[i-2] +
			2064 * value_buffer[i-1] +
			2182 * value_buffer[i+0] +
			2064 * value_buffer[i+1] +
			1747 * value_buffer[i+2] +
			1323 * value_buffer[i+3] +
			897 * value_buffer[i+4] +
			544 * value_buffer[i+5] +
			295 * value_buffer[i+6] +
			143 * value_buffer[i+7] +
			62 * value_buffer[i+8] +
			24 * value_buffer[i+9]) >> 14;
	case 4:
		if (i < 17 || (buffer_length - 17) <= i)
			return 0;
		return (12 * value_buffer[i-17] +
			21 * value_buffer[i-16] +
			34 * value_buffer[i-15] +
			54 * value_buffer[i-14] +
			83 * value_buffer[i-13] +
			122 * value_buffer[i-12] +
			175 * value_buffer[i-11] +
			243 * value_buffer[i-10] +
			327 * value_buffer[i-9] +
			427 * value_buffer[i-8] +
			539 * value_buffer[i-7] +
			660 * value_buffer[i-6] +
			783 * value_buffer[i-5] +
			901 * value_buffer[i-4] +
			1005 * value_buffer[i-3] +
			1087 * value_buffer[i-2] +
			1139 * value_buffer[i-1] +
			1156 * value_buffer[i+0] +
			1139 * value_buffer[i+1] +
			1087 * value_buffer[i+2] +
			1005 * value_buffer[i+3] +
			901 * value_buffer[i+4] +
			783 * value_buffer[i+5] +
			660 * value_buffer[i+6] +
			539 * value_buffer[i+7] +
			427 * value_buffer[i+8] +
			327 * value_buffer[i+9] +
			243 * value_buffer[i+10] +
			175 * value_buffer[i+11] +
			122 * value_buffer[i+12] +
			83 * value_buffer[i+13] +
			54 * value_buffer[i+14] +
			34 * value_buffer[i+15] +
			21 * value_buffer[i+16] +
			12 * value_buffer[i+17]) >> 14;
	case 5:
		if (i < 33 || (buffer_length - 33) <= i)
			return 0;
		return (6 * value_buffer[i-33] +
			8 * value_buffer[i-32] +
			11 * value_buffer[i-31] +
			14 * value_buffer[i-30] +
			18 * value_buffer[i-29] +
			23 * value_buffer[i-28] +
			29 * value_buffer[i-27] +
			36 * value_buffer[i-26] +
			45 * value_buffer[i-25] +
			55 * value_buffer[i-24] +
			67 * value_buffer[i-23] +
			80 * value_buffer[i-22] +
			96 * value_buffer[i-21] +
			114 * value_buffer[i-20] +
			134 * value_buffer[i-19] +
			156 * value_buffer[i-18] +
			180 * value_buffer[i-17] +
			207 * value_buffer[i-16] +
			235 * value_buffer[i-15] +
			265 * value_buffer[i-14] +
			296 * value_buffer[i-13] +
			329 * value_buffer[i-12] +
			361 * value_buffer[i-11] +
			394 * value_buffer[i-10] +
			426 * value_buffer[i-9] +
			457 * value_buffer[i-8] +
			487 * value_buffer[i-7] +
			514 * value_buffer[i-6] +
			538 * value_buffer[i-5] +
			558 * value_buffer[i-4] +
			574 * value_buffer[i-3] +
			586 * value_buffer[i-2] +
			594 * value_buffer[i-1] +
			596 * value_buffer[i+0] +
			594 * value_buffer[i+1] +
			586 * value_buffer[i+2] +
			574 * value_buffer[i+3] +
			558 * value_buffer[i+4] +
			538 * value_buffer[i+5] +
			514 * value_buffer[i+6] +
			487 * value_buffer[i+7] +
			457 * value_buffer[i+8] +
			426 * value_buffer[i+9] +
			394 * value_buffer[i+10] +
			361 * value_buffer[i+11] +
			329 * value_buffer[i+12] +
			296 * value_buffer[i+13] +
			265 * value_buffer[i+14] +
			235 * value_buffer[i+15] +
			207 * value_buffer[i+16] +
			180 * value_buffer[i+17] +
			156 * value_buffer[i+18] +
			134 * value_buffer[i+19] +
			114 * value_buffer[i+20] +
			96 * value_buffer[i+21] +
			80 * value_buffer[i+22] +
			67 * value_buffer[i+23] +
			55 * value_buffer[i+24] +
			45 * value_buffer[i+25] +
			36 * value_buffer[i+26] +
			29 * value_buffer[i+27] +
			23 * value_buffer[i+28] +
			18 * value_buffer[i+29] +
			14 * value_buffer[i+30] +
			11 * value_buffer[i+31] +
			8 * value_buffer[i+32] +
			6 * value_buffer[i+33]) >> 14;
	case 6:
		if (i < 65 || (buffer_length - 65) <= i)
			return 0;
		return (3 * value_buffer[i-65] +
			3 * value_buffer[i-64] +
			4 * value_buffer[i-63] +
			5 * value_buffer[i-62] +
			5 * value_buffer[i-61] +
			6 * value_buffer[i-60] +
			7 * value_buffer[i-59] +
			8 * value_buffer[i-58] +
			9 * value_buffer[i-57] +
			10 * value_buffer[i-56] +
			12 * value_buffer[i-55] +
			13 * value_buffer[i-54] +
			15 * value_buffer[i-53] +
			17 * value_buffer[i-52] +
			19 * value_buffer[i-51] +
			21 * value_buffer[i-50] +
			23 * value_buffer[i-49] +
			26 * value_buffer[i-48] +
			28 * value_buffer[i-47] +
			31 * value_buffer[i-46] +
			35 * value_buffer[i-45] +
			38 * value_buffer[i-44] +
			42 * value_buffer[i-43] +
			46 * value_buffer[i-42] +
			50 * value_buffer[i-41] +
			55 * value_buffer[i-40] +
			60 * value_buffer[i-39] +
			65 * value_buffer[i-38] +
			70 * value_buffer[i-37] +
			76 * value_buffer[i-36] +
			82 * value_buffer[i-35] +
			88 * value_buffer[i-34] +
			95 * value_buffer[i-33] +
			102 * value_buffer[i-32] +
			109 * value_buffer[i-31] +
			116 * value_buffer[i-30] +
			123 * value_buffer[i-29] +
			131 * value_buffer[i-28] +
			139 * value_buffer[i-27] +
			147 * value_buffer[i-26] +
			156 * value_buffer[i-25] +
			164 * value_buffer[i-24] +
			172 * value_buffer[i-23] +
			181 * value_buffer[i-22] +
			189 * value_buffer[i-21] +
			198 * value_buffer[i-20] +
			206 * value_buffer[i-19] +
			215 * value_buffer[i-18] +
			223 * value_buffer[i-17] +
			231 * value_buffer[i-16] +
			238 * value_buffer[i-15] +
			246 * value_buffer[i-14] +
			253 * value_buffer[i-13] +
			260 * value_buffer[i-12] +
			266 * value_buffer[i-11] +
			272 * value_buffer[i-10] +
			278 * value_buffer[i-9] +
			283 * value_buffer[i-8] +
			288 * value_buffer[i-7] +
			292 * value_buffer[i-6] +
			295 * value_buffer[i-5] +
			298 * value_buffer[i-4] +
			300 * value_buffer[i-3] +
			302 * value_buffer[i-2] +
			303 * value_buffer[i-1] +
			303 * value_buffer[i+0] +
			303 * value_buffer[i+1] +
			302 * value_buffer[i+2] +
			300 * value_buffer[i+3] +
			298 * value_buffer[i+4] +
			295 * value_buffer[i+5] +
			292 * value_buffer[i+6] +
			288 * value_buffer[i+7] +
			283 * value_buffer[i+8] +
			278 * value_buffer[i+9] +
			272 * value_buffer[i+10] +
			266 * value_buffer[i+11] +
			260 * value_buffer[i+12] +
			253 * value_buffer[i+13] +
			246 * value_buffer[i+14] +
			238 * value_buffer[i+15] +
			231 * value_buffer[i+16] +
			223 * value_buffer[i+17] +
			215 * value_buffer[i+18] +
			206 * value_buffer[i+19] +
			198 * value_buffer[i+20] +
			189 * value_buffer[i+21] +
			181 * value_buffer[i+22] +
			172 * value_buffer[i+23] +
			164 * value_buffer[i+24] +
			156 * value_buffer[i+25] +
			147 * value_buffer[i+26] +
			139 * value_buffer[i+27] +
			131 * value_buffer[i+28] +
			123 * value_buffer[i+29] +
			116 * value_buffer[i+30] +
			109 * value_buffer[i+31] +
			102 * value_buffer[i+32] +
			95 * value_buffer[i+33] +
			88 * value_buffer[i+34] +
			82 * value_buffer[i+35] +
			76 * value_buffer[i+36] +
			70 * value_buffer[i+37] +
			65 * value_buffer[i+38] +
			60 * value_buffer[i+39] +
			55 * value_buffer[i+40] +
			50 * value_buffer[i+41] +
			46 * value_buffer[i+42] +
			42 * value_buffer[i+43] +
			38 * value_buffer[i+44] +
			35 * value_buffer[i+45] +
			31 * value_buffer[i+46] +
			28 * value_buffer[i+47] +
			26 * value_buffer[i+48] +
			23 * value_buffer[i+49] +
			21 * value_buffer[i+50] +
			19 * value_buffer[i+51] +
			17 * value_buffer[i+52] +
			15 * value_buffer[i+53] +
			13 * value_buffer[i+54] +
			12 * value_buffer[i+55] +
			10 * value_buffer[i+56] +
			9 * value_buffer[i+57] +
			8 * value_buffer[i+58] +
			7 * value_buffer[i+59] +
			6 * value_buffer[i+60] +
			5 * value_buffer[i+61] +
			5 * value_buffer[i+62] +
			4 * value_buffer[i+63] +
			3 * value_buffer[i+64] +
			3 * value_buffer[i+65]) >> 14;
	default:
		return value_buffer[i];
	}
	return 0;
}

#endif
