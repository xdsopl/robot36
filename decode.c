
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#include <math.h>
#include <complex.h> 

float lerp(float a, float b, float x)
{
	return a - a * x + b * x;
}

int64_t gcd(int64_t a, int64_t b)
{
	int64_t c;
	while ((c = a % b)) {
		a = b;
		b = c;
	}
	return b;
}

float limit(float min, float max, float x)
{
	float tmp = x < min ? min : x;
	return tmp > max ? max : tmp;
}

float sinc(float x)
{
	return 0 == x ? 1.0 : sinf(M_PI * x) / (M_PI * x);
}
float hann(float n, float N)
{
	return 0.5 * (1.0 - cosf(2.0 * M_PI * n / (N - 1.0)));
}
float hamming(float n, float N)
{
	return 0.54 - 0.46 * cosf(2.0 * M_PI * n / (N - 1.0));
}
float lanczos(float n, float N)
{
	return sinc(2.0 * n / (N - 1.0) - 1.0);
}
float gauss(float n, float N)
{
	float o = 0.35;
	return expf(- 1.0/2.0 * powf((n - (N - 1.0) / 2.0) / (o * (N - 1.0) / 2.0), 2.0));
}

float i0f(float x)
{
	// converges for -3*M_PI:3*M_PI in less than 20 iterations
	float sum = 1.0, val = 1.0, c = 0.0;
	for (int n = 1; n < 20; n++) {
		float tmp = x / (2 * n);
		val *= tmp * tmp;
		float y = val - c;
		float t = sum + y;
		c = (t - sum) - y;
		sum = t;
	}
	return sum;
}
float kaiser(float n, float N)
{
	float a = 2.0;
	return i0f(M_PI * a * sqrtf(1.0 - powf((2.0 * n) / (N - 1.0) - 1.0, 2.0))) / i0f(M_PI * a);
}

typedef struct {
	float complex *b;
	float *s;
	float complex osc;
	float complex d;
	int offset;
	int skip;
	int last;
	int taps;
	int samples;
	int L;
	int M;
} ddc_t;

void do_ddc(ddc_t *ddc, float *input, float complex *output)
{
	int in = 0;
	ddc->s[ddc->last] = input[in++];
	ddc->last = (ddc->last + 1) < ddc->samples ? ddc->last + 1 : 0;
	ddc->skip += ddc->L;
	// this works only for L <= M
	for (int k = 0; k < ddc->L; k++) {
		float complex sum = 0.0;
		for (int i = ddc->offset, j = ddc->last; i < ddc->taps; i += ddc->L) {
			sum += ddc->b[i] * ddc->s[j];
			j += j ? - 1 : ddc->samples - 1;
		}

		ddc->offset = (ddc->offset + ddc->M) % ddc->L;

		while (ddc->skip < ddc->M) {
			ddc->s[ddc->last] = input[in++];
			ddc->last = (ddc->last + 1) < ddc->samples ? ddc->last + 1 : 0;
			ddc->skip += ddc->L;
		}

		ddc->skip %= ddc->M;
		output[k] = ddc->osc * sum;
		ddc->osc *= ddc->d;
//		ddc->osc /= cabsf(ddc->osc); // not really needed
	}
}
ddc_t *alloc_ddc(float freq, float bw, float step, int taps, int L, int M, float (*window)(float, float))
{
	float lstep = step / (float)L;
	float ostep = step * (float)M / (float)L;
	ddc_t *ddc = malloc(sizeof(ddc_t));
	ddc->taps = taps;
	ddc->samples = (taps + L - 1) / L;
	ddc->b = malloc(sizeof(float complex) * ddc->taps);
	ddc->s = malloc(sizeof(float) * ddc->samples);
	ddc->osc = I;
	ddc->d = cexpf(-I * 2.0 * M_PI * freq * ostep);
	ddc->offset = 0;
	ddc->last = 0;
	ddc->skip = 0;
	ddc->L = L;
	ddc->M = M;
	for (int i = 0; i < ddc->samples; i++)
		ddc->s[i] = 0.0;
	float sum = 0.0;
	for (int i = 0; i < ddc->taps; i++) {
		float N = (float)ddc->taps;
		float n = (float)i;
		float x = n - (N - 1.0) / 2.0;
		float l = 2.0 * M_PI * bw * lstep;
		float w = window(n, ddc->taps);
		float h = 0.0 == x ? l / M_PI : sinf(l * x) / (x * M_PI);
		float b = w * h;
		sum += b;
		complex float o = cexpf(I * 2.0 * M_PI * freq * lstep * n);
		ddc->b[i] = b * o * (float)L;
	}
	for (int i = 0; i < ddc->taps; i++)
		ddc->b[i] /= sum;
	return ddc;
}
void free_ddc(ddc_t *ddc)
{
	free(ddc->b);
	free(ddc->s);
	free(ddc);
}

