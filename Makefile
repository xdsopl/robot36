
CFLAGS = -g -DUP=0 -DDN=1 -D_GNU_SOURCE=1 -W -Wall -O3 -std=c99 -fno-math-errno -ffinite-math-only -fno-rounding-math -fno-signaling-nans -fno-trapping-math -fcx-limited-range -fsingle-precision-constant $(shell sdl-config --cflags)
LDLIBS = -lm -lasound $(shell sdl-config --libs)

all: encode decode debug

test: 8000.ppm 11025.ppm 16000.ppm 40000.ppm 44100.ppm 48000.ppm

fun: 8000.gnu 11025.gnu 16000.gnu 40000.gnu 44100.gnu 48000.gnu

clean:
	rm -f encode decode debug *.o {8000,11025,16000,40000,44100,48000}.{ppm,wav,dat,gnu}

.PRECIOUS: %.wav %.dat %.ppm %.gnu

%.wav: encode
	./encode smpte.ppm $@ $(basename $@)

%.dat: %.wav debug
	./debug $< $(basename $<).ppm > $@

%.ppm: %.wav decode
	./decode $< $@

%.gnu: %.dat
	echo 'plot "$<" u 1:2 w l t "data", "$<" u 1:3 w l t "control", "$<" u 1:4 w l t "sync", "$<" u 1:5 w l t "leader", "$<" u 1:6 w l t "break", "$<" u 1:7 w l t "vis ss", "$<" u 1:8 w l t "vis lo", "$<" u 1:9 w l t "vis hi", "$<" u 1:10 w l t "even", "$<" u 1:11 w l t "odd"' > $@


encode: encode.o mmap_file.o pcm.o wav.o alsa.o yuv.o img.o ppm.o sdl.o

decode: decode.o mmap_file.o pcm.o wav.o alsa.o window.o ddc.o buffer.o yuv.o img.o ppm.o sdl.o

debug: debug.o mmap_file.o pcm.o wav.o alsa.o window.o ddc.o buffer.o yuv.o img.o ppm.o sdl.o

