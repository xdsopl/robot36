
#include <stdint.h>
#include <string.h>
#include <malloc.h>
#include "pcm.h"
#include "alsa.h"
#include "wav.h"

void close_pcm(pcm_t *pcm)
{
	pcm->close(pcm);
	free(pcm);
}

void info_pcm(pcm_t *pcm)
{
	pcm->info(pcm);
}

int rate_pcm(pcm_t *pcm)
{
	return pcm->rate(pcm);
}

int channels_pcm(pcm_t *pcm)
{
	return pcm->channels(pcm);
}

int read_pcm(pcm_t *pcm, short *buff, int frames)
{
	return pcm->read(pcm, buff, frames);
}

int open_pcm(pcm_t **p, char *name)
{
	if (strstr(name, "plughw:") == name || strstr(name, "hw:") == name || strstr(name, "default") == name)
		return open_alsa(p, name);
	if (strstr(name, ".wav") == (name + (strlen(name) - strlen(".wav"))))
		return open_wav(p, name);
	return 0;
}

