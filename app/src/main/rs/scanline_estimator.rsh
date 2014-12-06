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

#ifndef SCANLINE_ESTIMATOR_RSH
#define SCANLINE_ESTIMATOR_RSH

#include "constants.rsh"
#include "state.rsh"

static const float ema_estimator_a = 0.7f;
static float robot36_estimator(int length)
{
    static ema_t variance = { 0.0f, ema_estimator_a };
    float deviation = length - robot36_scanline_length;
    return filter(&variance, deviation * deviation);
}
static float robot72_estimator(int length)
{
    static ema_t variance = { 0.0f, ema_estimator_a };
    float deviation = length - robot72_scanline_length;
    return filter(&variance, deviation * deviation);
}
static float martin1_estimator(int length)
{
    static ema_t variance = { 0.0f, ema_estimator_a };
    float deviation = length - martin1_scanline_length;
    return filter(&variance, deviation * deviation);
}
static float martin2_estimator(int length)
{
    static ema_t variance = { 0.0f, ema_estimator_a };
    float deviation = length - martin2_scanline_length;
    return filter(&variance, deviation * deviation);
}
static float scottie1_estimator(int length)
{
    static ema_t variance = { 0.0f, ema_estimator_a };
    float deviation = length - scottie1_scanline_length;
    return filter(&variance, deviation * deviation);
}
static float scottie2_estimator(int length)
{
    static ema_t variance = { 0.0f, ema_estimator_a };
    float deviation = length - scottie2_scanline_length;
    return filter(&variance, deviation * deviation);
}
static float scottieDX_estimator(int length)
{
    static ema_t variance = { 0.0f, ema_estimator_a };
    float deviation = length - scottieDX_scanline_length;
    return filter(&variance, deviation * deviation);
}

static int scanline_estimator(int sync_level)
{
    static scanline_counter, sync_counter;

    int sync_pulse = !sync_level && sync_counter >= minimum_sync_length;
    sync_counter = sync_level ? sync_counter + 1 : 0;

    if (!sync_pulse && scanline_counter < buffer_length) {
        ++scanline_counter;
        return -1;
    }

    float robot36_var = robot36_estimator(scanline_counter);
    float robot72_var = robot72_estimator(scanline_counter);
    float martin1_var = martin1_estimator(scanline_counter);
    float martin2_var = martin2_estimator(scanline_counter);
    float scottie1_var = scottie1_estimator(scanline_counter);
    float scottie2_var = scottie2_estimator(scanline_counter);
    float scottieDX_var = scottieDX_estimator(scanline_counter);
    scanline_counter = 0;

    float min_var = min(min(min(robot36_var, robot72_var),
        min(martin1_var, martin2_var)),
        min(min(scottie1_var, scottie2_var), scottieDX_var));

    if (min_var > maximum_variance)
        return -1;
    else if (min_var == robot36_var)
        return mode_robot36;
    else if (min_var == robot72_var)
        return mode_robot72;
    else if (min_var == martin1_var)
        return mode_martin1;
    else if (min_var == martin2_var)
        return mode_martin2;
    else if (min_var == scottie1_var)
        return mode_scottie1;
    else if (min_var == scottie2_var)
        return mode_scottie2;
    else if (min_var == scottieDX_var)
        return mode_scottieDX;
    return -1;
}

#endif