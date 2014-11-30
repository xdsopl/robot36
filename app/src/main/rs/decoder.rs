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

#pragma version(1)
#pragma rs java_package_name(xdsopl.robot36)

#include "complex.rsh"
#include "ema.rsh"
#include "ddc.rsh"
#include "fmd.rsh"
#include "scanline_estimator.rsh"
#include "calibration_detector.rsh"
#include "initialization.rsh"
#include "modes.rsh"
#include "constants.rsh"
#include "state.rsh"

short *audio_buffer;
uchar *value_buffer;
uchar4 *pixel_buffer;

static inline uchar4 rgb(uchar r, uchar g, uchar b) { return (uchar4){ b, g, r, 255 }; }

static void reset()
{
    vpos = 0;
    hpos = 0;
    even_hpos = 0;
    seperator_counter = 0;
    sync_counter = sync_length;
    for (int i = 0; i < bitmap_width * bitmap_height; ++i)
        pixel_buffer[i] = rgb(0, 0, 0);
}

static void robot36_decoder()
{
    static prev_timeout;
    if (!prev_timeout && 2 * abs(seperator_counter) > seperator_length)
        vpos = (~1 & vpos) | (seperator_counter > 0);
    prev_timeout = hpos >= maximum_length;
    if (vpos & 1) {
        for (int i = 0; i < bitmap_width; ++i) {
            uchar even_y = value_buffer[i * (y_end-y_begin) / bitmap_width + y_begin];
            uchar u = value_buffer[i * (u_end-u_begin) / bitmap_width + u_begin];
            uchar odd_y = value_buffer[i * (y_end-y_begin) / bitmap_width + even_hpos + y_begin];
            uchar v = value_buffer[i * (v_end-v_begin) / bitmap_width + even_hpos + v_begin];
            pixel_buffer[bitmap_width * (vpos-1) + i] = rsYuvToRGBA_uchar4(even_y, u, v);
            pixel_buffer[bitmap_width * vpos + i] = rsYuvToRGBA_uchar4(odd_y, u, v);
        }
        if (prev_timeout)
            hpos -= scanline_length;
        else
            hpos = 0;
        even_hpos = 0;
    } else {
        if (prev_timeout) {
            even_hpos = scanline_length;
            hpos -= scanline_length;
        } else {
            even_hpos = hpos;
            hpos = 0;
        }
    }
}

static void yuv_decoder()
{
    for (int i = 0; i < bitmap_width; ++i) {
        uchar y = value_buffer[i * (y_end-y_begin) / bitmap_width + y_begin];
        uchar u = value_buffer[i * (u_end-u_begin) / bitmap_width + u_begin];
        uchar v = value_buffer[i * (v_end-v_begin) / bitmap_width + v_begin];
        pixel_buffer[bitmap_width * vpos + i] = rsYuvToRGBA_uchar4(y, u, v);
    }
    if (hpos >= maximum_length)
        hpos -= scanline_length;
    else
        hpos = 0;
    even_hpos = 0;
}

static void rgb_decoder()
{
    for (int i = 0; i < bitmap_width; ++i) {
        uchar r = value_buffer[i * (r_end-r_begin) / bitmap_width + r_begin];
        uchar g = value_buffer[i * (g_end-g_begin) / bitmap_width + g_begin];
        uchar b = value_buffer[i * (b_end-b_begin) / bitmap_width + b_begin];
        pixel_buffer[bitmap_width * vpos + i] = rgb(r, g, b);
    }
    if (hpos >= maximum_length)
        hpos -= scanline_length;
    else
        hpos = 0;
    even_hpos = 0;
}

static void raw_decoder()
{
    for (int i = 0; i < bitmap_width; ++i) {
        uchar value = value_buffer[i * hpos / bitmap_width];
        pixel_buffer[bitmap_width * vpos + i] = rgb(value, value, value);
    }
    even_hpos = hpos = 0;
}

void decode(int samples) {
    for (int sample = 0; sample < samples; ++sample) {
        float amp = audio_buffer[sample] / 32768.0f;
        float power = amp * amp;
        if (filter(&avg_power, power) < 0.0000001f)
            continue;

        complex_t cnt_baseband = convert(&cnt_ddc, amp);
        complex_t dat_baseband = convert(&dat_ddc, amp);

        float cnt_value = demodulate(&cnt_fmd, cnt_baseband);
        float dat_value = demodulate(&dat_fmd, dat_baseband);

        int cnt_active = cabs(dat_baseband) < cabs(cnt_baseband);
        uchar cnt_level = save_cnt ? 127.5f - 127.5f * cnt_value : 0.0f;
        uchar dat_level = save_dat ? 127.5f + 127.5f * dat_value : 0.0f;
        value_buffer[hpos + even_hpos] = cnt_active ? cnt_level : dat_level;

        int cnt_quantized = round(cnt_value);
        int dat_quantized = round(dat_value);

        int sync_level = cnt_active && cnt_quantized == 0;
        int sync_pulse = !sync_level && sync_counter >= sync_length;
        sync_counter = sync_level ? sync_counter + 1 : 0;

        if (mode != mode_raw) {
            int detected_mode = calibration_detector(dat_value, cnt_active, cnt_quantized);
            if (detected_mode >= 0)
                reset();
            switch_mode(detected_mode);
            int estimated_mode = scanline_estimator(sync_level);
            if (estimated_mode >= 0 && estimated_mode != mode)
                reset();
            switch_mode(estimated_mode);
        }

        int u_sep = u_sep_begin <= hpos && hpos < u_sep_end;
        int v_sep = v_sep_begin <= hpos && hpos < v_sep_end;
        seperator_counter += (u_sep || v_sep) ? dat_quantized : 0;

        if (++hpos >= maximum_length || sync_pulse) {
            if (hpos < minimum_length) {
                hpos = 0;
                even_hpos = 0;
                seperator_counter = 0;
                continue;
            }
            switch (mode) {
                case mode_robot36:
                    robot36_decoder();
                    break;
                case mode_robot72:
                    yuv_decoder();
                    break;
                case mode_martin1:
                case mode_martin2:
                case mode_scottie1:
                case mode_scottie2:
                case mode_scottieDX:
                    rgb_decoder();
                    break;
                default:
                    raw_decoder();
            }
            if (++vpos >= bitmap_height)
                vpos = 0;
            seperator_counter = 0;
        }
    }
}
