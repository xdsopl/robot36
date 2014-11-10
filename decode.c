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
#include <time.h>
#include "pcm.h"
#include "ddc.h"
#include "buffer.h"
#include "yuv.h"
#include "utils.h"
#include "img.h"

void process_line(uint8_t *pixel, uint8_t *y_pixel, uint8_t *uv_pixel, int y_width, int uv_width, int width, int height, int n)
{
	// we only process after 2 full lines: on odd lines
	if (n % 2)
	for (int y = n-1, l = 0; l < 2 && y < height; l++, y++) {
		for (int x = 0; x < width; x++) {
#if DN && UP
			uint8_t Y = y_pixel[x + l*y_width];
			uint8_t U = uv_pixel[x/2 + uv_width];
			uint8_t V = uv_pixel[x/2];
#else
			float y_xf = (float)x * (float)y_width / (float)width;
			float uv_xf = (float)x * (float)uv_width / (float)width;
			int y_x0 = y_xf;
			int uv_x0 = uv_xf;
			int y_x1 = fclampf(0, y_width, y_xf + 1);
			int uv_x1 = fclampf(0, uv_width, uv_xf + 1);
			uint8_t Y = srgb(flerpf(linear(y_pixel[y_x0 + l*y_width]), linear(y_pixel[y_x1 + l*y_width]), y_xf - (float)y_x0));
			uint8_t U = flerpf(uv_pixel[uv_x0 + uv_width], uv_pixel[uv_x1 + uv_width], uv_xf - (float)uv_x0);
			uint8_t V = flerpf(uv_pixel[uv_x0], uv_pixel[uv_x1], uv_xf - (float)uv_x0);
#endif
			uint8_t *p = pixel + 3 * width * y + 3 * x;
			p[0] = R_YUV(Y, U, V);
			p[1] = G_YUV(Y, U, V);
			p[2] = B_YUV(Y, U, V);
		}
	}
}

int vis_code(int *reset, int *code, float cnt_freq, float drate)
{
	const float tolerance = 0.9;
	const float length = 0.03;

	static int ss_ticks = 0;
	static int lo_ticks = 0;
	static int hi_ticks = 0;

	ss_ticks = fabsf(cnt_freq - 1200.0) < 50.0 ? ss_ticks + 1 : 0;
	lo_ticks = fabsf(cnt_freq - 1300.0) < 50.0 ? lo_ticks + 1 : 0;
	hi_ticks = fabsf(cnt_freq - 1100.0) < 50.0 ? hi_ticks + 1 : 0;

	int sig_ss = ss_ticks >= (int)(drate * tolerance * length) ? 1 : 0;
	int sig_lo = lo_ticks >= (int)(drate * tolerance * length) ? 1 : 0;
	int sig_hi = hi_ticks >= (int)(drate * tolerance * length) ? 1 : 0;

	// we only want a pulse for the bits
	ss_ticks = sig_ss ? 0 : ss_ticks;
	lo_ticks = sig_lo ? 0 : lo_ticks;
	hi_ticks = sig_hi ? 0 : hi_ticks;

	static int ticks = -1;
	ticks++;

	static int bit = -1;
	static int byte = 0;

	if (*reset) {
		bit = -1;
		*reset = 0;
	}

	if (bit < 0) {
		if (sig_ss) {
			ticks = 0;
			byte = 0;
			bit = 0;
		}
		return 0;
	}
	if (ticks <= (int)(drate * 10.0 * length * (2.0 - tolerance))) {
		if (sig_ss) {
			bit = -1;
			*code = byte;
			return 1;
		}
		if (bit < 8) {
			if (sig_lo) bit++;
			if (sig_hi) byte |= 1 << bit++;
		}
		return 0;
	}
	// stop bit is missing.
	if (bit >= 8) {
		bit = -1;
		*code = byte;
		return 1;
	}
	// something went wrong and we shouldnt be here. return what we got anyway.
	bit = -1;
	*code = byte;
	return 1;
}

