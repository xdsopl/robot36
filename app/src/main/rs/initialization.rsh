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

#ifndef INITIALIZATION_RSH
#define INITIALIZATION_RSH

#include "constants.rsh"
#include "state.rsh"
#include "modes.rsh"

void initialize(float rate, int length, int width, int height)
{
    sample_rate = rate;
    buffer_length = length;
    maximum_width = width;
    maximum_height = height;

    vpos = 0;
    even_hpos = hpos = 0;
    even_hpos = 0;
    sync_counter = 0;
    seperator_counter = 0;
    buffer_cleared = 0;
    minimum_length = 0.05f * sample_rate;
    minimum_sync_length = 0.002f * sample_rate;

    robot36_scanline_length = robot36_scanline_seconds * sample_rate;
    robot72_scanline_length = robot72_scanline_seconds * sample_rate;
    martin1_scanline_length = martin1_scanline_seconds * sample_rate;
    martin2_scanline_length = martin2_scanline_seconds * sample_rate;
    scottie1_scanline_length = scottie1_scanline_seconds * sample_rate;
    scottie2_scanline_length = scottie2_scanline_seconds * sample_rate;
    scottieDX_scanline_length = scottieDX_scanline_seconds * sample_rate;
    wrasseSC2_180_scanline_length = wrasseSC2_180_scanline_seconds * sample_rate;

    const float pairwise_minimum_of_scanline_time_distances = 0.018226f;
    float deviation = 0.5f * pairwise_minimum_of_scanline_time_distances * sample_rate;
    maximum_variance = deviation * deviation;

    const float first_leader_tolerance = 0.3f;
    const float second_leader_tolerance = 0.9f;
    const float break_tolerance = 0.7f;
    const float leader_timeout_tolerance = 1.1f;
    const float break_timeout_tolerance = 1.8f;
    const float vis_timeout_tolerance = 1.01f;
    const float leader_seconds = 0.3f;
    const float break_seconds = 0.01f;
    const float vis_seconds = 0.3f;
    const float bit_seconds = 0.03f;
    first_leader_length = first_leader_tolerance * leader_seconds * sample_rate;
    second_leader_length = second_leader_tolerance * leader_seconds * sample_rate;
    leader_length = first_leader_length;
    break_length = break_tolerance * break_seconds * sample_rate;
    vis_length = vis_seconds * sample_rate;
    bit_length = bit_seconds * sample_rate;
    leader_timeout = leader_timeout_tolerance * leader_seconds * sample_rate;
    break_timeout = break_timeout_tolerance * break_seconds * sample_rate;
    vis_timeout = vis_timeout_tolerance * vis_seconds * sample_rate;

    const float dat_carrier = 1900.0f;
    const float cnt_carrier = 1200.0f;
    const float dat_bandwidth = 800.0f;
    const float cnt_bandwidth = 200.0f;

    avg_power = ema_cutoff(10.0f, sample_rate);
    leader_lowpass = ema_cutoff(100.0f, sample_rate);

    cnt_ddc = ddc(cnt_carrier, cnt_bandwidth, sample_rate);
    dat_ddc = ddc(dat_carrier, dat_bandwidth, sample_rate);

    cnt_fmd = fmd(cnt_bandwidth, sample_rate);
    dat_fmd = fmd(dat_bandwidth, sample_rate);

    robot36_mode();
}

#endif