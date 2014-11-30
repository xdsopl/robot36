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

void debug_sync()
{
    save_cnt = 1;
    save_dat = 0;
    mode = mode_raw;
    sync_length = minimum_sync_length;
    maximum_length = buffer_length;
    scanline_length = maximum_length;
}
void debug_image()
{
    save_dat = 1;
    save_cnt = 0;
    mode = mode_raw;
    sync_length = minimum_sync_length;
    maximum_length = buffer_length;
    scanline_length = maximum_length;
}
void debug_both()
{
    save_cnt = 1;
    save_dat = 1;
    mode = mode_raw;
    sync_length = minimum_sync_length;
    maximum_length = buffer_length;
    scanline_length = maximum_length;
}
void robot36_mode()
{
    save_dat = 1;
    save_cnt = 0;
    mode = mode_robot36;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_len = 0.009f;
    const float sync_porch_len = 0.003f;
    const float sep_porch_len = 0.0015f;
    const float y_scan_len = 0.088f;
    const float u_scan_len = 0.044f;
    const float v_scan_len = 0.044f;
    const float seperator_len = 0.0045f;
    seperator_length = seperator_len * sample_rate;
    sync_length = tolerance * sync_len * sample_rate;
    y_begin = (sync_porch_len - settling_time) * sample_rate;
    y_end = y_begin + y_scan_len * sample_rate;
    u_sep_begin = y_end;
    u_sep_end = u_sep_begin + seperator_len * sample_rate;
    u_begin = u_sep_end + sep_porch_len * sample_rate;
    u_end = u_begin + u_scan_len * sample_rate;
    v_sep_begin = u_sep_begin;
    v_sep_end = u_sep_end;
    v_begin = v_sep_end + sep_porch_len * sample_rate;
    v_end = v_begin + v_scan_len * sample_rate;
    scanline_length = robot36_scanline_length;
    maximum_length = scanline_length + sync_porch_len * sample_rate;
}
void robot72_mode()
{
    save_dat = 1;
    save_cnt = 0;
    mode = mode_robot72;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_len = 0.009f;
    const float sync_porch_len = 0.003f;
    const float sep_porch_len = 0.0015f;
    const float y_scan_len = 0.138f;
    const float u_scan_len = 0.069f;
    const float v_scan_len = 0.069f;
    const float seperator_len = 0.0045f;
    seperator_length = seperator_len * sample_rate;
    sync_length = tolerance * sync_len * sample_rate;
    y_begin = (sync_porch_len - settling_time) * sample_rate;
    y_end = y_begin + y_scan_len * sample_rate;
    u_sep_begin = y_end;
    u_sep_end = u_sep_begin + seperator_len * sample_rate;
    u_begin = u_sep_end + sep_porch_len * sample_rate;
    u_end = u_begin + u_scan_len * sample_rate;
    v_sep_begin = u_end;
    v_sep_end = v_sep_begin + seperator_len * sample_rate;
    v_begin = v_sep_end + sep_porch_len * sample_rate;
    v_end = v_begin + v_scan_len * sample_rate;
    scanline_length = robot72_scanline_length;
    maximum_length = scanline_length + sync_porch_len * sample_rate;
}
void martin1_mode()
{
    save_cnt = 0;
    save_dat = 1;
    mode = mode_martin1;
    const float tolerance = 0.5f;
    const float sync_len = 0.004862f;
    const float sync_porch_len = 0.000572f;
    const float r_scan_len = 0.146432f;
    const float g_scan_len = 0.146432f;
    const float b_scan_len = 0.146432f;
    const float seperator_len = 0.000572f;
    seperator_length = seperator_len * sample_rate;
    sync_length = tolerance * sync_len * sample_rate;
    g_begin = 0;
    g_end = g_begin + g_scan_len * sample_rate;
    b_begin = g_end + seperator_len * sample_rate;
    b_end = b_begin + b_scan_len * sample_rate;
    r_begin = b_end + seperator_len * sample_rate;
    r_end = r_begin + r_scan_len * sample_rate;
    scanline_length = martin1_scanline_length;
    maximum_length = scanline_length + sync_porch_len * sample_rate;
}
void martin2_mode()
{
    save_cnt = 0;
    save_dat = 1;
    mode = mode_martin2;
    const float tolerance = 0.5f;
    const float sync_len = 0.004862f;
    const float sync_porch_len = 0.000572f;
    const float r_scan_len = 0.073216f;
    const float g_scan_len = 0.073216f;
    const float b_scan_len = 0.073216f;
    const float seperator_len = 0.000572f;
    seperator_length = seperator_len * sample_rate;
    sync_length = tolerance * sync_len * sample_rate;
    g_begin = 0;
    g_end = g_begin + g_scan_len * sample_rate;
    b_begin = g_end + seperator_len * sample_rate;
    b_end = b_begin + b_scan_len * sample_rate;
    r_begin = b_end + seperator_len * sample_rate;
    r_end = r_begin + r_scan_len * sample_rate;
    scanline_length = martin2_scanline_length;
    maximum_length = scanline_length + sync_porch_len * sample_rate;
}
void scottie1_mode()
{
    save_cnt = 0;
    save_dat = 1;
    mode = mode_scottie1;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_len = 0.009f;
    const float sync_porch_len = 0.0015f;
    const float r_scan_len = 0.138240f;
    const float g_scan_len = 0.138240f;
    const float b_scan_len = 0.138240f;
    const float seperator_len = 0.0015f;
    seperator_length = seperator_len * sample_rate;
    sync_length = tolerance * sync_len * sample_rate;
    r_begin = (sync_porch_len - settling_time) * sample_rate;
    r_end = r_begin + r_scan_len * sample_rate;
    g_begin = r_end + seperator_len * sample_rate;
    g_end = g_begin + g_scan_len * sample_rate;
    b_begin = g_end + seperator_len * sample_rate;
    b_end = b_begin + b_scan_len * sample_rate;
    scanline_length = scottie1_scanline_length;
    maximum_length = scanline_length + sync_porch_len * sample_rate;
}
void scottie2_mode()
{
    save_cnt = 0;
    save_dat = 1;
    mode = mode_scottie2;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_len = 0.009f;
    const float sync_porch_len = 0.0015f;
    const float r_scan_len = 0.088064f;
    const float g_scan_len = 0.088064f;
    const float b_scan_len = 0.088064f;
    const float seperator_len = 0.0015f;
    seperator_length = seperator_len * sample_rate;
    sync_length = tolerance * sync_len * sample_rate;
    r_begin = (sync_porch_len - settling_time) * sample_rate;
    r_end = r_begin + r_scan_len * sample_rate;
    g_begin = r_end + seperator_len * sample_rate;
    g_end = g_begin + g_scan_len * sample_rate;
    b_begin = g_end + seperator_len * sample_rate;
    b_end = b_begin + b_scan_len * sample_rate;
    scanline_length = scottie2_scanline_length;
    maximum_length = scanline_length + sync_porch_len * sample_rate;
}
void scottieDX_mode()
{
    save_cnt = 0;
    save_dat = 1;
    mode = mode_scottieDX;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_len = 0.009f;
    const float sync_porch_len = 0.0015f;
    const float r_scan_len = 0.3456f;
    const float g_scan_len = 0.3456f;
    const float b_scan_len = 0.3456f;
    const float seperator_len = 0.0015f;
    seperator_length = seperator_len * sample_rate;
    sync_length = tolerance * sync_len * sample_rate;
    r_begin = (sync_porch_len - settling_time) * sample_rate;
    r_end = r_begin + r_scan_len * sample_rate;
    g_begin = r_end + seperator_len * sample_rate;
    g_end = g_begin + g_scan_len * sample_rate;
    b_begin = g_end + seperator_len * sample_rate;
    b_end = b_begin + b_scan_len * sample_rate;
    scanline_length = scottieDX_scanline_length;
    maximum_length = scanline_length + sync_porch_len * sample_rate;
}

static void switch_mode(int new_mode)
{
    if (new_mode == mode)
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
        default:
            return;
    }
}

#endif