/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include "pcm.h"
#include "alsa.h"
#include "wav.h"

void close_pcm(struct pcm *pcm)
{
	pcm->close(pcm);
}

void info_pcm(struct pcm *pcm)
{
	pcm->info(pcm);
}

int rate_pcm(struct pcm *pcm)
{
	return pcm->rate(pcm);
}

int channels_pcm(struct pcm *pcm)
{
	return pcm->channels(pcm);
}

int read_pcm(struct pcm *pcm, short *buff, int frames)
{
	return pcm->rw(pcm, buff, frames);
}

int write_pcm(struct pcm *pcm, short *buff, int frames)
{
	return pcm->rw(pcm, buff, frames);
}

int open_pcm_read(struct pcm **p, char *name)
{
	if (strstr(name, "plughw:") == name || strstr(name, "hw:") == name || strstr(name, "default") == name)
		return open_alsa_read(p, name);
	if (strstr(name, ".wav") == (name + (strlen(name) - strlen(".wav"))))
		return open_wav_read(p, name);
	return 0;
}

int open_pcm_write(struct pcm **p, char *name, int rate, int channels, float seconds)
{
	if (strstr(name, "plughw:") == name || strstr(name, "hw:") == name || strstr(name, "default") == name)
		return open_alsa_write(p, name, rate, channels, seconds);
	if (strstr(name, ".wav") == (name + (strlen(name) - strlen(".wav"))))
		return open_wav_write(p, name, rate, channels, seconds);
	return 0;
}