int cal_header(float cnt_freq, float dat_freq, float drate)
{
	const float break_len = 0.01;
	const float leader_len = 0.3;
	const float break_tolerance = 0.7;
	const float leader_tolerance = 0.3;

	static float dat_avg = 1900.0;
	const float dat_a = 1.0 / (drate * 0.00238 + 1.0);
	dat_avg = dat_a * dat_freq + (1.0 - dat_a) * dat_avg;

	static int break_ticks = 0;
	static int leader_ticks = 0;

	break_ticks = fabsf(cnt_freq - 1200.0) < 50.0 ? break_ticks + 1 : 0;
	leader_ticks = fabsf(dat_avg - 1900.0) < 50.0 ? leader_ticks + 1 : 0;

	int sig_break = break_ticks >= (int)(drate * break_tolerance * break_len) ? 1 : 0;
	int sig_leader = leader_ticks >= (int)(drate * leader_tolerance * leader_len) ? 1 : 0;

	static int ticks = -1;
	ticks++;
	static int got_break = 0;

	if (sig_leader && !sig_break && got_break &&
			ticks >= (int)(drate * (leader_len + break_len) * leader_tolerance) &&
			ticks <= (int)(drate * (leader_len + break_len) * (2.0 - leader_tolerance))) {
		got_break = 0;
		return 1;
	}

	if (sig_break && !sig_leader &&
			ticks >= (int)(drate * break_len * break_tolerance) &&
			ticks <= (int)(drate * break_len * (2.0 - break_tolerance)))
		got_break = 1;

	if (sig_leader && !sig_break) {
		ticks = 0;
		got_break = 0;
	}
	return 0;
}

