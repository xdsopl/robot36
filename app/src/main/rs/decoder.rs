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
#include "exports.rsh"
#include "blur.rsh"

static inline uchar4 rgb(uchar r, uchar g, uchar b) { return (uchar4){ b, g, r, 255 }; }
static inline uchar4 yuv(uchar y, uchar u, uchar v)
{
    uchar4 bgra = rsYuvToRGBA_uchar4(y, u, v);
    return rgb(bgra[0], bgra[1], bgra[2]);
}

static void reset_buffer()
{
    vpos = 0;
    hpos = 0;
    prev_hpos = 0;
    seperator_counter = 0;
    sync_counter = sync_length;
    buffer_cleared = 1;
    for (int i = 0; i < maximum_width * maximum_height; ++i)
        pixel_buffer[i] = rgb(0, 0, 0);
}

static void save_buffer()
{
    free_running = 1;
    if (!buffer_cleared)
        return;
    buffer_cleared = 0;
    *saved_height = bitmap_height;
    *saved_width = bitmap_width;
    for (int i = 0; i < bitmap_width * bitmap_height; ++i)
        saved_buffer[i] = pixel_buffer[i];
}

static void robot36_decoder()
{
    static prev_timeout;
    if (free_running && !prev_timeout && 2 * abs(seperator_counter) > seperator_length)
        vpos = (~1 & vpos) | (seperator_counter > 0);
    prev_timeout = hpos >= maximum_length;
    if (vpos & 1) {
        for (int i = 0; i < bitmap_width; ++i) {
            uchar even_y = value_blur(i, y_begin, y_end);
            uchar v = value_blur(i, v_begin, v_end);
            uchar odd_y = value_blur(i, prev_hpos + y_begin, prev_hpos + y_end);
            uchar u = value_blur(i, prev_hpos + u_begin, prev_hpos + u_end);
            pixel_buffer[bitmap_width * (vpos-1) + i] = yuv(even_y, u, v);
            pixel_buffer[bitmap_width * vpos + i] = yuv(odd_y, u, v);
        }
        if (prev_timeout)
            hpos -= scanline_length;
        else
            hpos = 0;
        prev_hpos = 0;
    } else {
        if (prev_timeout) {
            prev_hpos = scanline_length;
            hpos -= scanline_length;
        } else {
            prev_hpos = hpos;
            hpos = 0;
        }
    }
}

static void yuv_decoder()
{
    for (int i = 0; i < bitmap_width; ++i) {
        uchar y = value_blur(i, y_begin, y_end);
        uchar u = value_blur(i, u_begin, u_end);
        uchar v = value_blur(i, v_begin, v_end);
        pixel_buffer[bitmap_width * vpos + i] = yuv(y, u, v);
    }
    if (hpos >= maximum_length)
        hpos -= scanline_length;
    else
        hpos = 0;
    prev_hpos = 0;
}

static void rgb_decoder()
{
    for (int i = 0; i < bitmap_width; ++i) {
        uchar r = value_blur(i, r_begin, r_end);
        uchar g = value_blur(i, g_begin, g_end);
        uchar b = value_blur(i, b_begin, b_end);
        pixel_buffer[bitmap_width * vpos + i] = rgb(r, g, b);
    }
    if (hpos >= maximum_length)
        hpos -= scanline_length;
    else
        hpos = 0;
    prev_hpos = 0;
}

static void raw_decoder()
{
    for (int i = 0; i < bitmap_width; ++i) {
        uchar value = value_blur(i, 0, hpos);
        pixel_buffer[bitmap_width * vpos + i] = rgb(value, value, value);
    }
    prev_hpos = hpos = 0;
}

// don't you guys have anything better to do?
static void scottie_decoder()
{
    if (!prev_hpos) {
        for (int i = g_begin; i < g_end; ++i)
            value_buffer[i + b_begin - g_begin] = value_buffer[i];
        for (int i = r_begin; i < r_end; ++i)
            value_buffer[i + g_begin - r_begin] = value_buffer[i];
        prev_hpos = scanline_length;
        hpos = 0;
        // TODO: don't do this here ..
        vpos = vpos < 0 ? vpos : vpos - 1;
        return;
    }
    for (int i = 0; i < bitmap_width; ++i) {
        uchar r = value_blur(i, prev_hpos + r_begin, prev_hpos + r_end);
        uchar g = value_blur(i, g_begin, g_end);
        uchar b = value_blur(i, b_begin, b_end);
        pixel_buffer[bitmap_width * vpos + i] = rgb(r, g, b);
    }
    for (int i = 0; i < scanline_length; ++i)
        value_buffer[i] = value_buffer[i + prev_hpos];
    prev_hpos = scanline_length;
    hpos = 0;
}

void decode(int samples) {
    *saved_width = 0;
    *saved_height = 0;
    for (int sample = 0; sample < samples; ++sample) {
        float amp = audio_buffer[sample] / 32768.0f;
        float power = amp * amp;
        if (filter(&avg_power, power) < 0.0000001f)
            continue;

        complex_t cnt_baseband = convert(&cnt_ddc, amp);
        complex_t dat_baseband = convert(&dat_ddc, amp);

        float cnt_value = demodulate(&cnt_fmd, cnt_baseband);
        float dat_value = demodulate(&dat_fmd, dat_baseband);

        int cnt_active = cabs(dat_baseband) < 4.0f * cabs(cnt_baseband);
        int dat_active = cabs(cnt_baseband) < 4.0f * cabs(dat_baseband);
        uchar cnt_level = save_cnt && cnt_active ? 127.5f - 127.5f * cnt_value : 0.0f;
        uchar dat_level = save_dat && dat_active ? 127.5f + 127.5f * dat_value : 0.0f;
        value_buffer[hpos + prev_hpos] = cnt_level | dat_level;

        int cnt_quantized = round(cnt_value);
        int dat_quantized = round(dat_value);

        int sync_level = cnt_active && cnt_quantized == 0;
        int sync_pulse = !sync_level && sync_counter >= sync_length;
        sync_counter = sync_level ? sync_counter + 1 : 0;

        if (*current_mode != mode_debug) {
            int detected_mode = calibration_detector(dat_value, dat_active, cnt_active, cnt_quantized);
            if (detected_mode >= 0) {
                free_running = 0;
                reset_buffer();
                switch_mode(detected_mode);
            }
            int estimated_mode = scanline_estimator(sync_level);
            if (estimated_mode >= 0 && estimated_mode != *current_mode) {
                free_running = 1;
                reset_buffer();
                switch_mode(estimated_mode);
            }
        }

        int u_sep = u_sep_begin <= hpos && hpos < u_sep_end;
        int v_sep = v_sep_begin <= hpos && hpos < v_sep_end;
        seperator_counter += (u_sep || v_sep) ? dat_quantized : 0;

        if (++hpos >= maximum_length || sync_pulse) {
            if (hpos < minimum_length) {
                hpos = 0;
                prev_hpos = 0;
                seperator_counter = 0;
                continue;
            }
            switch (current_decoder) {
                case decoder_robot36:
                    robot36_decoder();
                    break;
                case decoder_yuv:
                    yuv_decoder();
                    break;
                case decoder_rgb:
                    rgb_decoder();
                    break;
                case decoder_scottie:
                    scottie_decoder();
                    break;
                default:
                    raw_decoder();
            }
            if (++vpos == bitmap_height)
                save_buffer();
            if (vpos >= maximum_height)
                vpos = 0;
            seperator_counter = 0;
        }
    }
}
