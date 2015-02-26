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
    static int sync_counter;
    int sync_pulse = !sync_level && sync_counter >= minimum_sync_length;
    int sync_len = sync_counter;
    sync_counter = sync_level ? sync_counter + 1 : 0;

    static int scanline_counter_5ms, scanline_counter_9ms, scanline_counter_20ms;
    ++scanline_counter_5ms;
    ++scanline_counter_9ms;
    ++scanline_counter_20ms;

    if (!sync_pulse)
        return -1;

    scanline_counter_5ms = scanline_counter_5ms >= buffer_length ? 0 : scanline_counter_5ms;
    scanline_counter_9ms = scanline_counter_9ms >= buffer_length ? 0 : scanline_counter_9ms;
    scanline_counter_20ms = scanline_counter_20ms >= buffer_length ? 0 : scanline_counter_20ms;

    if (sync_len >= min_sync_length_20ms) {
        static sma_t sma;
        sma_add(&sma, scanline_counter_20ms);
        scanline_counter_20ms = 0;

        if (sma_variance(&sma) > maximum_variance)
            return -1;

        int mean = sma_mean(&sma);

        int pd50_adev = abs(mean - pd50_scanline_length);
        int pd90_adev = abs(mean - pd90_scanline_length);
        int pd120_adev = abs(mean - pd120_scanline_length);
        int pd160_adev = abs(mean - pd160_scanline_length);
        int pd180_adev = abs(mean - pd180_scanline_length);
        int pd240_adev = abs(mean - pd240_scanline_length);
        int pd290_adev = abs(mean - pd290_scanline_length);

        int min_adev = min(
            min(
                min(pd50_adev, pd90_adev),
                min(pd120_adev, pd160_adev)
            ),
            min(
                min(pd180_adev, pd240_adev),
                pd290_adev
            )
        );

        if (min_adev > maximum_abs_dev_20ms)
            return -1;
        else if (min_adev == pd50_adev)
            return mode_pd50;
        else if (min_adev == pd90_adev)
            return mode_pd90;
        else if (min_adev == pd120_adev)
            return mode_pd120;
        else if (min_adev == pd160_adev)
            return mode_pd160;
        else if (min_adev == pd180_adev)
            return mode_pd180;
        else if (min_adev == pd240_adev)
            return mode_pd240;
        else if (min_adev == pd290_adev)
            return mode_pd290;
    } else if (sync_len >= min_sync_length_9ms) {
        static sma_t sma;
        sma_add(&sma, scanline_counter_9ms);
        scanline_counter_9ms = 0;

        if (sma_variance(&sma) > maximum_variance)
            return -1;

        int mean = sma_mean(&sma);

        int robot36_adev = abs(mean - robot36_scanline_length);
        int robot72_adev = abs(mean - robot72_scanline_length);
        int scottie1_adev = abs(mean - scottie1_scanline_length);
        int scottie2_adev = abs(mean - scottie2_scanline_length);
        int scottieDX_adev = abs(mean - scottieDX_scanline_length);

        int min_adev = min(
            min(
                min(robot36_adev, robot72_adev),
                min(scottie1_adev, scottie2_adev)
            ),
            scottieDX_adev
        );

        if (min_adev > maximum_abs_dev_9ms)
            return -1;
        else if (min_adev == robot36_adev)
            return mode_robot36;
        else if (min_adev == robot72_adev)
            return mode_robot72;
        else if (min_adev == scottie1_adev)
            return mode_scottie1;
        else if (min_adev == scottie2_adev)
            return mode_scottie2;
        else if (min_adev == scottieDX_adev)
            return mode_scottieDX;
    } else if (sync_len >= min_sync_length_5ms) {
        static sma_t sma;
        sma_add(&sma, scanline_counter_5ms);
        scanline_counter_5ms = 0;

        if (sma_variance(&sma) > maximum_variance)
            return -1;

        int mean = sma_mean(&sma);

        int martin1_adev = abs(mean - martin1_scanline_length);
        int martin2_adev = abs(mean - martin2_scanline_length);
        int wraaseSC2_180_adev = abs(mean - wraaseSC2_180_scanline_length);

        int min_adev = min(
            min(martin1_adev, martin2_adev),
            wraaseSC2_180_adev
        );

        if (min_adev > maximum_abs_dev_5ms)
            return -1;
        else if (min_adev == martin1_adev)
            return mode_martin1;
        else if (min_adev == martin2_adev)
            return mode_martin2;
        else if (min_adev == wraaseSC2_180_adev)
            return mode_wraaseSC2_180;
    }
    return -1;
}

#endif