typedef struct {
	float *s;
	int last;
	int len;
} delay_t;

float do_delay(delay_t *d, float input)
{
	d->s[d->last] = input;
	d->last = (d->last + 1) < d->len ? d->last + 1 : 0;
	return d->s[d->last];
}

delay_t *alloc_delay(int samples)
{
	int len = samples + 1;
	delay_t *d = malloc(sizeof(delay_t));
	d->s = malloc(sizeof(float) * len);
	d->last = 0;
	d->len = len;
	for (int i = 0; i < len; i++)
		d->s[i] = 0.0;
	return d;
}
void free_delay(delay_t *delay)
{
	free(delay->s);
	free(delay);
}

void *mmap_file_ro(char *name, size_t *size)
{
	*size = 0;
	int fd = open(name, O_RDONLY);
	if (fd == -1) {
		perror("open");
		return 0;
	}

	struct stat sb;
	if (fstat(fd, &sb) == -1) {
		perror("fstat");
		return 0;
	}

	if (!S_ISREG(sb.st_mode)) {
		fprintf(stderr, "%s not a file\n", name);
		return 0;
	}

	void *p = mmap(0, sb.st_size, PROT_READ, MAP_SHARED, fd, 0);
	if (p == MAP_FAILED) {
		perror("mmap");
		return 0; 
	}

	if (close(fd) == -1) {
		perror ("close");
		return 0; 
	}
	*size = sb.st_size;
	return p;
}

void *mmap_file_rw(char *name, size_t size)
{
	int fd = open(name, O_RDWR|O_CREAT|O_TRUNC, S_IRUSR|S_IWUSR|S_IRGRP|S_IROTH);
	if (fd == -1) {
		perror("open");
		return 0;
	}

	struct stat sb;
	if (fstat(fd, &sb) == -1) {
		perror("fstat");
		return 0;
	}

	if (!S_ISREG(sb.st_mode)) {
		fprintf(stderr, "%s not a file\n", name);
		return 0;
	}

	if (lseek(fd, size - 1, SEEK_SET) == -1) {
		perror("lseek");
		return 0;
	}

	if (write(fd, "", 1) != 1) {
		perror("write");
		return 0;
	}

	void *p = mmap(0, size, PROT_READ|PROT_WRITE, MAP_SHARED, fd, 0);
	if (p == MAP_FAILED) {
		perror("mmap");
		return 0; 
	}

	if (close(fd) == -1) {
		perror ("close");
		return 0; 
	}
	return p;
}
int munmap_file(void *p, size_t size)
{
	if (munmap(p, size) == -1) {
		perror("munmap");
		return 0;
	}
	return 1;
}

typedef struct {
	uint32_t ChunkID;
	uint32_t ChunkSize;
	uint32_t Format;
	uint32_t Subchunk1ID;
	uint32_t Subchunk1Size;
	uint16_t AudioFormat;
	uint16_t NumChannels;
	uint32_t SampleRate;
	uint32_t ByteRate;
	uint16_t BlockAlign;
	uint16_t BitsPerSample;
	uint32_t Subchunk2ID;
	uint32_t Subchunk2Size;
} wav_t;

