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

#ifndef FMD_RSH
#define FMD_RSH

#include "complex.rsh"

typedef struct {
    complex_t prev;
    float scale;
} fmd_t;

static fmd_t fmd(float bandwidth, float rate)
{
    return (fmd_t){ complex(0.0f, 0.0f), rate / (M_PI * bandwidth) };
}

static float demodulate(fmd_t *fmd, complex_t baseband)
{
#if 0
    float phase = carg(cdiv(baseband, fmd->prev));
#else
    // good enough, as we are working at the sampling rate
    float phase = (fmd->prev[0] * baseband[1] - baseband[0] * fmd->prev[1]) / dot(baseband, baseband);
#endif
    fmd->prev = baseband;
    return clamp(fmd->scale * phase, -1.0f, 1.0f);
}

#endif