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

typedef struct {
    float prev, a;
} ema_t;

typedef struct {
    complex_t prev;
    float a;
} cema_t;

static const int cema_cascade_order = 11;
typedef struct {
    cema_t ema[cema_cascade_order];
} cema_cascade_t;

static float ema_cutoff_a(float cutoff, float rate)
{
    float RC = 1.0f / (2.0f * M_PI * cutoff);
    float dt = 1.0f / rate;
    return dt / (RC + dt);
}

static inline ema_t ema(float a)
{
    return (ema_t){ 0.0f, a };
}

static inline cema_t cema(float a)
{
    return (cema_t){ complex(0.0f, 0.0f), a };
}

static ema_t ema_cutoff(float cutoff, float rate)
{
    return ema(ema_cutoff_a(cutoff, rate));
}

static cema_t cema_cutoff(float cutoff, float rate)
{
    return cema(ema_cutoff_a(cutoff, rate));
}

static cema_cascade_t cema_cutoff_cascade(float cutoff, float rate)
{
    cema_cascade_t cascade;
    for (int i = 0; i < cema_cascade_order; ++i)
        cascade.ema[i] = cema_cutoff(cutoff / sqrt(rootn(2.0f, cema_cascade_order) - 1.0f), rate);
    return cascade;
}

static inline float filter(ema_t *ema, float input)
{
    return ema->prev = ema->a * input + (1.0f - ema->a) * ema->prev;
}

static inline complex_t __attribute__((overloadable)) cfilter(cema_t *ema, complex_t input)
{
    return ema->prev = ema->a * input + (1.0f - ema->a) * ema->prev;
}

static complex_t __attribute__((overloadable)) cfilter(cema_cascade_t *cascade, complex_t input)
{
    for (int i = 0; i < cema_cascade_order; ++i)
        input = cfilter(cascade->ema + i, input);
    return input;
}

#endif