uint8_t R_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	(void)U;
	return limit(0.0, 255.0, 0.003906 * ((298.082 * (Y - 16.0)) + (408.583 * (V - 128))));
}
uint8_t G_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	return limit(0.0, 255.0, 0.003906 * ((298.082 * (Y - 16.0)) + (-100.291 * (U - 128)) + (-208.12 * (V - 128))));
}
uint8_t B_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	(void)V;
	return limit(0.0, 255.0, 0.003906 * ((298.082 * (Y - 16.0)) + (516.411 * (U - 128))));
}

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
			int y_x1 = limit(0, y_width, y_xf + 1);
			int uv_x1 = limit(0, uv_width, uv_xf + 1);
			uint8_t Y = lerp(y_pixel[y_x0 + l*y_width], y_pixel[y_x1 + l*y_width], y_xf - (float)y_x0);
			uint8_t U = lerp(uv_pixel[uv_x0 + uv_width], uv_pixel[uv_x1 + uv_width], uv_xf - (float)uv_x0);
			uint8_t V = lerp(uv_pixel[uv_x0], uv_pixel[uv_x1], uv_xf - (float)uv_x0);
#endif
			uint8_t *p = pixel + 3 * width * y + 3 * x;
			p[0] = R_YUV(Y, U, V);
			p[1] = G_YUV(Y, U, V);
			p[2] = B_YUV(Y, U, V);
		}
	}
}

