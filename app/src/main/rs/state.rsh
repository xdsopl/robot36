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

#ifndef STATE_RSH
#define STATE_RSH

static ema_t avg_power;
static ddc_t cnt_ddc, dat_ddc;
static fmd_t cnt_fmd, dat_fmd;
static pulse_t sync_pulse_detector, start_bit_detector;
static pulse_t sync_pulse_detector_5ms, sync_pulse_detector_9ms, sync_pulse_detector_20ms;
static int disable_analyzer;
static int automatic_mode_detection;
static int debug_mode;
static int current_decoder;
static int user_blur, blur_power, free_running;
static int sample_rate, sync_pos, prev_sync_pos;
static int maximum_variance, maximum_absolute_deviaton;
static int scanline_length, minimum_length, maximum_length;
static int vis_length, bit_length, ssb_length;
static int buffer_length, buffer_mask, buffer_pos;
static int buffer_cleared;
static int bitmap_width, bitmap_height;
static int maximum_width, maximum_height;
static int freerun_height;
static int spectrum_width, spectrum_height;
static int spectrogram_width, spectrogram_height;
static int vpos, hpos;
static int seperator_counter, seperator_length;
static int u_sep_begin, u_sep_end, v_sep_begin, v_sep_end;
static int y_even_begin, y_even_end, y_odd_begin, y_odd_end;
static int y_begin, y_end, u_begin, u_end, v_begin, v_end;
static int r_begin, r_end, b_begin, b_end, g_begin, g_end;
static int robot36_scanline_length;
static int robot72_scanline_length;
static int martin1_scanline_length;
static int martin2_scanline_length;
static int scottie1_scanline_length;
static int scottie2_scanline_length;
static int scottieDX_scanline_length;
static int wraaseSC2_180_scanline_length;
static int pd50_scanline_length;
static int pd90_scanline_length;
static int pd120_scanline_length;
static int pd160_scanline_length;
static int pd180_scanline_length;
static int pd240_scanline_length;
static int pd290_scanline_length;

#endif