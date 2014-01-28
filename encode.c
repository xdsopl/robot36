/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include <complex.h>
#include <limits.h>
#include "yuv.h"
#include "utils.h"
#include "pcm.h"
#include "img.h"

struct img *img;
struct pcm *pcm;
complex float nco;
float hz2rad;
int channels;
short *buff;
int rate = 48000;

const double sync_porch_sec = 0.003l;
const double porch_sec = 0.0015l;
const double y_sec = 0.088l;
const double uv_sec = 0.044l;
const double hor_sync_sec = 0.009l;
const double seperator_sec = 0.0045l;

int sync_porch_len = 0;
int porch_len = 0;
int y_len = 0;
int uv_len = 0;
int hor_sync_len = 0;
int seperator_len = 0;

int add_sample(float val)
{
	for (int i = 0; i < channels; i++)
		buff[i] = (float)SHRT_MAX * val;
	return write_pcm(pcm, buff, 1);
}
void add_freq(float freq)
{
	add_sample(creal(nco));
	nco *= cexpf(freq * hz2rad * I);
}

void hor_sync()
{
	for (int ticks = 0; ticks < hor_sync_len; ticks++)
		add_freq(1200.0);
}
void sync_porch()
{
	for (int ticks = 0; ticks < sync_porch_len; ticks++)
		add_freq(1500.0);
}
void porch()
{
	for (int ticks = 0; ticks < porch_len; ticks++)
		add_freq(1900.0);
}
void even_seperator()
{
	for (int ticks = 0; ticks < seperator_len; ticks++)
		add_freq(1500.0);
}
void odd_seperator()
{
	for (int ticks = 0; ticks < seperator_len; ticks++)
		add_freq(2300.0);
}
void y_scan(int y)
{
	for (int ticks = 0; ticks < y_len; ticks++) {
		float xf = fclampf((320.0 * ticks) / (float)y_len, 0.0, 319.0);
		int x0 = xf;
		int x1 = fclampf(x0 + 1, 0.0, 319.0);
		int off0 = 3 * y * img->width + 3 * x0;
		int off1 = 3 * y * img->width + 3 * x1;
		float R0 = linear(img->pixel[off0 + 0]);
		float G0 = linear(img->pixel[off0 + 1]);
		float B0 = linear(img->pixel[off0 + 2]);
		float R1 = linear(img->pixel[off1 + 0]);
		float G1 = linear(img->pixel[off1 + 1]);
		float B1 = linear(img->pixel[off1 + 2]);
		uint8_t R = srgb(flerpf(R0, R1, xf - (float)x0));
		uint8_t G = srgb(flerpf(G0, G1, xf - (float)x0));
		uint8_t B = srgb(flerpf(B0, B1, xf - (float)x0));
		add_freq(1500.0 + 800.0 * Y_RGB(R, G, B) / 255.0);
	}
}

