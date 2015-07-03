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
#include "radix2_generated.rsh"

static void radix2(complex_t *out, float *in, int N, int S, int L)
{
    // we only need 4 <= N forward FFTs
    if (N == 4) {
        float in0 = in[0];
        float in1 = in[S];
        float in2 = in[2 * S];
        float in3 = in[3 * S];
        float a = in0 + in2;
        float b = in0 - in2;
        float c = in1 + in3;
        out[0] = complex(a + c, 0.0f);
        out[1] = complex(b, in3 - in1);
        out[2] = complex(a - c, 0.0f);
        out[3] = complex(b, in1 - in3);
        return;
    }
    radix2(out, in, N / 2, 2 * S, L + 1);
    radix2(out + N / 2, in + S, N / 2, 2 * S, L + 1);
    for (int k = 0; k < N / 2; ++k) {
        int ke = k;
        int ko = k + N / 2;
        complex_t w = radix2_z[k << L];
        complex_t even = out[ke];
        complex_t odd = cmul(w, out[ko]);
        out[ke] = even + odd;
        out[ko] = even - odd;
    }
}

#endif