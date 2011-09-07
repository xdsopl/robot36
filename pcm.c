
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
	if (strstr(name, "alsa:"))
		return open_alsa(p, name + strlen("alsa:"));
	if (strstr(name, "wav:"))
		return open_wav(p, name + strlen("wav:"));
	return 0;
}

