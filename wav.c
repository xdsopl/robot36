
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <malloc.h>
#include "wav.h"
#include "mmap_file.h"

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
} wav_head_t;

typedef struct {
	void (*close)(pcm_t *);
	void (*info)(pcm_t *);
	int (*rate)(pcm_t *);
	int (*channels)(pcm_t *);
	int (*read)(struct pcm *, short *, int);
	void *p;
	short *b;
	size_t size;
	int r;
	int c;
	int samples;
	int index;
} wav_t;

void close_wav(pcm_t *pcm)
{
	wav_t *wav = (wav_t *)pcm;
	munmap_file(wav->p, wav->size);
}

void info_wav(pcm_t *pcm)
{
	wav_t *wav = (wav_t *)pcm;
	fprintf(stderr, "%d channel(s), %d rate, %d samples\n", wav->c, wav->r, wav->samples);
}
int rate_wav(pcm_t *pcm)
{
	wav_t *wav = (wav_t *)pcm;
	return wav->r;
}
int channels_wav(pcm_t *pcm)
{
	wav_t *wav = (wav_t *)pcm;
	return wav->c;
}
int read_wav(pcm_t *pcm, short *buff, int frames)
{
	wav_t *wav = (wav_t *)pcm;
	if ((wav->index + frames * wav->c) > wav->samples)
		return 0;
	memcpy(buff, wav->b + wav->index, sizeof(short) * frames * wav->c);
	wav->index += frames * wav->c;
	return 1;
}

int open_wav(pcm_t **p, char *name)
{
	wav_t *wav = (wav_t *)malloc(sizeof(wav_t));
	wav->close = close_wav;
	wav->info = info_wav;
	wav->rate = rate_wav;
	wav->channels = channels_wav;
	wav->read = read_wav;
	if (!mmap_file_ro(&wav->p, name, &wav->size)) {
		fprintf(stderr, "couldnt open wav file %s!\n", name);
		free(wav);
		return 0;
	}
	wav_head_t *head = (wav_head_t *)wav->p;
	wav->b = (short *)(wav->p + sizeof(wav_head_t));

	if (head->ChunkID != 0x46464952 || head->Format != 0x45564157 ||
			head->Subchunk1ID != 0x20746d66 || head->Subchunk1Size != 16 ||
			head->AudioFormat != 1 || head->Subchunk2ID != 0x61746164) {
		fprintf(stderr, "unsupported WAV file!\n");
		munmap_file(wav->p, wav->size);
		free(wav);
		return 0;
	}
	if (head->BitsPerSample != 16) {
		fprintf(stderr, "only 16bit WAV supported!\n");
		munmap_file(wav->p, wav->size);
		free(wav);
		return 0;
	}
	wav->c = head->NumChannels;
	wav->samples = head->Subchunk2Size / 2;
	wav->index = 0;
	wav->r = head->SampleRate;
	*p = (pcm_t *)wav;
	return 1;
}

