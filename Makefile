
CC = gcc
CFLAGS = -D_GNU_SOURCE= -W -Wall -O3 -std=c99 -lm -ffast-math

all: encode decode
	./encode smpte.ppm 8000.wav 8000
	./encode smpte.ppm 11025.wav 11025
	./encode smpte.ppm 40000.wav 40000
	./encode smpte.ppm 44100.wav 44100
	./encode smpte.ppm 48000.wav 48000
	./decode 8000.wav 8000.ppm
	./decode 11025.wav 11025.ppm
	./decode 40000.wav 40000.ppm
	./decode 44100.wav 44100.ppm
	./decode 48000.wav 48000.ppm

clean:
	rm -f encode decode {8000,11025,40000,44100,48000}.{ppm,wav}

encode: encode.c Makefile
	$(CC) -o $@ $< $(CFLAGS)

decode: decode.c Makefile
	$(CC) -o $@ $< $(CFLAGS) -DDN=1 -DUP=1

