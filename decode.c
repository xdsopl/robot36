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
#include "mmap_file.h"
#include "pcm.h"
#include "ddc.h"
#include "delay.h"
#include "yuv.h"
#include "utils.h"

void process_line(uint8_t *pixel, uint8_t *y_pixel, uint8_t *uv_pixel, int y_width, int uv_width, int width, int height, int n)
{
	// we only process after 2 full lines: on odd lines
	if (n % 2)
	for (int y = n-1, l = 0; l < 2 && y < height; l++, y++) {
		for (int x = 0; x < width; x++) {
			uint8_t Y = y_pixel[x + l*y_width];
			uint8_t U = uv_pixel[x/2 + uv_width];
			uint8_t V = uv_pixel[x/2];
			uint8_t *p = pixel + 3 * width * y + 3 * x;
			p[0] = R_YUV(Y, U, V);
			p[1] = G_YUV(Y, U, V);
			p[2] = B_YUV(Y, U, V);
		}
	}
}

int main(int argc, char **argv)
{
	pcm_t *pcm;
	char *pcm_name = "default";
	char *ppm_name = 0;
	if (argc != 1)
		pcm_name = argv[1];
	if (argc == 3)
		ppm_name = argv[2];

	if (!open_pcm(&pcm, pcm_name)) {
		fprintf(stderr, "couldnt open %s\n", pcm_name);
		return 1;
	}

	info_pcm(pcm);

	float rate = rate_pcm(pcm);
	if (rate * 0.088 < 320.0) {
		fprintf(stderr, "%.0fhz samplerate too low\n", rate);
		return 1;
	}

	int channels = channels_pcm(pcm);
	if (channels > 1)
		fprintf(stderr, "using first of %d channels\n", channels);

	const float step = 1.0 / rate;
	float complex cnt_last = -I;
	float complex dat_last = -I;

	float cal_avg = 1900.0;

	int begin_vis_ss = 0;
	int begin_vis_lo = 0;
	int begin_vis_hi = 0;
	int begin_hor_sync = 0;
	int begin_cal_break = 0;
	int begin_cal_leader = 0;
	int begin_sep_evn = 0;
	int begin_sep_odd = 0;
	int latch_sync = 0;

	const float vis_len = 0.03;
	const float hor_sync_len = 0.009;
	const float cal_break_len = 0.01;
	const float cal_leader_len = 0.3;
	const float seperator_len = 0.0045;
	int cal_ticks = 0;
	int got_cal_break = 0;
	int vis_mode = 0;
	int dat_mode = 0;
	int vis_ticks = 0;
	int vis_bit = -1;
	int vis_byte = 0;

	int y = 0;
	int odd = 0;
	int odd_count = 0;
	int evn_count = 0;
	int first_hor_sync = 0;

	// 320 / 0.088 = 160 / 0.044 = 40000 / 11 = 3636.(36)~ pixels per second for Y, U and V
	int64_t factor_L = 40000;
	int64_t factor_M = 11 * rate;
	int64_t factor_D = gcd(factor_L, factor_M);
	factor_L /= factor_D;
	factor_M /= factor_D;

	// we want odd number of taps, 4 and 2 ms window length gives best results
	int cnt_taps = 1 | (int)(rate * factor_L * 0.004);
	int dat_taps = 1 | (int)(rate * factor_L * 0.002);
	fprintf(stderr, "using %d and %d tap filter\n", cnt_taps, dat_taps);
	float drate = rate * (float)factor_L / (float)factor_M;
	float dstep = 1.0 / drate;
	fprintf(stderr, "using factor of %ld/%ld, working at %.2fhz\n", factor_L, factor_M, drate);
	float *cnt_amp = malloc(sizeof(float) * factor_M);
	float *dat_amp = malloc(sizeof(float) * factor_M);
	float complex *cnt_q = malloc(sizeof(float complex) * factor_L);
	float complex *dat_q = malloc(sizeof(float complex) * factor_L);
	// same factor to keep life simple and have accurate horizontal sync
	ddc_t *cnt_ddc = alloc_ddc(1200.0, 200.0, step, cnt_taps, factor_L, factor_M, kaiser, 2.0);
	ddc_t *dat_ddc = alloc_ddc(1900.0, 800.0, step, dat_taps, factor_L, factor_M, kaiser, 2.0);
	// delay input by phase shift of other filter to synchronize outputs
	delay_t *cnt_delay = alloc_delay((dat_taps - 1) / (2 * factor_L));
	delay_t *dat_delay = alloc_delay((cnt_taps - 1) / (2 * factor_L));

	short *buff = (short *)malloc(sizeof(short) * channels * factor_M);

	const float sync_porch_len = 0.003;
	const float porch_len = 0.0015; (void)porch_len;
	const float y_len = 0.088;
	const float uv_len = 0.044;
	const float hor_len = 0.15;
	int missing_sync = 0;
	int seperator_correction = 0;

	const int width = 320;
	const int height = 240;

	char ppm_head[32];
	snprintf(ppm_head, 32, "P6 %d %d 255\n", width, height);
	size_t ppm_size = strlen(ppm_head) + width * height * 3;
	void *ppm_p = 0;
	uint8_t *pixel = 0;

	int hor_ticks = 0;
	int y_pixel_x = 0;
	int uv_pixel_x = 0;
	int y_width = drate * y_len;
	int uv_width = drate * uv_len;
	uint8_t *y_pixel = malloc(y_width * 2);
	memset(y_pixel, 0, y_width * 2);
	uint8_t *uv_pixel = malloc(uv_width * 2);
	memset(uv_pixel, 0, uv_width * 2);

	for (int out = factor_L;; out++, hor_ticks++, cal_ticks++, vis_ticks++) {
		if (out >= factor_L) {
			out = 0;
			if (!read_pcm(pcm, buff, factor_M))
				break;
			for (int j = 0; j < factor_M; j++) {
				float amp = (float)buff[j * channels] / 32767.0;
				cnt_amp[j] = do_delay(cnt_delay, amp);
				dat_amp[j] = do_delay(dat_delay, amp);
			}
			do_ddc(cnt_ddc, cnt_amp, cnt_q);
			do_ddc(dat_ddc, dat_amp, dat_q);
		}

		float cnt_freq = fclampf(1200.0 + cargf(cnt_q[out] * conjf(cnt_last)) / (2.0 * M_PI * dstep), 1100.0, 1300.0);
		float dat_freq = fclampf(1900.0 + cargf(dat_q[out] * conjf(dat_last)) / (2.0 * M_PI * dstep), 1500.0, 2300.0);

		cnt_last = cnt_q[out];
		dat_last = dat_q[out];

		const float cal_a = 0.05;
		cal_avg = cal_a * dat_freq + (1.0 - cal_a) * cal_avg;

		begin_vis_ss = fabsf(cnt_freq - 1200.0) < 50.0 ? begin_vis_ss + 1 : 0;
		begin_vis_lo = fabsf(cnt_freq - 1300.0) < 50.0 ? begin_vis_lo + 1 : 0;
		begin_vis_hi = fabsf(cnt_freq - 1100.0) < 50.0 ? begin_vis_hi + 1 : 0;
		begin_hor_sync = fabsf(cnt_freq - 1200.0) < 50.0 ? begin_hor_sync + 1 : 0;
		begin_cal_break = fabsf(cnt_freq - 1200.0) < 50.0 ? begin_cal_break + 1 : 0;
		begin_cal_leader = fabsf(cal_avg - 1900.0) < 50.0 ? begin_cal_leader + 1 : 0;
		begin_sep_evn = fabsf(dat_freq - 1500.0) < 50.0 ? begin_sep_evn + 1 : 0;
		begin_sep_odd = fabsf(dat_freq - 2300.0) < 350.0 ? begin_sep_odd + 1 : 0;

		const float vis_tolerance = 0.9;
		const float sync_tolerance = 0.7;
		const float break_tolerance = 0.7;
		const float leader_tolerance = 0.3;
		const float seperator_tolerance = 0.7;

		int vis_ss = begin_vis_ss >= (int)(drate * vis_tolerance * vis_len) ? 1 : 0;
		int vis_lo = begin_vis_lo >= (int)(drate * vis_tolerance * vis_len) ? 1 : 0;
		int vis_hi = begin_vis_hi >= (int)(drate * vis_tolerance * vis_len) ? 1 : 0;
		int cal_break = begin_cal_break >= (int)(drate * break_tolerance * cal_break_len) ? 1 : 0;
		int cal_leader = begin_cal_leader >= (int)(drate * leader_tolerance * cal_leader_len) ? 1 : 0;
		int sep_evn = begin_sep_evn >= (int)(drate * seperator_tolerance * seperator_len) ? 1 : 0;
		int sep_odd = begin_sep_odd >= (int)(drate * seperator_tolerance * seperator_len) ? 1 : 0;

		// we want a pulse at the falling edge
		latch_sync = begin_hor_sync > (int)(drate * sync_tolerance * hor_sync_len) ? 1 : latch_sync;
		int hor_sync = begin_hor_sync > (int)(drate * sync_tolerance * hor_sync_len) ? 0 : latch_sync;
		latch_sync = hor_sync ? 0 : latch_sync;

		// we only want a pulse for the bits
		begin_vis_ss = vis_ss ? 0 : begin_vis_ss;
		begin_vis_lo = vis_lo ? 0 : begin_vis_lo;
		begin_vis_hi = vis_hi ? 0 : begin_vis_hi;

		if (cal_leader && !cal_break && got_cal_break &&
				cal_ticks >= (int)(drate * (cal_leader_len + cal_break_len) * leader_tolerance) &&
				cal_ticks <= (int)(drate * (cal_leader_len + cal_break_len) * (2.0 - leader_tolerance))) {
			vis_mode = 1;
			vis_bit = -1;
			dat_mode = 0;
			first_hor_sync = 1;
			got_cal_break = 0;
			fprintf(stderr, "%s got calibration header\n", string_time("%F %T"));
		}

		if (cal_break && !cal_leader &&
				cal_ticks >= (int)(drate * cal_break_len * break_tolerance) &&
				cal_ticks <= (int)(drate * cal_break_len * (2.0 - break_tolerance)))
			got_cal_break = 1;

		if (cal_leader && !cal_break) {
			cal_ticks = 0;
			got_cal_break = 0;
		}

		if (vis_mode) {
			if (vis_bit < 0) {
				if (vis_ss) {
					vis_ticks = 0;
					vis_byte = 0;
					vis_bit = 0;
					dat_mode = 0;
				}
			} else if (vis_ticks <= (int)(drate * 10.0 * vis_len * (2.0 - vis_tolerance))) {
				if (vis_ss) {
					dat_mode = 1;
					vis_mode = 0;
					vis_bit = -1;
					fprintf(stderr, "%s got VIS = 0x%x (complete)\n", string_time("%F %T"), vis_byte);
				}
				if (vis_bit < 8) {
					if (vis_lo) vis_bit++;
					if (vis_hi) vis_byte |= 1 << vis_bit++;
				}
			} else {
				if (vis_bit >= 8) {
					dat_mode = 1;
					vis_mode = 0;
					vis_bit = -1;
					fprintf(stderr, "%s got VIS = 0x%x (missing stop bit)\n", string_time("%F %T"), vis_byte);
				}
			}
			if (!vis_mode && vis_byte != 0x88) {
				fprintf(stderr, "unsupported mode 0x%x, ignoring\n", vis_byte);
				dat_mode = 0;
			}
			continue;
		}
		if (!dat_mode)
			continue;

		// we wait until first sync
		if (first_hor_sync && !hor_sync)
			continue;

		// data comes after first sync
		if (first_hor_sync && hor_sync) {
			first_hor_sync = 0;
			hor_ticks = 0;
			y_pixel_x = 0;
			uv_pixel_x = 0;
			y = 0;
			odd = 0;
			if (pixel) {
				munmap_file(ppm_p, ppm_size);
				fprintf(stderr, "%d missing sync's and %d corrections from seperator\n", missing_sync, seperator_correction);
				missing_sync = 0;
				seperator_correction = 0;
			}
			if (ppm_name)
				mmap_file_rw(&ppm_p, ppm_name, ppm_size);
			else
				mmap_file_rw(&ppm_p, string_time("%F_%T.ppm"), ppm_size);
			memcpy(ppm_p, ppm_head, strlen(ppm_head));
			pixel = (uint8_t *)ppm_p + strlen(ppm_head);
			memset(pixel, 0, width * height * 3);
			continue;
		}

		// if horizontal sync is too early, we reset to the beginning instead of ignoring
		if (hor_sync && hor_ticks < (int)((hor_len - sync_porch_len) * drate)) {
			for (int i = 0; i < 4; i++) {
				uint8_t *p = pixel + 3 * y * width + 3 * (width - i - 10);
				p[0] = 255;
				p[1] = 0;
				p[2] = 255;
			}
			hor_ticks = 0;
			y_pixel_x = 0;
			uv_pixel_x = 0;
		}

		// we always sync if sync pulse is where it should be.
		if (hor_sync && (hor_ticks >= (int)((hor_len - sync_porch_len) * drate) &&
				hor_ticks < (int)((hor_len + sync_porch_len) * drate))) {
			process_line(pixel, y_pixel, uv_pixel, y_width, uv_width, width, height, y++);
			if (y == height) {
				munmap_file(ppm_p, ppm_size);
				fprintf(stderr, "%d missing sync's and %d corrections from seperator\n", missing_sync, seperator_correction);
				pixel = 0;
				dat_mode = 0;
				missing_sync = 0;
				seperator_correction = 0;
				continue;
			}
			odd ^= 1;
			hor_ticks = 0;
			y_pixel_x = 0;
			uv_pixel_x = 0;
		}

		// if horizontal sync is missing, we extrapolate from last sync
		if (hor_ticks >= (int)((hor_len + sync_porch_len) * drate)) {
			process_line(pixel, y_pixel, uv_pixel, y_width, uv_width, width, height, y++);
			if (y == height) {
				munmap_file(ppm_p, ppm_size);
				fprintf(stderr, "%d missing sync's and %d corrections from seperator\n", missing_sync, seperator_correction);
				pixel = 0;
				dat_mode = 0;
				missing_sync = 0;
				seperator_correction = 0;
				continue;
			}
			odd ^= 1;
			missing_sync++;
			hor_ticks -= (int)(hor_len * drate);
			// we are not at the pixels yet, so no correction here
			y_pixel_x = 0;
			uv_pixel_x = 0;
		}

		if (hor_ticks > (int)((sync_porch_len + y_len) * drate) && hor_ticks < (int)((sync_porch_len + y_len + seperator_len) * drate)) {
			odd_count += sep_odd;
			evn_count += sep_evn;
		}
		// we try to correct from odd / even seperator
		if (evn_count != odd_count && hor_ticks > (int)((sync_porch_len + y_len + seperator_len) * drate)) {
			// even seperator
			if (evn_count > odd_count && odd) {
				odd = 0;
				seperator_correction++;
			}
			// odd seperator
			if (odd_count > evn_count && !odd) {
				odd = 1;
				seperator_correction++;
			}
			evn_count = 0;
			odd_count = 0;
		}
		// TODO: need better way to compensate for pulse decay time
		float fixme = 0.0007;
		if (y_pixel_x < y_width && hor_ticks >= (int)((fixme + sync_porch_len) * drate))
			y_pixel[y_pixel_x++ + (y % 2) * y_width] = fclampf(255.0 * (dat_freq - 1500.0) / 800.0, 0.0, 255.0);

		if (uv_pixel_x < uv_width && hor_ticks >= (int)((fixme + sync_porch_len + y_len + seperator_len + porch_len) * drate))
			uv_pixel[uv_pixel_x++ + odd * uv_width] = fclampf(255.0 * (dat_freq - 1500.0) / 800.0, 0.0, 255.0);
	}

	if (pixel) {
		munmap_file(ppm_p, ppm_size);
		fprintf(stderr, "%d missing sync's and %d corrections from seperator\n", missing_sync, seperator_correction);
		missing_sync = 0;
		seperator_correction = 0;
	}

	close_pcm(pcm);

	free_ddc(cnt_ddc);
	free_ddc(dat_ddc);
	free(cnt_amp);
	free(dat_amp);

	return 0;
}

