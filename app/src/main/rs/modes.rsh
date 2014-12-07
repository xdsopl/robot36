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

#ifndef MODES_RSH
#define MODES_RSH

#include "constants.rsh"
#include "state.rsh"
#include "exports.rsh"

void debug_sync()
{
    save_cnt = 1;
    save_dat = 0;
    *current_mode = mode_debug;
    current_decoder = decoder_raw;
    sync_length = minimum_sync_length;
    maximum_length = buffer_length;
    scanline_length = maximum_length;
}
void debug_image()
{
    save_dat = 1;
    save_cnt = 0;
    *current_mode = mode_debug;
    current_decoder = decoder_raw;
    sync_length = minimum_sync_length;
    maximum_length = buffer_length;
    scanline_length = maximum_length;
}
void debug_both()
{
    save_cnt = 1;
    save_dat = 1;
    *current_mode = mode_debug;
    current_decoder = decoder_raw;
    sync_length = minimum_sync_length;
    maximum_length = buffer_length;
    scanline_length = maximum_length;
}
void robot36_mode()
{
    save_dat = 1;
    save_cnt = 0;
    *current_mode = mode_robot36;
    current_decoder = decoder_robot36;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_seconds = 0.009f;
    const float sync_porch_seconds = 0.003f;
    const float sep_porch_seconds = 0.0015f;
    const float y_scan_seconds = 0.088f;
    const float uv_scan_seconds = 0.044f;
    const float seperator_seconds = 0.0045f;
    seperator_length = seperator_seconds * sample_rate;
    sync_length = tolerance * sync_seconds * sample_rate;
    y_begin = (sync_porch_seconds - settling_time) * sample_rate;
    y_end = y_begin + y_scan_seconds * sample_rate;
    u_sep_begin = v_sep_begin = y_end;
    u_sep_end = v_sep_end = u_sep_begin + seperator_seconds * sample_rate;
    u_begin = v_begin = u_sep_end + sep_porch_seconds * sample_rate;
    u_end = v_end = u_begin + uv_scan_seconds * sample_rate;
    scanline_length = robot36_scanline_length;
    maximum_length = scanline_length + sync_porch_seconds * sample_rate;
}
void robot72_mode()
{
    save_dat = 1;
    save_cnt = 0;
    *current_mode = mode_robot72;
    current_decoder = decoder_yuv;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_seconds = 0.009f;
    const float sync_porch_seconds = 0.003f;
    const float sep_porch_seconds = 0.0015f;
    const float y_scan_seconds = 0.138f;
    const float uv_scan_seconds = 0.069f;
    const float seperator_seconds = 0.0045f;
    seperator_length = seperator_seconds * sample_rate;
    sync_length = tolerance * sync_seconds * sample_rate;
    y_begin = (sync_porch_seconds - settling_time) * sample_rate;
    y_end = y_begin + y_scan_seconds * sample_rate;
    v_sep_begin = y_end;
    v_sep_end = v_sep_begin + seperator_seconds * sample_rate;
    v_begin = v_sep_end + sep_porch_seconds * sample_rate;
    v_end = v_begin + uv_scan_seconds * sample_rate;
    u_sep_begin = v_end;
    u_sep_end = u_sep_begin + seperator_seconds * sample_rate;
    u_begin = u_sep_end + sep_porch_seconds * sample_rate;
    u_end = u_begin + uv_scan_seconds * sample_rate;
    scanline_length = robot72_scanline_length;
    maximum_length = scanline_length + sync_porch_seconds * sample_rate;
}
void martin1_mode()
{
    save_cnt = 0;
    save_dat = 1;
    *current_mode = mode_martin1;
    current_decoder = decoder_rgb;
    const float tolerance = 0.5f;
    const float sync_seconds = 0.004862f;
    const float sync_porch_seconds = 0.000572f;
    const float r_scan_seconds = 0.146432f;
    const float g_scan_seconds = 0.146432f;
    const float b_scan_seconds = 0.146432f;
    const float seperator_seconds = 0.000572f;
    seperator_length = seperator_seconds * sample_rate;
    sync_length = tolerance * sync_seconds * sample_rate;
    g_begin = 0;
    g_end = g_begin + g_scan_seconds * sample_rate;
    b_begin = g_end + seperator_seconds * sample_rate;
    b_end = b_begin + b_scan_seconds * sample_rate;
    r_begin = b_end + seperator_seconds * sample_rate;
    r_end = r_begin + r_scan_seconds * sample_rate;
    scanline_length = martin1_scanline_length;
    maximum_length = scanline_length + sync_porch_seconds * sample_rate;
}
void martin2_mode()
{
    save_cnt = 0;
    save_dat = 1;
    *current_mode = mode_martin2;
    current_decoder = decoder_rgb;
    const float tolerance = 0.5f;
    const float sync_seconds = 0.004862f;
    const float sync_porch_seconds = 0.000572f;
    const float r_scan_seconds = 0.073216f;
    const float g_scan_seconds = 0.073216f;
    const float b_scan_seconds = 0.073216f;
    const float seperator_seconds = 0.000572f;
    seperator_length = seperator_seconds * sample_rate;
    sync_length = tolerance * sync_seconds * sample_rate;
    g_begin = 0;
    g_end = g_begin + g_scan_seconds * sample_rate;
    b_begin = g_end + seperator_seconds * sample_rate;
    b_end = b_begin + b_scan_seconds * sample_rate;
    r_begin = b_end + seperator_seconds * sample_rate;
    r_end = r_begin + r_scan_seconds * sample_rate;
    scanline_length = martin2_scanline_length;
    maximum_length = scanline_length + sync_porch_seconds * sample_rate;
}
void scottie1_mode()
{
    save_cnt = 0;
    save_dat = 1;
    *current_mode = mode_scottie1;
    current_decoder = decoder_rgb;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_seconds = 0.009f;
    const float sync_porch_seconds = 0.0015f;
    const float r_scan_seconds = 0.138240f;
    const float g_scan_seconds = 0.138240f;
    const float b_scan_seconds = 0.138240f;
    const float seperator_seconds = 0.0015f;
    seperator_length = seperator_seconds * sample_rate;
    sync_length = tolerance * sync_seconds * sample_rate;
    r_begin = (sync_porch_seconds - settling_time) * sample_rate;
    r_end = r_begin + r_scan_seconds * sample_rate;
    g_begin = r_end + seperator_seconds * sample_rate;
    g_end = g_begin + g_scan_seconds * sample_rate;
    b_begin = g_end + seperator_seconds * sample_rate;
    b_end = b_begin + b_scan_seconds * sample_rate;
    scanline_length = scottie1_scanline_length;
    maximum_length = scanline_length + sync_porch_seconds * sample_rate;
}
void scottie2_mode()
{
    save_cnt = 0;
    save_dat = 1;
    *current_mode = mode_scottie2;
    current_decoder = decoder_rgb;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_seconds = 0.009f;
    const float sync_porch_seconds = 0.0015f;
    const float r_scan_seconds = 0.088064f;
    const float g_scan_seconds = 0.088064f;
    const float b_scan_seconds = 0.088064f;
    const float seperator_seconds = 0.0015f;
    seperator_length = seperator_seconds * sample_rate;
    sync_length = tolerance * sync_seconds * sample_rate;
    r_begin = (sync_porch_seconds - settling_time) * sample_rate;
    r_end = r_begin + r_scan_seconds * sample_rate;
    g_begin = r_end + seperator_seconds * sample_rate;
    g_end = g_begin + g_scan_seconds * sample_rate;
    b_begin = g_end + seperator_seconds * sample_rate;
    b_end = b_begin + b_scan_seconds * sample_rate;
    scanline_length = scottie2_scanline_length;
    maximum_length = scanline_length + sync_porch_seconds * sample_rate;
}
void scottieDX_mode()
{
    save_cnt = 0;
    save_dat = 1;
    *current_mode = mode_scottieDX;
    current_decoder = decoder_rgb;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_seconds = 0.009f;
    const float sync_porch_seconds = 0.0015f;
    const float r_scan_seconds = 0.3456f;
    const float g_scan_seconds = 0.3456f;
    const float b_scan_seconds = 0.3456f;
    const float seperator_seconds = 0.0015f;
    seperator_length = seperator_seconds * sample_rate;
    sync_length = tolerance * sync_seconds * sample_rate;
    r_begin = (sync_porch_seconds - settling_time) * sample_rate;
    r_end = r_begin + r_scan_seconds * sample_rate;
    g_begin = r_end + seperator_seconds * sample_rate;
    g_end = g_begin + g_scan_seconds * sample_rate;
    b_begin = g_end + seperator_seconds * sample_rate;
    b_end = b_begin + b_scan_seconds * sample_rate;
    scanline_length = scottieDX_scanline_length;
    maximum_length = scanline_length + sync_porch_seconds * sample_rate;
}
void wrasseSC2_180_mode()
{
    save_cnt = 0;
    save_dat = 1;
    *current_mode = mode_wrasseSC2_180;
    current_decoder = decoder_rgb;
    const float tolerance = 0.5f;
    const float sync_seconds = 0.0055225f;
    const float sync_porch_seconds = 0.0005f;
    const float rgb_scan_seconds = 0.235f;
    sync_length = tolerance * sync_seconds * sample_rate;
    r_begin = 0;
    r_end = r_begin + rgb_scan_seconds * sample_rate;
    g_begin = r_end;
    g_end = g_begin + rgb_scan_seconds * sample_rate;
    b_begin = g_end;
    b_end = b_begin + rgb_scan_seconds * sample_rate;
    scanline_length = wrasseSC2_180_scanline_length;
    maximum_length = scanline_length + sync_porch_seconds * sample_rate;
}

static void switch_mode(int new_mode)
{
    if (new_mode == *current_mode)
        return;
    switch (new_mode) {
        case mode_robot36:
            robot36_mode();
            break;
        case mode_robot72:
            robot72_mode();
            break;
        case mode_martin1:
            martin1_mode();
            break;
        case mode_martin2:
            martin2_mode();
            break;
        case mode_scottie1:
            scottie1_mode();
            break;
        case mode_scottie2:
            scottie2_mode();
            break;
        case mode_scottieDX:
            scottieDX_mode();
            break;
        case mode_wrasseSC2_180:
            wrasseSC2_180_mode();
            break;
        default:
            return;
    }
}

#endif