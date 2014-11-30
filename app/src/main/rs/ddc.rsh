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

#ifndef DDC_RSH
#define DDC_RSH

#include "complex.rsh"
#include "ema.rsh"
#include "phasor.rsh"

typedef struct {
    phasor_t phasor;
    cema_cascade_t lowpass;
} ddc_t;

static ddc_t ddc(float carrier, float bandwidth, float rate)
{
    return (ddc_t){
        phasor(-carrier, rate),
        cema_cutoff_cascade(bandwidth, rate)
    };
}

static complex_t convert(ddc_t *ddc, float amp)
{
    return cfilter(&ddc->lowpass, amp * rotate(&ddc->phasor));
}

#endif