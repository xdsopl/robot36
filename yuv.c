
#include "yuv.h"

uint8_t yuv_limit(float x)
{
	float tmp = x < 0.0 ? 0.0 : x;
	return tmp > 255.0 ? 255.0 : tmp;
}

uint8_t R_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	(void)U;
	return yuv_limit(0.003906 * ((298.082 * (Y - 16.0)) + (408.583 * (V - 128))));
}
uint8_t G_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	return yuv_limit(0.003906 * ((298.082 * (Y - 16.0)) + (-100.291 * (U - 128)) + (-208.12 * (V - 128))));
}
uint8_t B_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	(void)V;
	return yuv_limit(0.003906 * ((298.082 * (Y - 16.0)) + (516.411 * (U - 128))));
}