int decode(int *reset, struct img **img, char *img_name, float cnt_freq, float dat_freq, float drate)
{
	const int width = 320;
	const int height = 240;
	const double sync_porch_sec = 0.003l;
	const double porch_sec = 0.0015l;
	const double y_sec = 0.088l;
	const double uv_sec = 0.044l;
	const double hor_sec = 0.15l;
	const double hor_sync_sec = 0.009l;
	const double seperator_sec = 0.0045l;
	const float sync_tolerance = 0.7;

	static int sync_porch_len = 0;
	static int porch_len = 0;
	static int y_len = 0;
	static int uv_len = 0;
	static int hor_len = 0;
	static int hor_sync_len = 0;
	static int seperator_len = 0;

	static int hor_ticks = 0;
	static int latch_sync = 0;

	static int y_width = 0;
	static int uv_width = 0;
	static uint8_t *y_pixel = 0;
	static uint8_t *uv_pixel = 0;

	static int init = 0;
	if (!init) {
		sync_porch_len = sync_porch_sec * drate;
		porch_len = porch_sec * drate;
		y_len = y_sec * drate;
		uv_len = uv_sec * drate;
		hor_len = hor_sec * drate;
		hor_sync_len = hor_sync_sec * drate;
		seperator_len = seperator_sec * drate;

		y_width = y_len;
		uv_width = uv_len;
		y_pixel = malloc(y_width * 2);
		memset(y_pixel, 0, y_width * 2);
		uv_pixel = malloc(uv_width * 2);
		memset(uv_pixel, 0, uv_width * 2);
		init = 1;
	}

	hor_ticks = fabsf(cnt_freq - 1200.0) < 50.0 ? hor_ticks + 1 : 0;

	// we want a pulse at the falling edge
	latch_sync = hor_ticks > (int)(sync_tolerance * hor_sync_len) ? 1 : latch_sync;
	int hor_sync = (cnt_freq > 1299.0) && latch_sync;
	latch_sync = hor_sync ? 0 : latch_sync;

	// we wait until first sync
	if (*reset && !hor_sync)
		return 0;

	static int y = 0;
	static int odd = 0;
	static int y_pixel_x = 0;
	static int uv_pixel_x = 0;

	static int ticks = -1;
	ticks++;

	// data comes after first sync
	if (*reset && hor_sync) {
		*reset = 0;
		ticks = 0;
		y_pixel_x = 0;
		uv_pixel_x = 0;
		y = 0;
		odd = 0;
		if (*img)
			close_img(*img);
		if (img_name) {
			if (!open_img_write(img, img_name, width, height))
				exit(1);
		} else {
			if (!open_img_write(img, string_time("%F_%T.ppm"), width, height))
				exit(1);
		}
		return 0;
	}

	// if horizontal sync is too early, we reset to the beginning instead of ignoring
	if (hor_sync && (ticks < (hor_len - sync_porch_len))) {
		ticks = 0;
		y_pixel_x = 0;
		uv_pixel_x = 0;
	}

	// we always sync if sync pulse is where it should be.
	if (hor_sync && (ticks >= (hor_len - sync_porch_len)) &&
			(ticks < (hor_len + sync_porch_len))) {
		process_line((*img)->pixel, y_pixel, uv_pixel, y_width, uv_width, width, height, y++);
		if (y == height) {
			close_img(*img);
			*img = 0;
			return 1;
		}
		odd ^= 1;
		ticks = 0;
		y_pixel_x = 0;
		uv_pixel_x = 0;
	}

	// if horizontal sync is missing, we extrapolate from last sync
	if (ticks >= (hor_len + sync_porch_len)) {
		process_line((*img)->pixel, y_pixel, uv_pixel, y_width, uv_width, width, height, y++);
		if (y == height) {
			close_img(*img);
			*img = 0;
			return 1;
		}
		odd ^= 1;
		ticks -= hor_len;
		// we are not at the pixels yet, so no correction here
		y_pixel_x = 0;
		uv_pixel_x = 0;
	}

	static int sep_count = 0;
	if ((ticks > (sync_porch_len + y_len)) &&
			(ticks < (sync_porch_len + y_len + seperator_len)))
		sep_count += dat_freq < 1900.0 ? 1 : -1;
	// we try to correct from odd / even seperator
	if (sep_count && (ticks > (sync_porch_len + y_len + seperator_len))) {
		odd = sep_count < 0;
		sep_count = 0;
	}
	if ((y_pixel_x < y_width) && (ticks >= sync_porch_len))
		y_pixel[y_pixel_x++ + (y % 2) * y_width] = fclampf(255.0 * (dat_freq - 1500.0) / 800.0, 0.0, 255.0);

	if ((uv_pixel_x < uv_width) && (ticks >= (sync_porch_len + y_len + seperator_len + porch_len)))
		uv_pixel[uv_pixel_x++ + odd * uv_width] = fclampf(255.0 * (dat_freq - 1500.0) / 800.0, 0.0, 255.0);
	return 0;
}
int demodulate(struct pcm *pcm, float *cnt_freq, float *dat_freq, float *drate)
{
	static float rate;
	static int channels;
	static int64_t factor_L;
	static int64_t factor_M;
	static int out;
	static float dstep;
	static float complex cnt_last = -I;
	static float complex dat_last = -I;
	static float complex *cnt_q;
	static float complex *dat_q;
	static struct ddc *cnt_ddc;
	static struct ddc *dat_ddc;
	static struct buffer *buffer;
	static int cnt_delay;
	static int dat_delay;
	static short *pcm_buff;

	static int init = 0;
	if (!init) {
		init = 1;
		rate = rate_pcm(pcm);
		channels = channels_pcm(pcm);
#if DN && UP
		// 320 / 0.088 = 160 / 0.044 = 40000 / 11 = 3636.(36)~ pixels per second for Y, U and V
		factor_L = 40000;
		factor_M = 11 * rate;
		int64_t factor_D = gcd(factor_L, factor_M);
		factor_L /= factor_D;
		factor_M /= factor_D;
#endif
#if DN && !UP
		factor_L = 1;
		// factor_M * step should be smaller than pixel length
		factor_M = rate * 0.088 / 320.0 / 2;
#endif
#if !DN
		factor_L = 1;
		factor_M = 1;
#endif

		// we want odd number of taps, 4 and 2 ms window length gives best results
		int cnt_taps = 1 | (int)(rate * factor_L * 0.004);
		int dat_taps = 1 | (int)(rate * factor_L * 0.002);
		fprintf(stderr, "using %d and %d tap filter\n", cnt_taps, dat_taps);
		*drate = rate * (float)factor_L / (float)factor_M;
		dstep = 1.0 / *drate;
		fprintf(stderr, "using factor of %ld/%ld, working at %.2fhz\n", factor_L, factor_M, *drate);
		cnt_q = malloc(sizeof(float complex) * factor_L);
		dat_q = malloc(sizeof(float complex) * factor_L);
		// same factor to keep life simple and have accurate horizontal sync
		cnt_ddc = alloc_ddc(factor_L, factor_M, 1200.0, 200.0, rate, cnt_taps, kaiser, 2.0);
		dat_ddc = alloc_ddc(factor_L, factor_M, 1900.0, 800.0, rate, dat_taps, kaiser, 2.0);
		// delay input by phase shift of other filter to synchronize outputs
		cnt_delay = (dat_taps - 1) / (2 * factor_L);
		dat_delay = (cnt_taps - 1) / (2 * factor_L);

		// minimize delay
		if (cnt_delay > dat_delay) {
			cnt_delay -= dat_delay;
			dat_delay = 0;
		} else {
			dat_delay -= cnt_delay;
			cnt_delay = 0;
		}

		pcm_buff = (short *)malloc(sizeof(short) * channels * factor_M);

		// 0.1 second history + enough room for delay and taps
		int buff_len = 0.1 * rate + factor_M
			+ fmaxf(cnt_delay, dat_delay)
			+ fmaxf(cnt_taps, dat_taps) / factor_L;
		buffer = alloc_buffer(buff_len);

		// start immediately below
		out = factor_L;
	}

	if (out >= factor_L) {
		out = 0;
		if (!read_pcm(pcm, pcm_buff, factor_M)) {
			init = 0;
			free(pcm_buff);
			free_ddc(cnt_ddc);
			free_ddc(dat_ddc);
			free_buffer(buffer);
			return 0;
		}
		float *buff = 0;
		for (int j = 0; j < factor_M; j++)
			buff = do_buffer(buffer, (float)pcm_buff[j * channels] / 32767.0);

		do_ddc(cnt_ddc, buff + cnt_delay, cnt_q);
		do_ddc(dat_ddc, buff + dat_delay, dat_q);
	}

	*cnt_freq = fclampf(1200.0 + cargf(cnt_q[out] * conjf(cnt_last)) / (2.0 * M_PI * dstep), 1100.0, 1300.0);
	*dat_freq = fclampf(1900.0 + cargf(dat_q[out] * conjf(dat_last)) / (2.0 * M_PI * dstep), 1500.0, 2300.0);

	if (cabsf(cnt_q[out]) > cabsf(dat_q[out]))
		*dat_freq = 1500.0;
	else
		*cnt_freq = 1300.0;

	cnt_last = cnt_q[out];
	dat_last = dat_q[out];

	out++;
	return 1;
}

