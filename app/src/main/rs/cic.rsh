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

#ifndef CIC_RSH
#define CIC_RSH

typedef struct {
    int prev_comb, prev_int;
} cic_t;

static const int cic_cascade_order = 5;
typedef struct {
    cic_t cic[cic_cascade_order];
} cic_cascade_t;

static int cic_comb(cic_t *cic, int input)
{
    int output = input - cic->prev_comb;
    cic->prev_comb = input;
    return output;
}

static int cic_int(cic_t *cic, int input)
{
    return cic->prev_int = input + cic->prev_int;
}

static int cic_comb_cascade(cic_cascade_t *cascade, int input)
{
    for (int i = 0; i < cic_cascade_order; ++i)
        input = cic_comb(cascade->cic + i, input);
    return input;
}

static int cic_int_cascade(cic_cascade_t *cascade, int input)
{
    for (int i = 0; i < cic_cascade_order; ++i)
        input = cic_int(cascade->cic + i, input);
    return input;
}

#endif