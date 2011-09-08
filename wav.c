/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
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
	int (*rw)(struct pcm *, short *, int);
	void *p;
	short *b;
	size_t size;
	int index;
	int frames;
	int r;
	int c;
} wav_t;

void close_wav(pcm_t *pcm)
{
	wav_t *wav = (wav_t *)pcm;
	munmap_file(wav->p, wav->size);
}

void info_wav(pcm_t *pcm)
{
	wav_t *wav = (wav_t *)pcm;
	fprintf(stderr, "%d channel(s), %d rate, %d frames\n", wav->c, wav->r, wav->frames);
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
	if ((wav->index + frames) > wav->frames)
		return 0;
	memcpy(buff, wav->b + wav->index * wav->c, sizeof(short) * frames * wav->c);
	wav->index += frames;
	return 1;
}
int write_wav(pcm_t *pcm, short *buff, int frames)
{
	wav_t *wav = (wav_t *)pcm;
	if ((wav->index + frames) > wav->frames)
		return 0;
	memcpy(wav->b + wav->index * wav->c, buff, sizeof(short) * frames * wav->c);
	wav->index += frames;
	return 1;
}

int open_wav_read(pcm_t **p, char *name)
{
	wav_t *wav = (wav_t *)malloc(sizeof(wav_t));
	wav->close = close_wav;
	wav->info = info_wav;
	wav->rate = rate_wav;
	wav->channels = channels_wav;
	wav->rw = read_wav;
	if (!mmap_file_ro(&wav->p, name, &wav->size)) {
		fprintf(stderr, "couldnt open wav file %s!\n", name);
		free(wav);
		return 0;
	}
	wav_head_t *head = (wav_head_t *)wav->p;
	wav->b = (short *)(wav->p + sizeof(wav_head_t));

	if (head->ChunkID != 0x46464952 || head->Format != 0x45564157 ||
			head->Subchunk1ID != 0x20746d66 || head->Subchunk1Size != 16 ||
			head->AudioFormat != 1 || head->Subchunk2ID != 0x61746164 ||
			head->ChunkSize != (head->Subchunk2Size + 36)) {
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
	wav->index = 0;
	wav->frames = head->Subchunk2Size / (sizeof(short) * head->NumChannels);
	wav->r = head->SampleRate;
	wav->c = head->NumChannels;
	*p = (pcm_t *)wav;
	return 1;
}

int open_wav_write(pcm_t **p, char *name, int rate, int channels, int frames)
{
	wav_t *wav = (wav_t *)malloc(sizeof(wav_t));
	wav->close = close_wav;
	wav->info = info_wav;
	wav->rate = rate_wav;
	wav->channels = channels_wav;
	wav->rw = write_wav;
	wav->size = frames * channels * sizeof(short) + sizeof(wav_head_t);
	if (!mmap_file_rw(&wav->p, name, wav->size)) {
		fprintf(stderr, "couldnt open wav file %s!\n", name);
		free(wav);
		return 0;
	}
	wav_head_t *head = (wav_head_t *)wav->p;
	wav->b = (short *)(wav->p + sizeof(wav_head_t));

	head->ChunkID = 0x46464952;
	head->ChunkSize = 36 + frames * sizeof(short) * channels;
	head->Format = 0x45564157;
	head->Subchunk1ID = 0x20746d66;
	head->Subchunk1Size = 16;
	head->AudioFormat = 1;
	head->NumChannels = channels;
	head->SampleRate = rate;
	head->ByteRate = 2 * rate;
	head->BlockAlign = 2;
	head->BitsPerSample = 16;
	head->Subchunk2ID = 0x61746164;
	head->Subchunk2Size = frames * sizeof(short) * channels;

	wav->r = rate;
	wav->c = channels;
	wav->frames = frames;
	wav->index = 0;

	*p = (pcm_t *)wav;
	return 1;
}