int main(int argc, char **argv)
{
	if (argc != 3) {
		fprintf(stderr, "usage: %s <input.wav> <output.ppm>\n", argv[0]);
		return 1;
	}

	size_t wav_size;
	char *wav_p = mmap_file_ro(argv[1], &wav_size);
	if (!wav_p)
		return 1;

	wav_t *wav = (wav_t *)wav_p;

#if 0
	fprintf(stderr, "\n");
	fprintf(stderr, "ChunkID = 0x%x\n", wav->ChunkID);
	fprintf(stderr, "ChunkSize = %d\n", wav->ChunkSize);
	fprintf(stderr, "Format = 0x%x\n", wav->Format);
	fprintf(stderr, "Subchunk1ID = 0x%x\n", wav->Subchunk1ID);
	fprintf(stderr, "Subchunk1Size = %d\n", wav->Subchunk1Size);
	fprintf(stderr, "AudioFormat = %d\n", wav->AudioFormat);
	fprintf(stderr, "NumChannels = %d\n", wav->NumChannels);
	fprintf(stderr, "SampleRate = %d\n", wav->SampleRate);
	fprintf(stderr, "ByteRate = %d\n", wav->ByteRate);
	fprintf(stderr, "BlockAlign = %d\n", wav->BlockAlign);
	fprintf(stderr, "BitsPerSample = %d\n", wav->BitsPerSample);
	fprintf(stderr, "Subchunk2ID = 0x%x\n", wav->Subchunk2ID);
	fprintf(stderr, "Subchunk2Size = %d\n", wav->Subchunk2Size);
	fprintf(stderr, "\n");
#endif

	if (wav->ChunkID != 0x46464952 || wav->Format != 0x45564157 ||
			wav->Subchunk1ID != 0x20746d66 || wav->Subchunk1Size != 16 ||
			wav->AudioFormat != 1 || wav->Subchunk2ID != 0x61746164) {
		fprintf(stderr, "unsupported WAV file!\n");
		return 1;
	}
	if (wav->BitsPerSample != 16) {
		fprintf(stderr, "only 16bit WAV supported!\n");
		return 1;
	}

	if (wav->NumChannels != 1) {
		fprintf(stderr, "only Mono WAV supported!\n");
		return 1;
	}

	int samples = wav->Subchunk2Size / 2;

	float rate = wav->SampleRate;
	if (rate * 0.088 < 320.0) {
		fprintf(stderr, "%.0fhz samplerate too low\n", rate);
		return 1;
	}
	fprintf(stderr, "%.0fhz samplerate\n", rate);

	short *b = (short *)(wav_p + sizeof(wav_t));

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
	int64_t factor_M = rate * 0.088 / 320.0;
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
	float *cnt_amp = malloc(sizeof(float) * factor_M);
	float *dat_amp = malloc(sizeof(float) * factor_M);
	float complex *cnt_q = malloc(sizeof(float complex) * factor_L);
	float complex *dat_q = malloc(sizeof(float complex) * factor_L);
	// same factor to keep life simple and have accurate horizontal sync
	ddc_t *cnt_ddc = alloc_ddc(1200.0, 200.0, step, cnt_taps, factor_L, factor_M, kaiser);
	ddc_t *dat_ddc = alloc_ddc(1900.0, 800.0, step, dat_taps, factor_L, factor_M, kaiser);
	// delay input by phase shift of other filter to synchronize outputs
	delay_t *cnt_delay = alloc_delay((dat_taps - 1) / (2 * factor_L));
	delay_t *dat_delay = alloc_delay((cnt_taps - 1) / (2 * factor_L));

	const float sync_porch_len = 0.003;
	const float porch_len = 0.0015; (void)porch_len;
	const float y_len = 0.088;
	const float uv_len = 0.044;
	const float hor_len = 0.15;
	int missing_sync = 0;
	int seperator_correction = 0;

#if DEBUG
	const int width = (0.150 + 3.0 * sync_porch_len) * drate + 20;
	const int height = 256;
#else
	const int width = 320;
	const int height = 240;
#endif
	char ppm_head[32];
	snprintf(ppm_head, 32, "P6 %d %d 255\n", width, height);
	size_t ppm_size = strlen(ppm_head) + width * height * 3;
	char *ppm_p = mmap_file_rw(argv[2], ppm_size);
	memcpy(ppm_p, ppm_head, strlen(ppm_head));
	uint8_t *pixel = (uint8_t *)ppm_p + strlen(ppm_head);
	memset(pixel, 0, width * height * 3);

	int hor_ticks = 0;
	int y_pixel_x = 0;
	int uv_pixel_x = 0;
	int y_width = drate * y_len;
	int uv_width = drate * uv_len;
	uint8_t *y_pixel = malloc(y_width * 2);
	memset(y_pixel, 0, y_width * 2);
	uint8_t *uv_pixel = malloc(uv_width * 2);
	memset(uv_pixel, 0, uv_width * 2);

	for (int ticks = 0, in = 0, out = factor_L; in < (samples + 1 - factor_M); out++, ticks++, hor_ticks++, cal_ticks++, vis_ticks++) {
		if (out >= factor_L) {
			out = 0;
			for (int j = 0; j < factor_M; j++) {
				float amp = (float)b[in++] / 32767.0;
				cnt_amp[j] = do_delay(cnt_delay, amp);
				dat_amp[j] = do_delay(dat_delay, amp);
			}
			do_ddc(cnt_ddc, cnt_amp, cnt_q);
			do_ddc(dat_ddc, dat_amp, dat_q);
		}

		float cnt_freq = limit(1100.0, 1300.0, 1200.0 + cargf(cnt_q[out] * conjf(cnt_last)) / (2.0 * M_PI * dstep));
		float dat_freq = limit(1500.0, 2300.0, 1900.0 + cargf(dat_q[out] * conjf(dat_last)) / (2.0 * M_PI * dstep));

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

#if DEBUG
		if ((int)(ticks * dstep) < 5.0)
			printf("%f %f %f %d %d %d %d %d %d %d %d\n", (float)ticks * dstep, dat_freq, cnt_freq,
				50*hor_sync+950, 50*cal_leader+850, 50*cal_break+750,
				50*vis_ss+650, 50*vis_lo+550, 50*vis_hi+450,
				50*sep_evn+350, 50*sep_odd+250);
#endif

		if (cal_leader && !cal_break && got_cal_break &&
				cal_ticks >= (int)(drate * (cal_leader_len + cal_break_len) * leader_tolerance) &&
				cal_ticks <= (int)(drate * (cal_leader_len + cal_break_len) * (2.0 - leader_tolerance))) {
			vis_mode = 1;
			vis_bit = -1;
			dat_mode = 0;
			first_hor_sync = 1;
			got_cal_break = 0;
			fprintf(stderr, "%f got calibration header\n", (float)ticks * dstep);
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
					fprintf(stderr, "%f got VIS = 0x%x (complete)\n", (float)ticks*dstep, vis_byte);
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
					fprintf(stderr, "%f got VIS = 0x%x (missing stop bit)\n", (float)ticks*dstep, vis_byte);
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
			continue;
		}

#if DEBUG
		if (hor_ticks < width) {
			uint8_t *p = pixel + 3 * y * width + 3 * hor_ticks;
#if DATA
			uint8_t v = limit(0.0, 255.0, 255.0 * (dat_freq - 1500.0) / 800.0);
#else
			uint8_t v = limit(0.0, 255.0, 255.0 * (cnt_freq - 1100.0) / 200.0);
#endif
			p[0] = v;
			p[1] = v;
			p[2] = v;
		}
#endif
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
#if DEBUG
			uint8_t *p = pixel + 3 * y * width + 3 * hor_ticks + 6 * (int)(sync_porch_len * drate);
			p[0] = 0;
			p[1] = 255;
			p[2] = 255;
			y++;
#else
			process_line(pixel, y_pixel, uv_pixel, y_width, uv_width, width, height, y++);
#endif
			if (y == height)
				break;
			odd ^= 1;
			hor_ticks = 0;
			y_pixel_x = 0;
			uv_pixel_x = 0;
		}

		// if horizontal sync is missing, we extrapolate from last sync
		if (hor_ticks >= (int)((hor_len + sync_porch_len) * drate)) {
#if DEBUG
			for (int i = 0; i < 4; i++) {
				uint8_t *p = pixel + 3 * y * width + 3 * (width - i - 5);
				p[0] = 255;
				p[1] = 255;
				p[2] = 0;
			}
			y++;
#else
			process_line(pixel, y_pixel, uv_pixel, y_width, uv_width, width, height, y++);
#endif
			if (y == height)
				break;
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
#if DEBUG
				for (int i = 0; i < 4; i++) {
					uint8_t *p = pixel + 3 * y * width + 3 * (width - i - 15);
					p[0] = 255;
					p[1] = 0;
					p[2] = 0;
				}
#endif
			}
			// odd seperator
			if (odd_count > evn_count && !odd) {
				odd = 1;
				seperator_correction++;
#if DEBUG
				for (int i = 0; i < 4; i++) {
					uint8_t *p = pixel + 3 * y * width + 3 * (width - i - 15);
					p[0] = 0;
					p[1] = 255;
					p[2] = 0;
				}
#endif
			}
			evn_count = 0;
			odd_count = 0;
		}
