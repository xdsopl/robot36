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

#ifndef RADIX2_RSH
#define RADIX2_RSH

#include "complex.rsh"

static inline void dft2(complex_t *out0, complex_t *out1, complex_t in0, complex_t in1)
{
        *out0 = in0 + in1;
        *out1 = in0 - in1;
}

static inline void fwd4(complex_t *out0, complex_t *out1, complex_t *out2, complex_t *out3,
                float in0, float in1, float in2, float in3)
{
        float a = in0 + in2;
        float b = in0 - in2;
        float c = in1 + in3;
        *out0 = complex(a + c, 0.0f);
        *out1 = complex(b, in3 - in1);
        *out2 = complex(a - c, 0.0f);
        *out3 = complex(b, in1 - in3);
}

#include "radix2_generated.rsh"

#endif