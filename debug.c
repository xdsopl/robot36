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
#include "buffer.h"
#include "yuv.h"
#include "utils.h"
#include "img.h"

int main(int argc, char **argv)
{
	struct pcm *pcm;
	char *pcm_name = "default";
	char *img_name = 0;
	if (argc != 1)
		pcm_name = argv[1];
	if (argc == 3)
		img_name = argv[2];

	if (!open_pcm_read(&pcm, pcm_name)) {
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

	float complex cnt_last = -I;
	float complex dat_last = -I;

	float dat_avg = 1900.0;

	int begin_vis_ss = 0;
	int begin_vis_lo = 0;
	int begin_vis_hi = 0;
	int begin_hor_sync = 0;
	int begin_cal_break = 0;
	int begin_cal_leader = 0;
	int latch_sync = 0;

	int cal_ticks = 0;
	int got_cal_break = 0;
	int vis_mode = 0;
	int dat_mode = 0;
	int vis_ticks = 0;
	int vis_bit = -1;
	int vis_byte = 0;
	int sep_evn = 0;
	int sep_odd = 0;

	int y = 0;
	int odd = 0;
	int first_hor_sync = 0;

#if DN && UP
	// 320 / 0.088 = 160 / 0.044 = 40000 / 11 = 3636.(36)~ pixels per second for Y, U and V
	int64_t factor_L = 40000;
	int64_t factor_M = 11 * rate;
	int64_t factor_D = gcd(factor_L, factor_M);
	factor_L /= factor_D;
	factor_M /= factor_D;
#endif
#if DN && !UP
	int64_t factor_L = 1;
	// factor_M * step should be smaller than pixel length
	int64_t factor_M = rate * 0.088 / 320.0 / 2;
#endif
#if !DN
	int64_t factor_L = 1;
	int64_t factor_M = 1;
#endif

	// we want odd number of taps, 4 and 2 ms window length gives best results
	int cnt_taps = 1 | (int)(rate * factor_L * 0.004);
	int dat_taps = 1 | (int)(rate * factor_L * 0.002);
	fprintf(stderr, "using %d and %d tap filter\n", cnt_taps, dat_taps);
	float drate = rate * (float)factor_L / (float)factor_M;
	float dstep = 1.0 / drate;
	fprintf(stderr, "using factor of %ld/%ld, working at %.2fhz\n", factor_L, factor_M, drate);
	float complex *cnt_q = malloc(sizeof(float complex) * factor_L);
	float complex *dat_q = malloc(sizeof(float complex) * factor_L);
	// same factor to keep life simple and have accurate horizontal sync
	struct ddc *cnt_ddc = alloc_ddc(factor_L, factor_M, 1200.0, 200.0, rate, cnt_taps, kaiser, 2.0);
	struct ddc *dat_ddc = alloc_ddc(factor_L, factor_M, 1900.0, 800.0, rate, dat_taps, kaiser, 2.0);
	// delay input by phase shift of other filter to synchronize outputs
	int cnt_delay = (dat_taps - 1) / (2 * factor_L);
	int dat_delay = (cnt_taps - 1) / (2 * factor_L);

	// minimize delay
	if (cnt_delay > dat_delay) {
		cnt_delay -= dat_delay;
		dat_delay = 0;
	} else {
		dat_delay -= cnt_delay;
		cnt_delay = 0;
	}

	short *pcm_buff = (short *)malloc(sizeof(short) * channels * factor_M);

	// 0.1 second history + enough room for delay and taps
	int buff_len = 0.1 * rate + factor_M
		+ fmaxf(cnt_delay, dat_delay)
		+ fmaxf(cnt_taps, dat_taps) / factor_L;
	struct buffer *buffer = alloc_buffer(buff_len);

	const double vis_sec = 0.03l;
	const double hor_sync_sec = 0.009l;
	const double cal_break_sec = 0.01l;
	const double cal_leader_sec = 0.3l;
	const double seperator_sec = 0.0045l;
	const double sync_porch_sec = 0.003l;
	const double porch_sec = 0.0015l;
	const double y_sec = 0.088l;
	const double uv_sec = 0.044l;
	const double hor_sec = 0.15l;

	int vis_len = vis_sec * drate;
	int hor_sync_len = hor_sync_sec * drate;
	int cal_break_len = cal_break_sec * drate;
	int cal_leader_len = cal_leader_sec * drate;
	int seperator_len = seperator_sec * drate;
	int sync_porch_len = sync_porch_sec * drate;
	int porch_len = porch_sec * drate;
	int y_len = y_sec * drate;
	int uv_len = uv_sec * drate;
	int hor_len = hor_sec * drate;

	int missing_sync = 0;
	int seperator_correction = 0;

	const int width = hor_len + sync_porch_len + 24;
	const int height = 256;
	struct img *img = 0;

	int hor_ticks = 0;
	int y_width = y_len;
	int uv_width = uv_len;
	uint8_t *y_pixel = malloc(y_width * 2);
	memset(y_pixel, 0, y_width * 2);
	uint8_t *uv_pixel = malloc(uv_width * 2);
	memset(uv_pixel, 0, uv_width * 2);

	for (int out = factor_L;; out++, hor_ticks++, cal_ticks++, vis_ticks++) {
		if (out >= factor_L) {
			out = 0;
			if (!read_pcm(pcm, pcm_buff, factor_M))
				break;
			float *buff = 0;
			for (int j = 0; j < factor_M; j++)
				buff = do_buffer(buffer, (float)pcm_buff[j * channels] / 32767.0);

			do_ddc(cnt_ddc, buff + cnt_delay, cnt_q);
			do_ddc(dat_ddc, buff + dat_delay, dat_q);
		}

		float cnt_freq = fclampf(1200.0 + cargf(cnt_q[out] * conjf(cnt_last)) / (2.0 * M_PI * dstep), 1100.0, 1300.0);
		float dat_freq = fclampf(1900.0 + cargf(dat_q[out] * conjf(dat_last)) / (2.0 * M_PI * dstep), 1500.0, 2300.0);

		if (cabsf(cnt_q[out]) > cabsf(dat_q[out]))
			dat_freq = 1500.0;
		else
			cnt_freq = 1300.0;

		cnt_last = cnt_q[out];
		dat_last = dat_q[out];

		const float dat_a = 1.0 / (drate * 0.00238 + 1.0);
		dat_avg = dat_a * dat_freq + (1.0 - dat_a) * dat_avg;

		begin_vis_ss = fabsf(cnt_freq - 1200.0) < 50.0 ? begin_vis_ss + 1 : 0;
		begin_vis_lo = fabsf(cnt_freq - 1300.0) < 50.0 ? begin_vis_lo + 1 : 0;
		begin_vis_hi = fabsf(cnt_freq - 1100.0) < 50.0 ? begin_vis_hi + 1 : 0;
		begin_hor_sync = fabsf(cnt_freq - 1200.0) < 50.0 ? begin_hor_sync + 1 : 0;
		begin_cal_break = fabsf(cnt_freq - 1200.0) < 50.0 ? begin_cal_break + 1 : 0;
		begin_cal_leader = fabsf(dat_avg - 1900.0) < 50.0 ? begin_cal_leader + 1 : 0;

		// TODO: remove floats
		const float vis_tolerance = 0.9;
		const float sync_tolerance = 0.7;
		const float break_tolerance = 0.7;
		const float leader_tolerance = 0.3;

		int vis_ss = begin_vis_ss >= (int)(vis_tolerance * vis_len) ? 1 : 0;
		int vis_lo = begin_vis_lo >= (int)(vis_tolerance * vis_len) ? 1 : 0;
		int vis_hi = begin_vis_hi >= (int)(vis_tolerance * vis_len) ? 1 : 0;
		int cal_break = begin_cal_break >= (int)(break_tolerance * cal_break_len) ? 1 : 0;
		int cal_leader = begin_cal_leader >= (int)(leader_tolerance * cal_leader_len) ? 1 : 0;

		// we want a pulse at the falling edge
		latch_sync = begin_hor_sync > (int)(sync_tolerance * hor_sync_len) ? 1 : latch_sync;
		int hor_sync = (cnt_freq > 1299.0) && latch_sync;
		latch_sync = hor_sync ? 0 : latch_sync;

		// we only want a pulse for the bits
		begin_vis_ss = vis_ss ? 0 : begin_vis_ss;
		begin_vis_lo = vis_lo ? 0 : begin_vis_lo;
		begin_vis_hi = vis_hi ? 0 : begin_vis_hi;

		static int ticks = 0;
		if (ticks++ < 5.0 * drate)
			printf("%f %f %f %d %d %d %d %d %d %d %d\n", (float)ticks * dstep, dat_freq, cnt_freq,
				50*hor_sync+950, 50*cal_leader+850, 50*cal_break+750,
				50*vis_ss+650, 50*vis_lo+550, 50*vis_hi+450,
				50*sep_evn+350, 50*sep_odd+250);

		// only want to see a pulse
		sep_evn = 0;
		sep_odd = 0;

		if (cal_leader && !cal_break && got_cal_break &&
				(cal_ticks >= (cal_leader_len + cal_break_len) * leader_tolerance) &&
				(cal_ticks <= (cal_leader_len + cal_break_len) * (2.0 - leader_tolerance))) {
			vis_mode = 1;
			vis_bit = -1;
			dat_mode = 0;
			first_hor_sync = 1;
			got_cal_break = 0;
			fprintf(stderr, "%s got calibration header\n", string_time("%F %T"));
		}

		if (cal_break && !cal_leader &&
				cal_ticks >= (int)(cal_break_len * break_tolerance) &&
				cal_ticks <= (int)(cal_break_len * (2.0 - break_tolerance)))
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
			} else if (vis_ticks <= (int)(10.0 * vis_len * (2.0 - vis_tolerance))) {
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
			y = 0;
			odd = 0;
			if (img) {
				close_img(img);
				fprintf(stderr, "%d missing sync's and %d corrections from seperator\n", missing_sync, seperator_correction);
				missing_sync = 0;
				seperator_correction = 0;
			}
			if (img_name) {
				if (!open_img_write(&img, img_name, width, height))
					return 1;
			} else {
				if (!open_img_write(&img, string_time("%F_%T.ppm"), width, height))
					return 1;
			}
			continue;
		}

		if (hor_ticks < width) {
			uint8_t *p = img->pixel + 3 * y * width + 3 * hor_ticks;
			float dat_v = (dat_freq - 1500.0) / 800.0;
			float cnt_v = (1300.0 - cnt_freq) / 200.0;
			p[0] = fclampf(0.0, 255.0, 255.0 * dat_v);
			p[1] = fclampf(0.0, 255.0, 255.0 * (dat_v + cnt_v));
			p[2] = fclampf(0.0, 255.0, 255.0 * dat_v);
		}

		// if horizontal sync is too early, we reset to the beginning instead of ignoring
		if (hor_sync && hor_ticks < (hor_len - sync_porch_len)) {
			for (int i = 0; i < 4; i++) {
				uint8_t *p = img->pixel + 3 * y * width + 3 * (width - i - 10);
				p[0] = 255;
				p[1] = 0;
				p[2] = 255;
			}
			hor_ticks = 0;
		}

		// we always sync if sync pulse is where it should be.
		if (hor_sync && (hor_ticks >= (hor_len - sync_porch_len)) &&
				(hor_ticks < (hor_len + sync_porch_len))) {
			uint8_t *p = img->pixel + 3 * y * width + 3 * hor_ticks;
			p[0] = 255;
			p[1] = 0;
			p[2] = 0;
			y++;
			if (y == height) {
				close_img(img);
				fprintf(stderr, "%d missing sync's and %d corrections from seperator\n", missing_sync, seperator_correction);
				img = 0;
				dat_mode = 0;
				missing_sync = 0;
				seperator_correction = 0;
				continue;
			}
			odd ^= 1;
			hor_ticks = 0;
		}

		// if horizontal sync is missing, we extrapolate from last sync
		if (hor_ticks >= (hor_len + sync_porch_len)) {
			for (int i = 0; i < 4; i++) {
				uint8_t *p = img->pixel + 3 * y * width + 3 * (width - i - 5);
				p[0] = 255;
				p[1] = 255;
				p[2] = 0;
			}
			y++;
			if (y == height) {
				close_img(img);
				fprintf(stderr, "%d missing sync's and %d corrections from seperator\n", missing_sync, seperator_correction);
				img = 0;
				dat_mode = 0;
				missing_sync = 0;
				seperator_correction = 0;
				continue;
			}
			odd ^= 1;
			missing_sync++;
			hor_ticks -= hor_len;
			// we are not at the pixels yet, so no correction here
		}

		static int sep_count = 0;
		if ((hor_ticks > (sync_porch_len + y_len)) &&
				(hor_ticks < (sync_porch_len + y_len + seperator_len)))
			sep_count += dat_freq < 1900.0 ? 1 : -1;

		// we try to correct from odd / even seperator
		if (sep_count && (hor_ticks > (sync_porch_len + y_len + seperator_len))) {
			if (sep_count > 0) {
				sep_evn = 1;
				if (odd) {
					odd = 0;
					seperator_correction++;
					for (int i = 0; i < 4; i++) {
						uint8_t *p = img->pixel + 3 * y * width + 3 * (width - i - 15);
						p[0] = 255;
						p[1] = 0;
						p[2] = 0;
					}
				}
			} else {
				sep_odd = 1;
				if (!odd) {
					odd = 1;
					seperator_correction++;
					for (int i = 0; i < 4; i++) {
						uint8_t *p = img->pixel + 3 * y * width + 3 * (width - i - 15);
						p[0] = 0;
						p[1] = 255;
						p[2] = 0;
					}
				}
			}
			sep_count = 0;
		}
		if ((hor_ticks == sync_porch_len) ||
			(hor_ticks == (sync_porch_len + y_len)) ||
			(hor_ticks == (sync_porch_len + y_len + seperator_len)) ||
			(hor_ticks == (sync_porch_len + y_len + seperator_len + porch_len)) ||
			(hor_ticks == (sync_porch_len + y_len + seperator_len + porch_len + uv_len))) {
			uint8_t *p = img->pixel + 3 * y * width + 3 * hor_ticks;
			p[0] = 255;
			p[1] = 0;
			p[2] = 0;
		}
	}

	if (img) {
		close_img(img);
		fprintf(stderr, "%d missing sync's and %d corrections from seperator\n", missing_sync, seperator_correction);
		missing_sync = 0;
		seperator_correction = 0;
	}

	close_pcm(pcm);

	free_ddc(cnt_ddc);
	free_ddc(dat_ddc);
	free_buffer(buffer);
	free(pcm_buff);

	return 0;
}

