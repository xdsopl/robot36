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

#ifndef SMA_RSH
#define SMA_RSH

static const int sma_N = 1 << 2;

typedef struct {
    int64_t prev_x[sma_N];
    int64_t prev_x2[sma_N];
    int64_t sum_x, sum_x2;
    int pos;
} sma_t;

static void sma_add(sma_t *sma, int sample)
{
    int64_t x = sample;
    sma->sum_x += x - sma->prev_x[sma->pos];
    sma->prev_x[sma->pos] = x;
    sma->sum_x2 += x * x - sma->prev_x2[sma->pos];
    sma->prev_x2[sma->pos] = x * x;
    sma->pos = (sma->pos + 1) & (sma_N - 1);
}

static int sma_mean(sma_t *sma)
{
    return sma->sum_x / sma_N;
}

static int sma_variance(sma_t *sma)
{
    int64_t var = (sma_N * sma->sum_x2 - sma->sum_x * sma->sum_x) / (sma_N * (sma_N - 1));
    if (var > 0x7fffffff)
        var = 0x7fffffff;
    return var;
}

#endif
