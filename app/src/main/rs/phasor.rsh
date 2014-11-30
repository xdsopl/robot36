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

#ifndef PHASOR_RSH
#define PHASOR_RSH

#include "complex.rsh"

typedef struct {
    complex_t phasor, delta;
} phasor_t;

static phasor_t phasor(float freq, float rate)
{
    return (phasor_t){
        complex(0.0f, 1.0f),
        cexp(complex(0.0f, 2.0f * M_PI * freq / rate))
    };
}

static inline complex_t rotate(phasor_t *phasor)
{
    complex_t prev = phasor->phasor;
    phasor->phasor = normalize(cmul(phasor->phasor, phasor->delta));
    return prev;
}

#endif