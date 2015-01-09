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
    for (int j = 0; j < spectrum_height; ++j) {
        int i = (radix2_N * freq + sample_rate / 2) / sample_rate;
        spectrum_buffer[spectrum_width * j + i] = rgb(255, 255, 255);
    }
}

static void spectrum_analyzer(int amplitude)
{
    const int M = 7;
    static int n, m;
    static complex_t input[radix2_N];
    static complex_t output[radix2_N];

#if 1
    static cic_cascade_t cascade;
    int tmp = cic_int_cascade(&cascade, amplitude);
    if (++m < M)
        return;
    m = 0;
    amplitude = cic_comb_cascade(&cascade, tmp);
#else
    static int sum;
    sum += amplitude;
    if (++m < M)
        return;
    m = 0;
    amplitude = sum / M;
    sum = 0;
#endif

    input[n&(radix2_N-1)] += complex(stft_w[n] * amplitude, 0.0f);
    if (++n >= stft_N) {
        n = 0;
        // yep, were wasting 3x performance
        radix2(output, input, radix2_N, 1, 0);
        for (int i = 0; i < radix2_N; ++i)
            input[i] = 0.0f;
        float maximum = 0.0f;
        for (int i = 0; i < radix2_N; ++i)
            maximum = max(maximum, cabs(output[i]));
        for (int j = spectrum_height - 1; 0 < j; --j)
            for (int i = 0; i < spectrum_width; ++i)
                spectrum_buffer[spectrum_width * j + i] = spectrum_buffer[spectrum_width * (j-1) + i];
        for (int i = 0; i < spectrum_width; ++i) {
            int b = (i * (radix2_N / 2)) / spectrum_width;
            float power = clamp(pown(cabs(output[b]) / maximum, 2), 0.0f, 1.0f);
            float dB = 10.0f * log10(max(0.000001f, power));
            float v = clamp((60.0f + dB) / 60.0f, 0.0f, 1.0f);
            spectrum_buffer[i] = rainbow(v);
        }
        freq_marker(1100 * M);
        freq_marker(1300 * M);
        freq_marker(1500 * M);
        freq_marker(2300 * M);
#if 0
        for (int j = spectrum_height / 2; j < spectrum_height; ++j)
            for (int i = 0; i < spectrum_width; ++i)
                spectrum_buffer[spectrum_width * j + i] = rainbow((float)i / spectrum_width);
#endif
    }
}

#endif