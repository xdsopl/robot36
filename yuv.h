/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#ifndef YUV_H
#define YUV_H
#include <stdint.h>

uint8_t srgb(float linear);
float linear(uint8_t srgb);
uint8_t R_YUV(uint8_t, uint8_t, uint8_t);
uint8_t G_YUV(uint8_t, uint8_t, uint8_t);
uint8_t B_YUV(uint8_t, uint8_t, uint8_t);
uint8_t Y_RGB(uint8_t, uint8_t, uint8_t);
uint8_t V_RGB(uint8_t, uint8_t, uint8_t);
uint8_t U_RGB(uint8_t, uint8_t, uint8_t);
#endif
