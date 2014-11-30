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

#ifndef COMPLEX_RSH
#define COMPLEX_RSH

typedef float2 complex_t;
static inline complex_t complex(float a, float b) { return (complex_t){ a, b }; }
static inline float cabs(complex_t z) { return length(z); }
static inline float carg(complex_t z) { return atan2(z[1], z[0]); }
static inline complex_t cexp(complex_t z)
{
    return complex(exp(z[0]) * cos(z[1]), exp(z[0]) * sin(z[1]));
}
static inline complex_t cmul(complex_t a, complex_t b)
{
    return complex(a[0] * b[0] - a[1] * b[1], a[0] * b[1] + a[1] * b[0]);
}
static inline complex_t conj(complex_t z) { return complex(z[0], -z[1]); }
static inline complex_t cdiv(complex_t a, complex_t b) { return cmul(a, conj(b)) / dot(b, b); }

#endif