CFLAGS = -std=gnu99 -W -Wall -O3
LDFLAGS = -lm

generate: blur
	./blur > ../app/src/main/rs/blur_generated.rsh

blur: blur.c
