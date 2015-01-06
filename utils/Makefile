CFLAGS = -std=gnu99 -W -Wall -O3
LDFLAGS = -lm

generate: blur radix2 stft
	./blur > ../app/src/main/rs/blur_generated.rsh
	./radix2 > ../app/src/main/rs/radix2_generated.rsh
	./stft > ../app/src/main/rs/stft_generated.rsh

blur: blur.c
radix2: radix2.c
stft: stft.c
