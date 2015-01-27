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
#include "stft.rsh"

void initialize(float rate, int length, int iw, int ih, int sw, int sh, int sgw, int sgh)
{
    sample_rate = rate;
    buffer_length = length;
    buffer_mask = length - 1;
    maximum_width = iw;
    maximum_height = ih;

    for (int i = 0; i < iw * ih; ++i)
        pixel_buffer[i] = rgb(0, 0, 0);

    init_analyzer(sw, sh, sgw, sgh);

    automatic_mode_detection = 1;
    debug_mode = 0;
    vpos = 0;
    hpos = 0;
    prev_sync_pos = sync_pos = 0;
    buffer_pos = 0;
    sync_counter = 0;
    seperator_counter = 0;
    buffer_cleared = 0;
    free_running = 1;
    minimum_sync_length = 0.002f * sample_rate;
    sync_buildup_length = round((sync_buildup_ms * sample_rate) / 1000.0f);

    robot36_scanline_length = round((robot36_scanline_ms * sample_rate) / 1000.0f);
    robot72_scanline_length = round((robot72_scanline_ms * sample_rate) / 1000.0f);
    martin1_scanline_length = round((martin1_scanline_ms * sample_rate) / 1000.0f);
    martin2_scanline_length = round((martin2_scanline_ms * sample_rate) / 1000.0f);
    scottie1_scanline_length = round((scottie1_scanline_ms * sample_rate) / 1000.0f);
    scottie2_scanline_length = round((scottie2_scanline_ms * sample_rate) / 1000.0f);
    scottieDX_scanline_length = round((scottieDX_scanline_ms * sample_rate) / 1000.0f);
    wrasseSC2_180_scanline_length = round((wrasseSC2_180_scanline_ms * sample_rate) / 1000.0f);

    const float pairwise_minimum_of_scanline_time_distances = 0.018226f;
    maximum_absolute_deviaton = 0.5f * pairwise_minimum_of_scanline_time_distances * sample_rate;
    maximum_variance = pown(0.0005f * sample_rate, 2);

    const float first_leader_tolerance = 0.3f;
    const float second_leader_tolerance = 0.9f;
    const float break_tolerance = 0.5f;
    const float leader_timeout_tolerance = 1.2f;
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