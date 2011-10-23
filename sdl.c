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

typedef struct {
	void (*close)(img_t *);
	uint8_t *pixel;
	int width;
	int height;
	SDL_Surface *screen;
	SDL_Thread *thread;
	int quit;
} sdl_t;

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

int update_sdl(void *data)
{
	sdl_t *sdl = (sdl_t *)data;
	while (!sdl->quit) {
		SDL_Flip(sdl->screen);
		SDL_Delay(100);
		handle_events();
	}
	return 0;
}

void close_sdl(img_t *img)
{
	sdl_t *sdl = (sdl_t *)img;
	sdl->quit = 1;
	SDL_WaitThread(sdl->thread, 0);
	SDL_Quit();
}

int open_sdl_write(img_t **p, char *name, int width, int height)
{
	sdl_t *sdl = (sdl_t *)malloc(sizeof(sdl_t));
	sdl->close = close_sdl;

	atexit(SDL_Quit);
	SDL_Init(SDL_INIT_VIDEO);
	sdl->screen = SDL_SetVideoMode(width, height, 24, SDL_SWSURFACE);
	if (!sdl->screen) {
		fprintf(stderr, "couldnt open %s window %dx%d@24\n", name, width, height);
		SDL_Quit();
		free(sdl);
		return 0;
	}
	if (sdl->screen->format->BytesPerPixel != 3 || sdl->screen->w != width || sdl->screen->h != height) {
		fprintf(stderr, "requested %dx%d@24 but got %s window %dx%d@24\n", width, height, name, sdl->screen->w, sdl->screen->h);
		SDL_Quit();
		free(sdl);
		return 0;
	}
	SDL_WM_SetCaption("robot36", "robot36");
	SDL_EnableKeyRepeat(SDL_DEFAULT_REPEAT_DELAY, SDL_DEFAULT_REPEAT_INTERVAL);

	sdl->pixel = sdl->screen->pixels;
	memset(sdl->pixel, 0, width * height * 3);

	sdl->quit = 0;
	sdl->thread = SDL_CreateThread(update_sdl, sdl);
	if (!sdl->thread) {
		fprintf(stderr, "Unable to create thread: %s\n", SDL_GetError());
		SDL_Quit();
		free(sdl);
		return 0;
	}

	*p = (img_t *)sdl;

	return 1;
}