void v_scan(int y)
{
	for (int ticks = 0; ticks < uv_len; ticks++) {
		float xf = fclampf((160.0 * ticks) / (float)uv_len, 0.0, 159.0);
		int x0 = xf;
		int x1 = fclampf(x0 + 1, 0.0, 159.0);
		int evn0 = 3 * y * img->width + 6 * x0;
		int evn1 = 3 * y * img->width + 6 * x1;
		int odd0 = 3 * (y + 1) * img->width + 6 * x0;
		int odd1 = 3 * (y + 1) * img->width + 6 * x1;
		float R0 = (linear(img->pixel[evn0 + 0]) + linear(img->pixel[odd0 + 0]) + linear(img->pixel[evn0 + 3]) + linear(img->pixel[odd0 + 3])) / 4;
		float G0 = (linear(img->pixel[evn0 + 1]) + linear(img->pixel[odd0 + 1]) + linear(img->pixel[evn0 + 4]) + linear(img->pixel[odd0 + 4])) / 4;
		float B0 = (linear(img->pixel[evn0 + 2]) + linear(img->pixel[odd0 + 2]) + linear(img->pixel[evn0 + 5]) + linear(img->pixel[odd0 + 5])) / 4;
		float R1 = (linear(img->pixel[evn1 + 0]) + linear(img->pixel[odd1 + 0]) + linear(img->pixel[evn1 + 3]) + linear(img->pixel[odd1 + 3])) / 4;
		float G1 = (linear(img->pixel[evn1 + 1]) + linear(img->pixel[odd1 + 1]) + linear(img->pixel[evn1 + 4]) + linear(img->pixel[odd1 + 4])) / 4;
		float B1 = (linear(img->pixel[evn1 + 2]) + linear(img->pixel[odd1 + 2]) + linear(img->pixel[evn1 + 5]) + linear(img->pixel[odd1 + 5])) / 4;
		uint8_t R = srgb(flerpf(R0, R1, xf - (float)x0));
		uint8_t G = srgb(flerpf(G0, G1, xf - (float)x0));
		uint8_t B = srgb(flerpf(B0, B1, xf - (float)x0));
		add_freq(1500.0 + 800.0 * V_RGB(R, G, B) / 255.0);
	}
}
void u_scan(int y)
{
	for (int ticks = 0; ticks < uv_len; ticks++) {
		float xf = fclampf((160.0 * ticks) / (float)uv_len, 0.0, 159.0);
		int x0 = xf;
		int x1 = fclampf(x0 + 1, 0.0, 159.0);
		int evn0 = 3 * (y - 1) * img->width + 6 * x0;
		int evn1 = 3 * (y - 1) * img->width + 6 * x1;
		int odd0 = 3 * y * img->width + 6 * x0;
		int odd1 = 3 * y * img->width + 6 * x1;
		float R0 = (linear(img->pixel[evn0 + 0]) + linear(img->pixel[odd0 + 0]) + linear(img->pixel[evn0 + 3]) + linear(img->pixel[odd0 + 3])) / 4;
		float G0 = (linear(img->pixel[evn0 + 1]) + linear(img->pixel[odd0 + 1]) + linear(img->pixel[evn0 + 4]) + linear(img->pixel[odd0 + 4])) / 4;
		float B0 = (linear(img->pixel[evn0 + 2]) + linear(img->pixel[odd0 + 2]) + linear(img->pixel[evn0 + 5]) + linear(img->pixel[odd0 + 5])) / 4;
		float R1 = (linear(img->pixel[evn1 + 0]) + linear(img->pixel[odd1 + 0]) + linear(img->pixel[evn1 + 3]) + linear(img->pixel[odd1 + 3])) / 4;
		float G1 = (linear(img->pixel[evn1 + 1]) + linear(img->pixel[odd1 + 1]) + linear(img->pixel[evn1 + 4]) + linear(img->pixel[odd1 + 4])) / 4;
		float B1 = (linear(img->pixel[evn1 + 2]) + linear(img->pixel[odd1 + 2]) + linear(img->pixel[evn1 + 5]) + linear(img->pixel[odd1 + 5])) / 4;
		uint8_t R = srgb(flerpf(R0, R1, xf - (float)x0));
		uint8_t G = srgb(flerpf(G0, G1, xf - (float)x0));
		uint8_t B = srgb(flerpf(B0, B1, xf - (float)x0));
		add_freq(1500.0 + 800.0 * U_RGB(R, G, B) / 255.0);
	}
}

int main(int argc, char **argv)
{
	if (argc < 2) {
		fprintf(stderr, "usage: %s <input.ppm> <output.wav|default|hw:?,?> <rate>\n", argv[0]);
		return 1;
	}

	if (!open_img_read(&img, argv[1]))
		return 1;

	if (320 != img->width || 240 != img->height) {
		fprintf(stderr, "image not 320x240\n");
		close_img(img);
		return 1;
	}

	char *pcm_name = "default";
	if (argc > 2)
		pcm_name = argv[2];
	if (argc > 3)
		rate = atoi(argv[3]);
	if (!open_pcm_write(&pcm, pcm_name, rate, 1, 37.5))
		return 1;

	rate = rate_pcm(pcm);
	channels = channels_pcm(pcm);

	sync_porch_len = rate * sync_porch_sec;
	porch_len = rate * porch_sec;
	y_len = rate * y_sec;
	uv_len = rate * uv_sec;
	hor_sync_len = rate * hor_sync_sec;
	seperator_len = rate * seperator_sec;

//	fprintf(stderr, "%d %d %d %d %d %d\n", sync_porch_len, porch_len, y_len, uv_len, hor_sync_len, seperator_len);

	buff = (short *)malloc(sizeof(short)*channels);

	info_pcm(pcm);

	if (fabsf(porch_sec * rate - porch_len) > 0.0001)
		fprintf(stderr, "this rate will not give accurate (smooth) results.\ntry 40000Hz and resample to %dHz\n", rate);

	hz2rad = (2.0 * M_PI) / rate;
	nco = -I * 0.7;
	enum { N = 13 };
	float seq_freq[N] = { 1900.0, 1200.0, 1900.0, 1200.0, 1300.0, 1300.0, 1300.0, 1100.0, 1300.0, 1300.0, 1300.0, 1100.0, 1200.0 };
	float seq_time[N] = { 0.3, 0.01, 0.3, 0.03, 0.03, 0.03, 0.03, 0.03, 0.03, 0.03, 0.03, 0.03, 0.03 };


	for (int ticks = 0; ticks < (int)(0.3 * rate); ticks++)
		add_sample(0.0);

	for (int i = 0; i < N; i++)
		for (int ticks = 0; ticks < (int)(seq_time[i] * rate); ticks++)
			add_freq(seq_freq[i]);

	for (int y = 0; y < img->height; y++) {
		// EVEN LINES
		hor_sync();
		sync_porch();
		y_scan(y);
		even_seperator();
		porch();
		v_scan(y);
		// ODD LINES
		y++;
		hor_sync();
		sync_porch();
		y_scan(y);
		odd_seperator();
		porch();
		u_scan(y);
	}

	while (add_sample(0.0));

	close_pcm(pcm);
	close_img(img);
	return 0;
}

