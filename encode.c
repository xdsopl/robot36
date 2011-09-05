
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
#include <limits.h>

float limit(float min, float max, float x)
{
	float tmp = x < min ? min : x;
	return tmp > max ? max : tmp;
}
float lerp(float a, float b, float x)
{
	return a - a * x + b * x;
}
uint8_t R_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	(void)U;
	return limit(0.0, 255.0, 0.003906 * ((298.082 * (Y - 16.0)) + (408.583 * (V - 128.0))));
}
uint8_t G_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	return limit(0.0, 255.0, 0.003906 * ((298.082 * (Y - 16.0)) + (-100.291 * (U - 128.0)) + (-208.12 * (V - 128.0))));
}
uint8_t B_YUV(uint8_t Y, uint8_t U, uint8_t V)
{
	(void)V;
	return limit(0.0, 255.0, 0.003906 * ((298.082 * (Y - 16.0)) + (516.411 * (U - 128.0))));
}

uint8_t Y_RGB(uint8_t R, uint8_t G, uint8_t B)
{
	return limit(0.0, 255.0, 16.0 + (0.003906 * ((65.738 * R) + (129.057 * G) + (25.064 * B))));
}
uint8_t V_RGB(uint8_t R, uint8_t G, uint8_t B)
{
	return limit(0.0, 255.0, 128.0 + (0.003906 * ((112.439 * R) + (-94.154 * G) + (-18.285 * B))));
}
uint8_t U_RGB(uint8_t R, uint8_t G, uint8_t B)
{
	return limit(0.0, 255.0, 128.0 + (0.003906 * ((-37.945 * R) + (-74.494 * G) + (112.439 * B))));
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

short *buffer;
complex float nco;
int sample;
float hz2rad;

void add_sample(float val) {
//	static float avg = 0.0;
//	const float a = 0.9;
//	avg = a * val + (1.0 - a) * avg;
//	buffer[sample++] = (float)SHRT_MAX * avg;
	buffer[sample++] = (float)SHRT_MAX * val;
//	buffer[sample++] = (float)SHRT_MAX * avg + random() / (RAND_MAX / 10000);
}
void add_freq(float freq) {
	add_sample(creal(nco));
	nco *= cexpf(freq * hz2rad * I);
}

int main(int argc, char **argv)
{
	if (argc != 4) {
		fprintf(stderr, "usage: %s <input.ppm> <output.wav> <rate>\n", argv[0]);
		return 1;
	}

	size_t ppm_size;
	char *ppm_p = mmap_file_ro(argv[1], &ppm_size);
	const int width = 320;
	const int height = 240;
	const char *ppm_head = "P6 320 240 255\n";

	if (strncmp(ppm_head, ppm_p, strlen(ppm_head))) {
		fprintf(stderr, "unsupported image file\n");
		return 1;
	}

	uint8_t *pixel = (uint8_t *)ppm_p + strlen(ppm_head);

	float rate = atoi(argv[3]);

	if (fabsf(0.0015 * rate - (int)(0.0015 * rate)) > 0.0001)
		fprintf(stderr, "this rate will not give accurate (smooth) results.\ntry 40000Hz and resample to %0.fHz\n", rate);

	hz2rad = (2.0 * M_PI) / rate;
	nco = -I * 0.7;
	enum { N = 13 };
	float seq_freq[N] = { 1900.0, 1200.0, 1900.0, 1200.0, 1300.0, 1300.0, 1300.0, 1100.0, 1300.0, 1300.0, 1300.0, 1100.0, 1200.0 };
	float seq_time[N] = { 0.3, 0.01, 0.3, 0.03, 0.03, 0.03, 0.03, 0.03, 0.03, 0.03, 0.03, 0.03, 0.03 };

	size_t wav_size = 4096 * ((size_t)(37.5 * rate * 2 + 44 + 4095) / 4096);
	int samples = (wav_size - 44) / 2;
	char *wav_p = mmap_file_rw(argv[2], wav_size);
	if (!wav_p)
		return 1;

	buffer = (short *)(wav_p + sizeof(wav_t));

	sample = 0;

	for (int ticks = 0; ticks < (int)(0.3 * rate); ticks++)
		add_sample(0.0);

	for (int i = 0; i < N; i++)
		for (int ticks = 0; ticks < (int)(seq_time[i] * rate); ticks++)
			add_freq(seq_freq[i]);

	for (int y = 0; y < height; y++) {
		// EVEN LINES
		// SYNC
		for (int ticks = 0; ticks < (int)(0.009 * rate); ticks++) {
			add_freq(1200.0);
		}
		// PORCH
		for (int ticks = 0; ticks < (int)(0.003 * rate); ticks++) {
			add_freq(1500.0);
		}
		// Y
		for (int ticks = 0; ticks < (int)(0.088 * rate); ticks++) {
			float xf = limit(0.0, 319.0, (320.0 * (float)ticks) / (0.088 * rate));
			int x0 = xf;
			int x1 = limit(0.0, 319.0, x0 + 1);
			int off0 = 3 * y * width + 3 * x0;
			int off1 = 3 * y * width + 3 * x1;
			uint8_t R0 = pixel[off0 + 0];
			uint8_t G0 = pixel[off0 + 1];
			uint8_t B0 = pixel[off0 + 2];
			uint8_t R1 = pixel[off1 + 0];
			uint8_t G1 = pixel[off1 + 1];
			uint8_t B1 = pixel[off1 + 2];
			uint8_t R = lerp(R0, R1, xf - (float)x0);
			uint8_t G = lerp(G0, G1, xf - (float)x0);
			uint8_t B = lerp(B0, B1, xf - (float)x0);
			add_freq(1500.0 + 800.0 * Y_RGB(R, G, B) / 255.0);
		}
		// EVEN
		for (int ticks = 0; ticks < (int)(0.0045 * rate); ticks++) {
			add_freq(1500.0);
		}
		// PORCH
		for (int ticks = 0; ticks < (int)(0.0015 * rate); ticks++) {
			add_freq(1900.0);
		}
		// V
		for (int ticks = 0; ticks < (int)(0.044 * rate); ticks++) {
			float xf = limit(0.0, 159.0, (160.0 * (float)ticks) / (0.044 * rate));
			int x0 = xf;
			int x1 = limit(0.0, 159.0, x0 + 1);
			int evn0 = 3 * y * width + 6 * x0;
			int evn1 = 3 * y * width + 6 * x1;
			int odd0 = 3 * (y + 1) * width + 6 * x0;
			int odd1 = 3 * (y + 1) * width + 6 * x1;
			uint8_t R0 = (pixel[evn0 + 0] + pixel[odd0 + 0] + pixel[evn0 + 3] + pixel[odd0 + 3]) / 4;
			uint8_t G0 = (pixel[evn0 + 1] + pixel[odd0 + 1] + pixel[evn0 + 4] + pixel[odd0 + 4]) / 4;
			uint8_t B0 = (pixel[evn0 + 2] + pixel[odd0 + 2] + pixel[evn0 + 5] + pixel[odd0 + 5]) / 4;
			uint8_t R1 = (pixel[evn1 + 0] + pixel[odd1 + 0] + pixel[evn1 + 3] + pixel[odd1 + 3]) / 4;
			uint8_t G1 = (pixel[evn1 + 1] + pixel[odd1 + 1] + pixel[evn1 + 4] + pixel[odd1 + 4]) / 4;
			uint8_t B1 = (pixel[evn1 + 2] + pixel[odd1 + 2] + pixel[evn1 + 5] + pixel[odd1 + 5]) / 4;
			uint8_t R = lerp(R0, R1, xf - (float)x0);
			uint8_t G = lerp(G0, G1, xf - (float)x0);
			uint8_t B = lerp(B0, B1, xf - (float)x0);
			add_freq(1500.0 + 800.0 * V_RGB(R, G, B) / 255.0);
		}
		// ODD LINES
		y++;
		// SYNC
		for (int ticks = 0; ticks < (int)(0.009 * rate); ticks++) {
			add_freq(1200.0);
		}
		// PORCH
		for (int ticks = 0; ticks < (int)(0.003 * rate); ticks++) {
			add_freq(1500.0);
		}
		// Y
		for (int ticks = 0; ticks < (int)(0.088 * rate); ticks++) {
			float xf = limit(0.0, 319.0, (320.0 * (float)ticks) / (0.088 * rate));
			int x0 = xf;
			int x1 = limit(0.0, 319.0, x0 + 1);
			int off0 = 3 * y * width + 3 * x0;
			int off1 = 3 * y * width + 3 * x1;
			uint8_t R0 = pixel[off0 + 0];
			uint8_t G0 = pixel[off0 + 1];
			uint8_t B0 = pixel[off0 + 2];
			uint8_t R1 = pixel[off1 + 0];
			uint8_t G1 = pixel[off1 + 1];
			uint8_t B1 = pixel[off1 + 2];
			uint8_t R = lerp(R0, R1, xf - (float)x0);
			uint8_t G = lerp(G0, G1, xf - (float)x0);
			uint8_t B = lerp(B0, B1, xf - (float)x0);
			add_freq(1500.0 + 800.0 * Y_RGB(R, G, B) / 255.0);
		}
		// ODD
		for (int ticks = 0; ticks < (int)(0.0045 * rate); ticks++) {
			add_freq(2300.0);
		}
		// PORCH
		for (int ticks = 0; ticks < (int)(0.0015 * rate); ticks++) {
			add_freq(1900.0);
		}
		// U
		for (int ticks = 0; ticks < (int)(0.044 * rate); ticks++) {
			float xf = limit(0.0, 159.0, (160.0 * (float)ticks) / (0.044 * rate));
			int x0 = xf;
			int x1 = limit(0.0, 159.0, x0 + 1);
			int evn0 = 3 * (y - 1) * width + 6 * x0;
			int evn1 = 3 * (y - 1) * width + 6 * x1;
			int odd0 = 3 * y * width + 6 * x0;
			int odd1 = 3 * y * width + 6 * x1;
			uint8_t R0 = (pixel[evn0 + 0] + pixel[odd0 + 0] + pixel[evn0 + 3] + pixel[odd0 + 3]) / 4;
			uint8_t G0 = (pixel[evn0 + 1] + pixel[odd0 + 1] + pixel[evn0 + 4] + pixel[odd0 + 4]) / 4;
			uint8_t B0 = (pixel[evn0 + 2] + pixel[odd0 + 2] + pixel[evn0 + 5] + pixel[odd0 + 5]) / 4;
			uint8_t R1 = (pixel[evn1 + 0] + pixel[odd1 + 0] + pixel[evn1 + 3] + pixel[odd1 + 3]) / 4;
			uint8_t G1 = (pixel[evn1 + 1] + pixel[odd1 + 1] + pixel[evn1 + 4] + pixel[odd1 + 4]) / 4;
			uint8_t B1 = (pixel[evn1 + 2] + pixel[odd1 + 2] + pixel[evn1 + 5] + pixel[odd1 + 5]) / 4;
			uint8_t R = lerp(R0, R1, xf - (float)x0);
			uint8_t G = lerp(G0, G1, xf - (float)x0);
			uint8_t B = lerp(B0, B1, xf - (float)x0);
			add_freq(1500.0 + 800.0 * U_RGB(R, G, B) / 255.0);
		}
	}

	while (sample < samples)
		add_sample(0.0);

	wav_t *wav = (wav_t *)wav_p;
	wav->ChunkID = 0x46464952;
	wav->ChunkSize = 36 + 2 * samples;
	wav->Format = 0x45564157;
	wav->Subchunk1ID = 0x20746d66;
	wav->Subchunk1Size = 16;
	wav->AudioFormat = 1;
	wav->NumChannels = 1;
	wav->SampleRate = rate;
	wav->ByteRate = 2 * rate;
	wav->BlockAlign = 2;
	wav->BitsPerSample = 16;
	wav->Subchunk2ID = 0x61746164;
	wav->Subchunk2Size = 2 * samples;

	munmap_file(wav_p, wav_size);
	munmap_file(ppm_p, ppm_size);
	return 0;
}

