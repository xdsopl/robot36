/*
Copyright 2014 Ahmet Inan <xdsopl@googlemail.com>

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

#ifndef EMA_RSH
#define EMA_RSH

#include "complex.rsh"

static float ema_a(float cutoff, float rate)
{
    float RC = 1.0f / (2.0f * M_PI * cutoff);
    float dt = 1.0f / rate;
    return dt / (RC + dt);
}

static float ema_cascade_a(float cutoff, float rate, int order)
{
    return ema_a(cutoff / sqrt(rootn(2.0f, order) - 1.0f), rate);
}

static inline float ema(float *output, float input, float a)
{
    return *output = a * input + (1.0f - a) * *output;
}

static inline complex_t cema(complex_t *output, complex_t input, float a)
{
    return *output = a * input + (1.0f - a) * *output;
}

static complex_t cema_cascade(complex_t *output, complex_t input, float a, int order)
{
    for (int i = 0; i < order; ++i)
        input = cema(output + i, input, a);
    return input;
}

#endif