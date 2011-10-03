
CFLAGS = -DUP=0 -DDN=1 -D_GNU_SOURCE=1 -W -Wall -O3 -std=c99 -ffast-math
LDFLAGS = -lm -lasound

all: encode decode debug

test: all
	./encode smpte.ppm 8000.wav 8000
	./encode smpte.ppm 11025.wav 11025
	./encode smpte.ppm 16000.wav 16000
	./encode smpte.ppm 40000.wav 40000
	./encode smpte.ppm 44100.wav 44100
	./encode smpte.ppm 48000.wav 48000
	./decode 8000.wav 8000.ppm
	./decode 11025.wav 11025.ppm
	./decode 16000.wav 16000.ppm
	./decode 40000.wav 40000.ppm
	./decode 44100.wav 44100.ppm
	./decode 48000.wav 48000.ppm

fun: all
	./encode smpte.ppm 8000.wav 8000
	./encode smpte.ppm 11025.wav 11025
	./encode smpte.ppm 16000.wav 16000
	./encode smpte.ppm 40000.wav 40000
	./encode smpte.ppm 44100.wav 44100
	./encode smpte.ppm 48000.wav 48000
	./debug 8000.wav 8000.ppm > 8000.dat
	./debug 11025.wav 11025.ppm > 11025.dat
	./debug 16000.wav 16000.ppm > 16000.dat
	./debug 40000.wav 40000.ppm > 40000.dat
	./debug 44100.wav 44100.ppm > 44100.dat
	./debug 48000.wav 48000.ppm > 48000.dat

clean:
	rm -f encode decode debug *.o {8000,11025,16000,40000,44100,48000}.{ppm,wav,dat}

encode: encode.o mmap_file.o pcm.o wav.o alsa.o yuv.o img.o ppm.o

decode: decode.o mmap_file.o pcm.o wav.o alsa.o window.o ddc.o delay.o yuv.o

debug: debug.o mmap_file.o pcm.o wav.o alsa.o window.o ddc.o delay.o yuv.o

