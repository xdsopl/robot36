/*
Copyright 2015 Ahmet Inan <xdsopl@googlemail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

#ifndef STFT_RSH
#define STFT_RSH

#include "state.rsh"
#include "exports.rsh"
#include "utils.rsh"
#include "complex.rsh"
#include "radix2.rsh"
#include "cic.rsh"
#include "stft_generated.rsh"

static inline uchar4 rainbow(float v)
{
    float r = v * clamp(4.0f * v - 2.0f, 0.0f, 1.0f);
    float g = v * clamp(1.0f - 4.0f * fabs(v - 0.5f), 0.0f, 1.0f);
    float b = v * clamp(2.0f - 4.0f * v, 0.0f, 1.0f);
    return rgb(255.0f * sqrt(r), 255.0f * sqrt(g), 255.0f * sqrt(b));
}

static void freq_marker(int freq)
{
    int sgi = (spectrogram_width * freq + sample_rate / 4) / (sample_rate / 2);
    spectrogram_buffer[sgi] = rgb(255, 255, 255);

    int si = (spectrum_width * freq + sample_rate / 4) / (sample_rate / 2);
    for (int j = 0; j < spectrum_height; ++j)
        spectrum_buffer[spectrum_width * j + si] = rgb(255, 255, 255);
}

static void show_rainbow()
{
    for (int j = 0; j < spectrogram_height; ++j)
        for (int i = 0; i < spectrogram_width; ++i)
            spectrogram_buffer[spectrogram_width * j + i] = rainbow((float)i / spectrogram_width);
}

static void clear_spectrum()
{
    for (int i = 0; i < spectrum_height * spectrum_width; ++i)
        spectrum_buffer[i] = 0;
    for (int i = 0; i < spectrogram_height * spectrogram_width; ++i)
        spectrogram_buffer[i] = 0;
}

static void fade_spectrum()
{
    for (int i = 0; i < spectrum_height * spectrum_width; ++i) {
        int b = (spectrum_buffer[i][0] * 7) >> 3;
        int g = (spectrum_buffer[i][1] * 7) >> 3;
        int r = (spectrum_buffer[i][2] * 7) >> 3;
        spectrum_buffer[i] = rgb(r, g, b);
    }
}

static void init_analyzer(int sw, int sh, int sgw, int sgh)
{
    disable_analyzer = 0;
    spectrum_width = sw;
    spectrum_height = sh;
    spectrogram_width = sgw;
    spectrogram_height = sgh;
    show_rainbow();
}

void enable_analyzer(int enable)
{
    disable_analyzer = !enable;
    if (disable_analyzer)
        clear_spectrum();
    else
        show_rainbow();
}

// only made for abs(x0-x1) <= 1
static void draw_line(int x0, int y0, int x1, int y1)
{
    int a0 = min(y0, (y0 + y1) / 2);
    int a1 = max(y0, (y0 + y1) / 2);
    for (int y = a0; y <= a1; ++y)
        spectrum_buffer[spectrum_width * y + x0] = rgb(255, 255, 255);
    int b0 = min((y0 + y1) / 2, y1);
    int b1 = max((y0 + y1) / 2, y1);
    for (int y = b0; y <= b1; ++y)
        spectrum_buffer[spectrum_width * y + x1] = rgb(255, 255, 255);
}

static void spectrum_analyzer(int amplitude)
{
    const int M = 7;
    static int n, m;
    static int buffer[stft_N];
    static float input[radix2_N];
    static complex_t output[radix2_N];

    if (disable_analyzer)
        return;

#if 1
    const int order = 5;
    const int gain = pown(M, order);
    static cic_t cascade[order];
    int tmp = cic_int_cascade(cascade, amplitude, order);
    if (++m < M)
        return;
    m = 0;
    amplitude = cic_comb_cascade(cascade, tmp, order) / gain;
#else
    static int sum;
    sum += amplitude;
    if (++m < M)
        return;
    m = 0;
    amplitude = sum / M;
    sum = 0;
#endif

    buffer[n] = amplitude;
    if (!(++n&(radix2_N-1))) {
        n &= stft_N - 1;
        for (int i = 0; i < stft_N; ++i)
            input[i&(radix2_N-1)] += stft_w[i] * buffer[(i+n)&(stft_N-1)];
        // yep, were wasting 2x performance
        radix2(output, input, radix2_N, 1);
        for (int i = 0; i < radix2_N; ++i)
            input[i] = 0.0f;
        for (int j = spectrogram_height - 1; 0 < j; --j)
            for (int i = 0; i < spectrogram_width; ++i)
                spectrogram_buffer[spectrogram_width * j + i] = spectrogram_buffer[spectrogram_width * (j-1) + i];
        for (int i = 0; i < spectrogram_width; ++i) {
            int b = (i * (radix2_N / 2)) / spectrogram_width;
            float power = min(pown(cabs(output[b]) / 127.0f, 2), 1.0f);
            float dB = 10.0f * log10(max(0.000001f, power));
            float v = clamp((60.0f + dB) / 60.0f, 0.0f, 1.0f);
            spectrogram_buffer[i] = rainbow(v);
        }
        fade_spectrum();
        for (int b = 0, i0, j0; b < radix2_N / 2; ++b) {
            float power = min(pown(cabs(output[b]) / 127.0f, 2), 1.0f);
            float dB = 10.0f * log10(max(0.000001f, power));
            float v = clamp((60.0f + dB) / 60.0f, 0.0f, 1.0f);
            int i1 = (b * spectrum_width) / (radix2_N / 2);
            int j1 = (spectrum_height - 1) - (spectrum_height - 1) * v;
            if (b)
                draw_line(i0, j0, i1, j1);
            i0 = i1;
            j0 = j1;
        }
        freq_marker(1100 * M);
        freq_marker(1300 * M);
        freq_marker(1500 * M);
        freq_marker(2300 * M);
    }
}

#endif