#if DEBUG
		float fixme = 0.0007;
		if (hor_ticks == (int)((fixme + sync_porch_len) * drate) ||
			hor_ticks == (int)((fixme + sync_porch_len + y_len) * drate) ||
			hor_ticks == (int)((fixme + sync_porch_len + y_len + seperator_len) * drate) ||
			hor_ticks == (int)((fixme + sync_porch_len + y_len + seperator_len + porch_len) * drate) ||
			hor_ticks == (int)((fixme + sync_porch_len + y_len + seperator_len + porch_len + uv_len) * drate)) {
			uint8_t *p = pixel + 3 * y * width + 3 * hor_ticks;
			p[0] = 255;
			p[1] = 0;
			p[2] = 0;
		}
#else
		// TODO: need better way to compensate for pulse decay time
		float fixme = 0.0007;
		if (y_pixel_x < y_width && hor_ticks >= (int)((fixme + sync_porch_len) * drate))
			y_pixel[y_pixel_x++ + (y % 2) * y_width] = limit(0.0, 255.0, 255.0 * (dat_freq - 1500.0) / 800.0);

		if (uv_pixel_x < uv_width && hor_ticks >= (int)((fixme + sync_porch_len + y_len + seperator_len + porch_len) * drate))
			uv_pixel[uv_pixel_x++ + odd * uv_width] = limit(0.0, 255.0, 255.0 * (dat_freq - 1500.0) / 800.0);
#endif
	}

	munmap_file(wav_p, wav_size);

	free_ddc(cnt_ddc);
	free_ddc(dat_ddc);
	free(cnt_amp);
	free(dat_amp);

	munmap_file(ppm_p, ppm_size);
	fprintf(stderr, "%d missing sync's and %d corrections from seperator\n", missing_sync, seperator_correction);

	return 0;
}

