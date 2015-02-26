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
#include "stft.rsh"
#include "utils.rsh"

void reset_buffer()
{
    vpos = 0;
    hpos = 0;
    prev_sync_pos = sync_pos = 0;
    buffer_pos = 0;
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

static void robot36_decoder(int sync_timeout)
{
    static int prev_timeout, mismatch_counter, parity, latch;
    if (!prev_timeout && 2 * abs(seperator_counter) > seperator_length)
        mismatch_counter = parity ^ (seperator_counter > 0) ? mismatch_counter + 1 : 0;
    if ((free_running && mismatch_counter > 1) || mismatch_counter > 5) {
        parity ^= 1;
        mismatch_counter = 0;
    }
    if (!free_running && !vpos && !latch) {
        parity = 0;
        latch = 1;
        mismatch_counter = 0;
    }
    prev_timeout = sync_timeout;
    if (debug_mode) {
        for (int i = 0; i < bitmap_width; ++i) {
            uchar r, g, b;
            r = g = b = value_blur(i, prev_sync_pos, sync_pos);
            int pos = (i * (sync_pos - prev_sync_pos) + (sync_pos - prev_sync_pos) / 2) / bitmap_width;
            if (y_begin <= pos && pos < y_end)
                r = b = 0;
            if (parity) {
                if (v_begin <= pos && pos < v_end)
                    g = b = 0;
                if (u_sep_begin <= pos && pos < u_sep_end)
                    b = 128;
            } else {
                if (u_begin <= pos && pos < u_end)
                    r = g = 0;
                if (v_sep_begin <= pos && pos < v_sep_end)
                    r = g = 128;
            }
            pixel_buffer[bitmap_width * vpos + i] = rgb(r, g, b);
        }
        latch = 0;
        ++vpos;
    } else {
        static int even_sync_pos, odd_sync_pos;
        if (parity) {
            odd_sync_pos = prev_sync_pos;
            int even_vpos = vpos;
            int odd_vpos = (vpos + 1) % freerun_height;
            for (int i = 0; i < bitmap_width; ++i) {
                uchar even_y = value_blur(i, even_sync_pos + y_begin, even_sync_pos + y_end);
                uchar v = value_blur(i, even_sync_pos + v_begin, even_sync_pos + v_end);
                uchar odd_y = value_blur(i, odd_sync_pos + y_begin, odd_sync_pos + y_end);
                uchar u = value_blur(i, odd_sync_pos + u_begin, odd_sync_pos + u_end);
                pixel_buffer[bitmap_width * even_vpos + i] = yuv(even_y, u, v);
                pixel_buffer[bitmap_width * odd_vpos + i] = yuv(odd_y, u, v);
            }
            vpos += 2;
            latch = 0;
        } else {
            even_sync_pos = prev_sync_pos;
        }
    }
    parity ^= 1;
}

static void yuv_decoder()
{
    for (int i = 0; i < bitmap_width; ++i) {
        if (debug_mode) {
            uchar r, g, b;
            r = g = b = value_blur(i, prev_sync_pos, sync_pos);
            int pos = (i * (sync_pos - prev_sync_pos) + (sync_pos - prev_sync_pos) / 2) / bitmap_width;
            if (v_begin <= pos && pos < v_end)
                g = b = 0;
            if (y_begin <= pos && pos < y_end)
                r = b = 0;
            if (u_begin <= pos && pos < u_end)
                r = g = 0;
            if (u_sep_begin <= pos && pos < u_sep_end)
                b = 128;
            if (v_sep_begin <= pos && pos < v_sep_end)
                r = g = 128;
            pixel_buffer[bitmap_width * vpos + i] = rgb(r, g, b);
        } else {
            uchar y = value_blur(i, y_begin + prev_sync_pos, y_end + prev_sync_pos);
            uchar u = value_blur(i, u_begin + prev_sync_pos, u_end + prev_sync_pos);
            uchar v = value_blur(i, v_begin + prev_sync_pos, v_end + prev_sync_pos);
            pixel_buffer[bitmap_width * vpos + i] = yuv(y, u, v);
        }
    }
    ++vpos;
}

static void pd_decoder()
{
    if (debug_mode) {
        for (int i = 0; i < bitmap_width; ++i) {
            uchar r, g, b;
            r = g = b = value_blur(i, prev_sync_pos, sync_pos);
            int pos = (i * (sync_pos - prev_sync_pos) + (sync_pos - prev_sync_pos) / 2) / bitmap_width;
            if (v_begin <= pos && pos < v_end)
                g = b = 0;
            if ((y_even_begin <= pos && pos < y_even_end) || (y_odd_begin <= pos && pos < y_odd_end))
                r = b = 0;
            if (u_begin <= pos && pos < u_end)
                r = g = 0;
            pixel_buffer[bitmap_width * vpos + i] = rgb(r, g, b);
        }
        ++vpos;
    } else {
        int even_vpos = vpos;
        int odd_vpos = (vpos + 1) % freerun_height;
        for (int i = 0; i < bitmap_width; ++i) {
            uchar even_y = value_blur(i, y_even_begin + prev_sync_pos, y_even_end + prev_sync_pos);
            uchar u = value_blur(i, u_begin + prev_sync_pos, u_end + prev_sync_pos);
            uchar v = value_blur(i, v_begin + prev_sync_pos, v_end + prev_sync_pos);
            uchar odd_y = value_blur(i, y_odd_begin + prev_sync_pos, y_odd_end + prev_sync_pos);
            pixel_buffer[bitmap_width * even_vpos + i] = yuv(even_y, u, v);
            pixel_buffer[bitmap_width * odd_vpos + i] = yuv(odd_y, u, v);
        }
        vpos += 2;
    }
}

static void rgb_decoder()
{
    for (int i = 0; i < bitmap_width; ++i) {
        uchar r, g, b;
        if (debug_mode) {
            r = g = b = value_blur(i, prev_sync_pos, sync_pos);
            int pos = (i * (sync_pos - prev_sync_pos) + (sync_pos - prev_sync_pos) / 2) / bitmap_width;
            if (r_begin <= pos && pos < r_end)
                g = b = 0;
            if (g_begin <= pos && pos < g_end)
                r = b = 0;
            if (b_begin <= pos && pos < b_end)
                r = g = 0;
        } else {
            r = value_blur(i, r_begin + prev_sync_pos, r_end + prev_sync_pos);
            g = value_blur(i, g_begin + prev_sync_pos, g_end + prev_sync_pos);
            b = value_blur(i, b_begin + prev_sync_pos, b_end + prev_sync_pos);
        }
        pixel_buffer[bitmap_width * vpos + i] = rgb(r, g, b);
    }
    ++vpos;
}

static void raw_decoder()
{
    for (int i = 0; i < bitmap_width; ++i) {
        uchar value = value_blur(i, prev_sync_pos, sync_pos);
        pixel_buffer[bitmap_width * vpos + i] = rgb(value, value, value);
    }
    ++vpos;
}

// don't you guys have anything better to do?
static int scottie_extrawurst()
{
    static int first_sync = 1;
    if (!free_running && !vpos && first_sync) {
        first_sync = 0;
        hpos = buffer_pos - sync_pos;
        return 1;
    }
    first_sync = 1;
    return 0;
}
static void scottie_decoder()
{
    for (int i = 0; i < bitmap_width; ++i) {
        uchar r, g, b;
        if (debug_mode) {
            r = g = b = value_blur(i, g_begin + prev_sync_pos, r_end + prev_sync_pos);
            int pos = (i * (r_end - g_begin) + (r_end - g_begin) / 2) / bitmap_width + g_begin;
            if (r_begin <= pos && pos < r_end)
                g = b = 0;
            if (g_begin <= pos && pos < g_end)
                r = b = 0;
            if (b_begin <= pos && pos < b_end)
                r = g = 0;
        } else {
            r = value_blur(i, r_begin + prev_sync_pos, r_end + prev_sync_pos);
            g = value_blur(i, g_begin + prev_sync_pos, g_end + prev_sync_pos);
            b = value_blur(i, b_begin + prev_sync_pos, b_end + prev_sync_pos);
        }
        pixel_buffer[bitmap_width * vpos + i] = rgb(r, g, b);
    }
    ++vpos;
}

void decode(int samples) {
    *saved_width = 0;
    *saved_height = 0;
    for (int sample = 0; sample < samples; ++sample, ++buffer_pos) {
        int amp = audio_buffer[sample];
        float avg_pow = filter(&avg_power, amp * amp);
        float avg_amp = clamp(sqrt(2.0f * avg_pow), 1.0f, 32767.0f);
        *volume = avg_amp / 32767.0f;
        float norm_amp = (127 * amp) / avg_amp;

        spectrum_analyzer(norm_amp);

        complex_t cnt_baseband = convert(&cnt_ddc, norm_amp);
        complex_t dat_baseband = convert(&dat_ddc, norm_amp);

        float cnt_value = demodulate(&cnt_fmd, cnt_baseband);
        float dat_value = demodulate(&dat_fmd, dat_baseband);

        float cnt_pow = dot(cnt_baseband, cnt_baseband);
        float dat_pow = dot(dat_baseband, dat_baseband);

        int cnt_active = dat_pow < 4.0f * cnt_pow;
        int dat_active = cnt_pow < 4.0f * dat_pow;

        uchar cnt_level = debug_mode && cnt_active ? 127.5f - 127.5f * cnt_value : 0.0f;
        uchar dat_level = dat_active ? 127.5f + 127.5f * dat_value : 0.0f;
        value_buffer[buffer_pos & buffer_mask] = cnt_level | dat_level;

        int cnt_quantized = round(cnt_value);
        int dat_quantized = round(dat_value);

        int sync_level = cnt_active && cnt_quantized == 0;
        int sync_pulse = !sync_level && sync_counter >= sync_length;
        sync_counter = sync_level ? sync_counter + 1 : 0;
        if (sync_pulse) {
            prev_sync_pos = sync_pos;
            sync_pos = buffer_pos - sync_buildup_length;
        }

        static int reset_on_first_sync;
        if (automatic_mode_detection) {
            int detected_mode = calibration_detector(cnt_quantized);
            if (detected_mode >= 0) {
                //RS_DEBUG(detected_mode);
                reset_on_first_sync = 1;
                free_running = 0;
                reset_buffer();
                switch_mode(detected_mode);
            }
            int estimated_mode = scanline_estimator(sync_level);
            if (estimated_mode >= 0 && estimated_mode != *current_mode) {
                //RS_DEBUG(estimated_mode);
                free_running = 1;
                reset_buffer();
                switch_mode(estimated_mode);
            }
        }
        if (sync_pulse && reset_on_first_sync) {
            reset_on_first_sync = 0;
            hpos = buffer_pos - sync_pos;
            seperator_counter = 0;
            continue;
        }
        if (sync_pulse && current_decoder == decoder_scottie && scottie_extrawurst())
            continue;

        int u_sep = u_sep_begin <= hpos && hpos < u_sep_end;
        int v_sep = v_sep_begin <= hpos && hpos < v_sep_end;
        seperator_counter += (u_sep || v_sep) ? dat_quantized : 0;

        if (++hpos >= maximum_length || sync_pulse) {
            static int too_early_sync_counter;
            if (hpos < minimum_length) {
                if (++too_early_sync_counter <= 5)
                    continue;
                too_early_sync_counter = 0;
                hpos = buffer_pos - sync_pos;
                seperator_counter = 0;
                continue;
            }
            int sync_timeout;
            if (hpos >= maximum_length) {
                sync_timeout = 1;
                hpos -= scanline_length;
                prev_sync_pos = sync_pos;
                sync_pos += scanline_length;
            } else {
                sync_timeout = 0;
                too_early_sync_counter = 0;
                hpos = buffer_pos - sync_pos;
            }
            switch (current_decoder) {
                case decoder_robot36:
                    robot36_decoder(sync_timeout);
                    break;
                case decoder_pd:
                    pd_decoder();
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
            if (vpos >= bitmap_height)
                save_buffer();
            if (vpos >= freerun_height)
                vpos -= freerun_height;
            seperator_counter = 0;
        }
    }
}