int main(int argc, char **argv)
{
	struct pcm *pcm;
	char *pcm_name = "default";
	char *img_name = 0;
	if (argc != 1)
		pcm_name = argv[1];
	if (argc == 3)
		img_name = argv[2];

	if (!open_pcm_read(&pcm, pcm_name))
		return 1;

	info_pcm(pcm);

	float rate = rate_pcm(pcm);
	if (rate * 0.088 < 320.0) {
		fprintf(stderr, "%.0fhz samplerate too low\n", rate);
		return 1;
	}

	int channels = channels_pcm(pcm);
	if (channels > 1)
		fprintf(stderr, "using first of %d channels\n", channels);

	int vis_mode = 0;
	int dat_mode = 0;
	int vis_reset = 0;
	int dat_reset = 0;

	struct img *img = 0;
	float cnt_freq = 0.0;
	float dat_freq = 0.0;
	float drate = 0.0;

	while (demodulate(pcm, &cnt_freq, &dat_freq, &drate)) {
		if (cal_header(cnt_freq, dat_freq, drate)) {
			vis_mode = 1;
			vis_reset = 1;
			dat_mode = 0;
			dat_reset = 1;
			fprintf(stderr, "%s got calibration header\n", string_time("%F %T"));
		}

		if (vis_mode) {
			int code = 0;
			if (!vis_code(&vis_reset, &code, cnt_freq, drate))
				continue;
			if (0x88 != code) {
				fprintf(stderr, "%s got unsupported VIS 0x%x, ignoring\n", string_time("%F %T"), code);
				vis_mode = 0;
				continue;
			}
			fprintf(stderr, "%s got VIS = 0x%x\n", string_time("%F %T"), code);
			dat_mode = 1;
			dat_reset = 1;
			vis_mode = 0;
		}

		if (dat_mode) {
			if (decode(&dat_reset, &img, img_name, cnt_freq, dat_freq, drate))
				dat_mode = 0;
		}
	}

	if (img)
		close_img(img);

	close_pcm(pcm);

	return 0;
}

