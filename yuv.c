/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#include "yuv.h"

uint8_t yuv_clamp(float x)
{
	float tmp = x < 0.0 ? 0.0 : x;
	return tmp > 255.0 ? 255.0 : tmp;
}

uint8_t R_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	(void)U;
	return yuv_clamp(0.003906 * ((298.082 * (Y - 16.0)) + (408.583 * (V - 128))));
}
uint8_t G_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	return yuv_clamp(0.003906 * ((298.082 * (Y - 16.0)) + (-100.291 * (U - 128)) + (-208.12 * (V - 128))));
}
uint8_t B_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	(void)V;
	return yuv_clamp(0.003906 * ((298.082 * (Y - 16.0)) + (516.411 * (U - 128))));
}

uint8_t Y_RGB(uint8_t R, uint8_t G, uint8_t B)
{
	return yuv_clamp(16.0 + (0.003906 * ((65.738 * R) + (129.057 * G) + (25.064 * B))));
}
uint8_t V_RGB(uint8_t R, uint8_t G, uint8_t B)
{
	return yuv_clamp(128.0 + (0.003906 * ((112.439 * R) + (-94.154 * G) + (-18.285 * B))));
}
uint8_t U_RGB(uint8_t R, uint8_t G, uint8_t B)
{
	return yuv_clamp(128.0 + (0.003906 * ((-37.945 * R) + (-74.494 * G) + (112.439 * B))));
}

