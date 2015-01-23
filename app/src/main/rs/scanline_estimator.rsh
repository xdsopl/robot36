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
#include "sma.rsh"

static int scanline_estimator(int sync_level)
{
    static scanline_counter, sync_counter;

    int sync_pulse = !sync_level && sync_counter >= minimum_sync_length;
    sync_counter = sync_level ? sync_counter + 1 : 0;

    if (!sync_pulse) {
        ++scanline_counter;
        return -1;
    }

    if (scanline_counter >= buffer_length)
        scanline_counter = 0;

    static sma_t sma;
    sma_add(&sma, scanline_counter);
    scanline_counter = 0;

    if (sma_variance(&sma) > maximum_variance)
        return -1;

    int mean = sma_mean(&sma);

    int robot36_adev = abs(mean - robot36_scanline_length);
    int robot72_adev = abs(mean - robot72_scanline_length);
    int martin1_adev = abs(mean - martin1_scanline_length);
    int martin2_adev = abs(mean - martin2_scanline_length);
    int scottie1_adev = abs(mean - scottie1_scanline_length);
    int scottie2_adev = abs(mean - scottie2_scanline_length);
    int scottieDX_adev = abs(mean - scottieDX_scanline_length);
    int wrasseSC2_180_adev = abs(mean - wrasseSC2_180_scanline_length);

    int min_adev = min(
        min(
            min(robot36_adev, robot72_adev),
            min(martin1_adev, martin2_adev)
        ), min(
            min(scottie1_adev, scottie2_adev),
            min(scottieDX_adev, wrasseSC2_180_adev)
        )
    );

    if (min_adev > maximum_absolute_deviaton)
        return -1;
    else if (min_adev == robot36_adev)
        return mode_robot36;
    else if (min_adev == robot72_adev)
        return mode_robot72;
    else if (min_adev == martin1_adev)
        return mode_martin1;
    else if (min_adev == martin2_adev)
        return mode_martin2;
    else if (min_adev == scottie1_adev)
        return mode_scottie1;
    else if (min_adev == scottie2_adev)
        return mode_scottie2;
    else if (min_adev == scottieDX_adev)
        return mode_scottieDX;
    else if (min_adev == wrasseSC2_180_adev)
        return mode_wrasseSC2_180;
    return -1;
}

#endif