/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <SDL.h>
#include "img.h"

struct data {
	uint8_t *pixel;
	int quit;
	int stop;
	int busy;
	int okay;
	SDL_Surface *screen;
	SDL_Thread *thread;
};

struct sdl {
	struct img base;
	struct data *data;
};

void handle_events()
{
	SDL_Event event;
	while (SDL_PollEvent(&event)) {
		switch (event.type) {
			case SDL_KEYDOWN:
				switch (event.key.keysym.sym) {
					case SDLK_q:
						exit(0);
						break;
					case SDLK_ESCAPE:
						exit(0);
						break;
					default:
						break;
				}
				break;
			case SDL_QUIT:
				exit(0);
				break;
			default:
				break;
		}
	}

}

int update_sdl(void *ptr)
{
	struct data *data = (struct data *)ptr;
	while (!data->quit) {
		if (data->stop) {
			data->busy = 0;
			SDL_Delay(100);
			continue;
		}
		data->busy = 1;
		if (data->okay) {
			for (int i = 0; i < data->screen->w * data->screen->h; i++) {
				uint8_t *src = data->pixel + i * 3;
				uint8_t *dst = data->screen->pixels + i * 4;
				dst[0] = src[2];
				dst[1] = src[1];
				dst[2] = src[0];
				dst[3] = 0;
			}
		}
		SDL_Flip(data->screen);
		SDL_Delay(100);
		handle_events();
	}
	return 0;
}

void close_sdl(struct img *img)
{
	struct sdl *sdl = (struct sdl *)(img->data);
	sdl->data->okay = 0;
	sdl->data->stop = 1;
	while (sdl->data->busy)
		SDL_Delay(50);
	sdl->data->stop = 0;
	free(sdl->base.pixel);
	free(sdl);
}

int open_sdl_write(struct img **p, char *name, int width, int height)
{
	struct sdl *sdl = (struct sdl *)malloc(sizeof(struct sdl));
	sdl->base.close = close_sdl;
	sdl->base.data = (void *)sdl;

	static struct data *data = 0;
	if (!data) {
		data = (struct data *)malloc(sizeof(struct data));
		data->quit = 0;
		data->stop = 1;
		data->okay = 0;
		data->busy = 0;
		atexit(SDL_Quit);
		SDL_Init(SDL_INIT_VIDEO);
		data->thread = SDL_CreateThread(update_sdl, data);
		if (!data->thread) {
			fprintf(stderr, "Unable to create thread: %s\n", SDL_GetError());
			SDL_Quit();
			free(data);
			free(sdl);
			return 0;
		}
	}
	data->okay = 0;
	data->stop = 1;
	while (data->busy)
		SDL_Delay(50);
	data->screen = SDL_SetVideoMode(width, height, 32, SDL_SWSURFACE);
	if (!data->screen) {
		fprintf(stderr, "couldnt open %s window %dx%d@32\n", name, width, height);
		data->quit = 1;
		SDL_WaitThread(data->thread, 0);
		SDL_Quit();
		free(data);
		free(sdl);
		return 0;
	}
	if (data->screen->format->BytesPerPixel != 4 || data->screen->w != width || data->screen->h != height) {
		fprintf(stderr, "requested %dx%d@32 but got %s window %dx%d@32\n", width, height, name, data->screen->w, data->screen->h);
		data->quit = 1;
		SDL_WaitThread(data->thread, 0);
		SDL_Quit();
		free(data);
		free(sdl);
		return 0;
	}
	SDL_WM_SetCaption("robot36", "robot36");
	SDL_EnableKeyRepeat(SDL_DEFAULT_REPEAT_DELAY, SDL_DEFAULT_REPEAT_INTERVAL);

	data->pixel = malloc(width * height * 3);
	memset(data->pixel, 0, width * height * 3);
	memset(data->screen->pixels, 0, width * height * 4);

	sdl->base.width = width;
	sdl->base.height = height;
	sdl->data = data;
	sdl->base.pixel = data->pixel;

	data->okay = 1;
	data->stop = 0;

	*p = &(sdl->base);

	return 1;
}

