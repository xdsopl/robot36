/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#ifndef WAV_H
#define WAV_H
#include "pcm.h"
int open_wav_read(struct pcm **, char *);
int open_wav_write(struct pcm **, char *, int, int, float);
#endif
