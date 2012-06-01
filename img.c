/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/

#include <stdlib.h>
#include <string.h>
#include "img.h"
#include "ppm.h"
#include "sdl.h"

void close_img(struct img *img)
{
	img->close(img);
}

int open_img_read(struct img **p, char *name)
{
	if (strstr(name, ".ppm") == (name + (strlen(name) - strlen(".ppm"))))
		return open_ppm_read(p, name);
	return 0;
}

int open_img_write(struct img **p, char *name, int width, int height)
{
	if (strstr(name, ".ppm") == (name + (strlen(name) - strlen(".ppm"))))
		return open_ppm_write(p, name, width, height);
	if (strstr(name, "sdl:") == name)
		return open_sdl_write(p, name, width, height);
	return 0;
}

