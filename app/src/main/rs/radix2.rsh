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
    if (1 == N) {
        out[0] = complex(in[0], 0.0f);
        return;
    } else if (2 == N) {
        out[0] = complex(in[0] + in[S], 0.0f);
        out[1] = complex(in[0] - in[S], 0.0f);
        return;
    } else if (4 == N) {
        complex_t w = radix2_z[1 << L];
        out[0] = complex(in[0] + in[S] + in[2 * S] + in[3 * S], 0.0f);
        out[1] = complex(in[0] - in[2 * S], 0.0f) + w * (in[S] - in[3 * S]);
        out[2] = complex(in[0] - in[S] + in[2 * S] - in[3 * S], 0.0f);
        out[3] = complex(in[0] - in[2 * S], 0.0f) + w * (in[3 * S] - in[S]);
        return;
    }
    radix2(out, in, N / 2, 2 * S, L + 1);
    radix2(out + N / 2, in + S, N / 2, 2 * S, L + 1);
    for (int k = 0; k < N / 2; ++k) {
        int ke = k;
        int ko = k + N / 2;
        complex_t even = out[ke];
        complex_t odd = out[ko];
        complex_t w = radix2_z[k << L];
        out[ke] = even + cmul(w, odd);
        out[ko] = even - cmul(w, odd);
    }
}

#endif