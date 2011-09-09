/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#include <stdio.h>
#include <stdlib.h>
#include <alsa/asoundlib.h> 
#include "alsa.h"

typedef struct {
	void (*close)(pcm_t *);
	void (*info)(pcm_t *);
	int (*rate)(pcm_t *);
	int (*channels)(pcm_t *);
	int (*rw)(struct pcm *, short *, int);
	snd_pcm_t *pcm;
	int index;
	int frames;
	int r;
	int c;
} alsa_t;

void close_alsa(pcm_t *pcm)
{
	alsa_t *alsa = (alsa_t *)pcm;
	snd_pcm_drain(alsa->pcm);
	snd_pcm_close(alsa->pcm);
}

void info_alsa(pcm_t *pcm)
{
	alsa_t *alsa = (alsa_t *)pcm;
	if (alsa->frames)
		fprintf(stderr, "%d channel(s), %d rate, %.2f seconds\n", alsa->c, alsa->r, (float)alsa->frames / (float)alsa->r);
	else
		fprintf(stderr, "%d channel(s), %d rate\n", alsa->c, alsa->r);
}
int rate_alsa(pcm_t *pcm)
{
	alsa_t *alsa = (alsa_t *)pcm;
	return alsa->r;
}
int channels_alsa(pcm_t *pcm)
{
	alsa_t *alsa = (alsa_t *)pcm;
	return alsa->c;
}
int read_alsa(pcm_t *pcm, short *buff, int frames)
{
	alsa_t *alsa = (alsa_t *)pcm;
	int got = 0;
	while (0 < frames) {
		while ((got = snd_pcm_readi(alsa->pcm, buff, frames)) < 0)
			snd_pcm_prepare(alsa->pcm);
		buff += got * alsa->c;
		frames -= got;
	}
	return 1;
}

int write_alsa(pcm_t *pcm, short *buff, int frames)
{
	alsa_t *alsa = (alsa_t *)pcm;
	if (alsa->frames && (alsa->index + frames) > alsa->frames)
		return 0;
	alsa->index += frames;
	int got = 0;
	while (0 < frames) {
		while ((got = snd_pcm_writei(alsa->pcm, buff, frames)) < 0)
			snd_pcm_prepare(alsa->pcm);
		buff += got * alsa->c;
		frames -= got;
	}
	return 1;
}

int open_alsa_read(pcm_t **p, char *name)
{
	alsa_t *alsa = (alsa_t *)malloc(sizeof(alsa_t));
	alsa->close = close_alsa;
	alsa->info = info_alsa;
	alsa->rate = rate_alsa;
	alsa->channels = channels_alsa;
	alsa->rw = read_alsa;

	snd_pcm_t *pcm;
	snd_pcm_hw_params_t *params;
	snd_pcm_hw_params_alloca(&params);

	if (snd_pcm_open(&pcm, name, SND_PCM_STREAM_CAPTURE, 0) < 0) {
		fprintf(stderr, "Error opening PCM device %s\n", name);
		free(alsa);
		return 0;
	}
  
	if (snd_pcm_hw_params_any(pcm, params) < 0) {
		fprintf(stderr, "Can not configure this PCM device.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}
  
	if (snd_pcm_hw_params_set_access(pcm, params, SND_PCM_ACCESS_RW_INTERLEAVED) < 0) {
		fprintf(stderr, "Error setting access.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}

	if (snd_pcm_hw_params_set_format(pcm, params, SND_PCM_FORMAT_S16_LE) < 0) {
		fprintf(stderr, "Error setting S16_LE format.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}

	if (snd_pcm_hw_params_set_rate_resample(pcm, params, 0) < 0) {
		fprintf(stderr, "Error disabling resampling.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}

	if (snd_pcm_hw_params(pcm, params) < 0) {
		fprintf(stderr, "Error setting HW params.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}
	unsigned int rate = 0;
	if (snd_pcm_hw_params_get_rate(params, &rate, 0) < 0) {
		fprintf(stderr, "Error getting rate.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}
	unsigned int channels = 0;
	if (snd_pcm_hw_params_get_channels(params, &channels) < 0) {
		fprintf(stderr, "Error getting channels.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}
	alsa->pcm = pcm;
	alsa->r = rate;
	alsa->c = channels;
	*p = (pcm_t *)alsa;
	return 1;
}

int open_alsa_write(pcm_t **p, char *name, int rate, int channels, float seconds)
{
	alsa_t *alsa = (alsa_t *)malloc(sizeof(alsa_t));
	alsa->close = close_alsa;
	alsa->info = info_alsa;
	alsa->rate = rate_alsa;
	alsa->channels = channels_alsa;
	alsa->rw = write_alsa;

	snd_pcm_t *pcm;
	snd_pcm_hw_params_t *params;
	snd_pcm_hw_params_alloca(&params);

	if (snd_pcm_open(&pcm, name, SND_PCM_STREAM_PLAYBACK, 0) < 0) {
		fprintf(stderr, "Error opening PCM device %s\n", name);
		free(alsa);
		return 0;
	}
  
	if (snd_pcm_hw_params_any(pcm, params) < 0) {
		fprintf(stderr, "Can not configure this PCM device.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}
  
	if (snd_pcm_hw_params_set_access(pcm, params, SND_PCM_ACCESS_RW_INTERLEAVED) < 0) {
		fprintf(stderr, "Error setting access.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}

	if (snd_pcm_hw_params_set_format(pcm, params, SND_PCM_FORMAT_S16_LE) < 0) {
		fprintf(stderr, "Error setting S16_LE format.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}

	if (snd_pcm_hw_params_set_rate_resample(pcm, params, 0) < 0) {
		fprintf(stderr, "Error disabling resampling.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}

	if (snd_pcm_hw_params_set_rate_near(pcm, params, (unsigned int *)&rate, 0) < 0) {
		fprintf(stderr, "Error setting rate.\n");
		return 0;
	}

	if (snd_pcm_hw_params_set_channels_near(pcm, params, (unsigned int *)&channels) < 0) {
		fprintf(stderr, "Error setting channels.\n");
		return 0;
	}

	if (snd_pcm_hw_params(pcm, params) < 0) {
		fprintf(stderr, "Error setting HW params.\n");
		snd_pcm_close(alsa->pcm);
		free(alsa);
		return 0;
	}
	alsa->pcm = pcm;
	alsa->r = rate;
	alsa->c = channels;
	alsa->frames = seconds * rate;
	alsa->index = 0;
	*p = (pcm_t *)alsa;
	return 1;
}
