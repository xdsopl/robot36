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

short *audio_buffer;
uchar *value_buffer;
uchar4 *pixel_buffer;

static inline uchar4 rgb(uchar r, uchar g, uchar b) { return (uchar4){ b, g, r, 255 }; }

static inline float2 complex(float a, float b) { return (float2){ a, b }; }
static inline float cabs(float2 z) { return length(z); }
static inline float carg(float2 z) { return atan2(z[1], z[0]); }
static inline float2 cexp(float2 z)
{
    return complex(exp(z[0]) * cos(z[1]), exp(z[0]) * sin(z[1]));
}
static inline float2 cmul(float2 a, float2 b)
{
    return complex(a[0] * b[0] - a[1] * b[1], a[0] * b[1] + a[1] * b[0]);
}
static inline float2 conj(float2 z) { return complex(z[0], -z[1]); }
static inline float2 cdiv(float2 a, float2 b) { return cmul(a, conj(b)) / dot(b, b); }

static float ema_a(float cutoff, float rate, int order)
{
    float fc = cutoff / sqrt(rootn(2.0f, order) - 1.0f);
    float RC = 1.0f / (2.0f * M_PI * fc);
    float dt = 1.0f / rate;
    return dt / (RC + dt);
}

static const float ema_estimator_a = 0.7f;
static int robot36_scanline_length;
static float robot36_estimator(int length)
{
    static float variance;
    float deviation = robot36_scanline_length - length;
    return variance = ema_estimator_a * deviation * deviation + (1.0f - ema_estimator_a) * variance;
}
static int robot72_scanline_length;
static float robot72_estimator(int length)
{
    static float variance;
    float deviation = robot72_scanline_length - length;
    return variance = ema_estimator_a * deviation * deviation + (1.0f - ema_estimator_a) * variance;
}
static int martin1_scanline_length;
static float martin1_estimator(int length)
{
    static float variance;
    float deviation = martin1_scanline_length - length;
    return variance = ema_estimator_a * deviation * deviation + (1.0f - ema_estimator_a) * variance;
}
static int martin2_scanline_length;
static float martin2_estimator(int length)
{
    static float variance;
    float deviation = martin2_scanline_length - length;
    return variance = ema_estimator_a * deviation * deviation + (1.0f - ema_estimator_a) * variance;
}
static int scottie1_scanline_length;
static float scottie1_estimator(int length)
{
    static float variance;
    float deviation = scottie1_scanline_length - length;
    return variance = ema_estimator_a * deviation * deviation + (1.0f - ema_estimator_a) * variance;
}
static int scottie2_scanline_length;
static float scottie2_estimator(int length)
{
    static float variance;
    float deviation = scottie2_scanline_length - length;
    return variance = ema_estimator_a * deviation * deviation + (1.0f - ema_estimator_a) * variance;
}
static int scottieDX_scanline_length;
static float scottieDX_estimator(int length)
{
    static float variance;
    float deviation = scottieDX_scanline_length - length;
    return variance = ema_estimator_a * deviation * deviation + (1.0f - ema_estimator_a) * variance;
}

static float ema_power_a;
static float avg_power(float input)
{
    static float output;
    return output = ema_power_a * input + (1.0f - ema_power_a) * output;
}

static float ema_leader_a;
static float leader_lowpass(float input)
{
    static float output;
    return output = ema_leader_a * input + (1.0f - ema_leader_a) * output;
}

static const int filter_order = 11;
static float ema_cnt_a;
static float2 cnt_lowpass(float2 input)
{
    static float2 output[filter_order];
    for (int i = 0; i < filter_order; ++i)
        output[i] = input = ema_cnt_a * input + (1.0f - ema_cnt_a) * output[i];
    return input;
}

static float ema_dat_a;
static float2 dat_lowpass(float2 input)
{
    static float2 output[filter_order];
    for (int i = 0; i < filter_order; ++i)
        output[i] = input = ema_dat_a * input + (1.0f - ema_dat_a) * output[i];
    return input;
}

static float2 cnt_phasor, cnt_phasor_delta;
static float2 cnt_phasor_rotate()
{
    float2 prev = cnt_phasor;
    cnt_phasor = cmul(cnt_phasor, cnt_phasor_delta);
    cnt_phasor = normalize(cnt_phasor);
    return prev;
}

static float2 dat_phasor, dat_phasor_delta;
static float2 dat_phasor_rotate()
{
    float2 prev = dat_phasor;
    dat_phasor = cmul(dat_phasor, dat_phasor_delta);
    dat_phasor = normalize(dat_phasor);
    return prev;
}

static float2 cnt_ddc(float amp)
{
    return cnt_lowpass(amp * cnt_phasor_rotate());
}

static float2 dat_ddc(float amp)
{
    return dat_lowpass(amp * dat_phasor_rotate());
}

static float cnt_fmd_scale;
static float cnt_fmd(float2 baseband)
{
    static float2 prev;
    float phase = carg(cdiv(baseband, prev));
    prev = baseband;
    return clamp(cnt_fmd_scale * phase, -1.0f, 1.0f);
}

static float dat_fmd_scale;
static float dat_fmd(float2 baseband)
{
    static float2 prev;
    float phase = carg(cdiv(baseband, prev));
    prev = baseband;
    return clamp(dat_fmd_scale * phase, -1.0f, 1.0f);
}

static int sample_rate, mode, even_hpos;
static int maximum_variance, minimum_sync_length;
static int scanline_length, minimum_length, maximum_length;
static int vis_timeout, vis_length, bit_length;
static int break_timeout, break_length;
static int leader_timeout, leader_length;
static int first_leader_length, second_leader_length;
static int buffer_length, bitmap_width, bitmap_height;
static int sync_length, sync_counter, vpos, hpos;
static int save_cnt, save_dat, seperator_counter, seperator_length;
static int u_sep_begin, u_sep_end, v_sep_begin, v_sep_end;
static int y_begin, y_end, u_begin, u_end, v_begin, v_end;
static int r_begin, r_end, b_begin, b_end, g_begin, g_end;

static const int mode_raw = 0;
static const int mode_robot36 = 1;
static const int mode_robot72 = 2;
static const int mode_martin1 = 3;
static const int mode_martin2 = 4;
static const int mode_scottie1 = 5;
static const int mode_scottie2 = 6;
static const int mode_scottieDX = 7;

static const float robot36_scanline_seconds = 0.15f;
static const float robot72_scanline_seconds = 0.3f;
static const float martin1_scanline_seconds = 0.446446f;
static const float martin2_scanline_seconds = 0.226798f;
static const float scottie1_scanline_seconds = 0.42822f;
static const float scottie2_scanline_seconds = 0.277692f;
static const float scottieDX_scanline_seconds = 1.0503f;

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

void initialize(float rate, int length, int width, int height)
{
    sample_rate = rate;
    buffer_length = length;
    bitmap_width = width;
    bitmap_height = height;

    vpos = 0;
    even_hpos = hpos = 0;
    even_hpos = 0;
    sync_counter = 0;
    seperator_counter = 0;
    minimum_length = 0.05f * sample_rate;
    minimum_sync_length = 0.002f * sample_rate;

    robot36_scanline_length = robot36_scanline_seconds * sample_rate;
    robot72_scanline_length = robot72_scanline_seconds * sample_rate;
    martin1_scanline_length = martin1_scanline_seconds * sample_rate;
    martin2_scanline_length = martin2_scanline_seconds * sample_rate;
    scottie1_scanline_length = scottie1_scanline_seconds * sample_rate;
    scottie2_scanline_length = scottie2_scanline_seconds * sample_rate;
    scottieDX_scanline_length = scottieDX_scanline_seconds * sample_rate;

    const float pairwise_minimum_of_scanline_time_distances = 0.018226f;
    float deviation = pairwise_minimum_of_scanline_time_distances * sample_rate;
    maximum_variance = deviation * deviation;

    const float first_leader_tolerance = 0.3f;
    const float second_leader_tolerance = 0.9f;
    const float break_tolerance = 0.7f;
    const float leader_timeout_tolerance = 1.1f;
    const float break_timeout_tolerance = 1.8f;
    const float vis_timeout_tolerance = 1.01f;
    const float leader_len = 0.3f;
    const float break_len = 0.01f;
    const float vis_len = 0.3f;
    const float bit_len = 0.03f;
    first_leader_length = first_leader_tolerance * leader_len * sample_rate;
    second_leader_length = second_leader_tolerance * leader_len * sample_rate;
    leader_length = first_leader_length;
    break_length = break_tolerance * break_len * sample_rate;
    vis_length = vis_len * sample_rate;
    bit_length = bit_len * sample_rate;
    leader_timeout = leader_timeout_tolerance * leader_len * sample_rate;
    break_timeout = break_timeout_tolerance * break_len * sample_rate;
    vis_timeout = vis_timeout_tolerance * vis_len * sample_rate;

    const float dat_carrier = 1900.0f;
    const float cnt_carrier = 1200.0f;
    const float dat_bandwidth = 800.0f;
    const float cnt_bandwidth = 200.0f;

    ema_power_a = ema_a(10.0f, sample_rate, 1);
    ema_leader_a = ema_a(100.0f, sample_rate, 1);
    ema_cnt_a = ema_a(cnt_bandwidth, sample_rate, filter_order);
    ema_dat_a = ema_a(dat_bandwidth, sample_rate, filter_order);

    cnt_phasor = complex(0.0f, 1.0f);
    cnt_phasor_delta = cexp(complex(0.0f, -2.0f * M_PI * cnt_carrier / sample_rate));

    dat_phasor = complex(0.0f, 1.0f);
    dat_phasor_delta = cexp(complex(0.0f, -2.0f * M_PI * dat_carrier / sample_rate));

    cnt_fmd_scale = sample_rate / (M_PI * cnt_bandwidth);
    dat_fmd_scale = sample_rate / (M_PI * dat_bandwidth);

    robot36_mode();
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

static void scanline_estimator(int sync_level)
{
    static scanline_counter, sync_counter;

    int sync_pulse = !sync_level && sync_counter >= minimum_sync_length;
    sync_counter = sync_level ? sync_counter + 1 : 0;

    if (!sync_pulse) {
        ++scanline_counter;
        return;
    }

    float robot36_var = robot36_estimator(scanline_counter);
    float robot72_var = robot72_estimator(scanline_counter);
    float martin1_var = martin1_estimator(scanline_counter);
    float martin2_var = martin2_estimator(scanline_counter);
    float scottie1_var = scottie1_estimator(scanline_counter);
    float scottie2_var = scottie2_estimator(scanline_counter);
    float scottieDX_var = scottieDX_estimator(scanline_counter);
    scanline_counter = 0;

    float min_var = min(min(min(robot36_var, robot72_var),
        min(martin1_var, martin2_var)),
        min(min(scottie1_var, scottie2_var), scottieDX_var));

    int mode_estimated = 0;
    if (min_var > maximum_variance)
        return;
    else if (min_var == robot36_var)
        mode_estimated = mode_robot36;
    else if (min_var == robot72_var)
        mode_estimated = mode_robot72;
    else if (min_var == martin1_var)
        mode_estimated = mode_martin1;
    else if (min_var == martin2_var)
        mode_estimated = mode_martin2;
    else if (min_var == scottie1_var)
        mode_estimated = mode_scottie1;
    else if (min_var == scottie2_var)
        mode_estimated = mode_scottie2;
    else if (min_var == scottieDX_var)
        mode_estimated = mode_scottieDX;
    else
        return;

    if (mode_estimated == mode)
        return;

    switch (mode_estimated) {
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
    }
    reset();
}

static int calibration_detected(float dat_value, int cnt_active, int cnt_quantized)
{
    static int progress, countdown;
    static int leader_counter, break_counter;

    int dat_active = !cnt_active;
    progress = countdown ? progress : 0;
    countdown -= !!countdown;

    int leader_quantized = round(leader_lowpass(dat_value));
    int leader_level = dat_active && leader_quantized == 0;
    int leader_pulse = !leader_level && leader_counter >= leader_length;
    leader_counter = leader_level ? leader_counter + 1 : 0;
    if (leader_pulse) {
        if (progress == 2) {
            progress = 3;
            countdown = vis_timeout;
            leader_length = first_leader_length;
        } else {
            progress = 1;
            countdown = break_timeout;
            leader_length = second_leader_length;
        }
    }

    int break_level = cnt_active && cnt_quantized == 0;
    int break_pulse = !break_level && break_counter >= break_length;
    break_counter = break_level ? break_counter + 1 : 0;
    if (break_pulse) {
        if (progress == 1) {
            progress = 2;
            countdown = leader_timeout;
        } else if (progress < 3) {
            progress = 0;
            leader_length = first_leader_length;
        }
    }

    if (progress == 3) {
        static int bit_pos, vis_pos, vis_code;
        static int vis_counter, bit_counter;
        if (leader_pulse) {
            bit_pos = 0;
            vis_pos = bit_length;
            bit_counter = 0;
            vis_counter = 0;
        }
        if (++vis_counter < vis_pos) {
            bit_counter += cnt_quantized;
        } else {
            if (bit_pos == 0 && 2 * abs(bit_counter) < bit_length) {
                vis_code = 0;
            } else if (0 < bit_pos && bit_pos < 9 && 2 * abs(bit_counter) > bit_length) {
                int bit_val = bit_counter < 0 ? 1 : 0;
                vis_code |= bit_val << (bit_pos - 1);
                // sometimes stop bit is missing, finish up here.
                if (bit_pos == 8) {
                    progress = 0;
                    countdown = 0;
                    return vis_code;
                }
            } else if (bit_pos == 9 && 2 * abs(bit_counter) < bit_length) {
                progress = 0;
                countdown = 0;
                return vis_code;
            } else {
                progress = 0;
                countdown = 0;
                return -1;
            }
            ++bit_pos;
            bit_counter = cnt_quantized;
            vis_pos += bit_length;
        }
    }

    return -1;
}

void decode(int samples) {
    for (int sample = 0; sample < samples; ++sample) {
        float amp = audio_buffer[sample] / 32768.0f;
        float power = amp * amp;
        if (avg_power(power) < 0.0000001f)
            continue;

        float2 cnt_baseband = cnt_ddc(amp);
        float2 dat_baseband = dat_ddc(amp);

        float cnt_value = cnt_fmd(cnt_baseband);
        float dat_value = dat_fmd(dat_baseband);

        int cnt_active = cabs(dat_baseband) < cabs(cnt_baseband);
        uchar cnt_level = save_cnt ? 127.5f - 127.5f * cnt_value : 0.0f;
        uchar dat_level = save_dat ? 127.5f + 127.5f * dat_value : 0.0f;
        value_buffer[hpos + even_hpos] = cnt_active ? cnt_level : dat_level;

        int cnt_quantized = round(cnt_value);
        int dat_quantized = round(dat_value);

        switch (calibration_detected(dat_value, cnt_active, cnt_quantized)) {
            case 0x88:
                robot36_mode();
                reset();
                break;
            case 0x0c:
                robot72_mode();
                reset();
                break;
            case 0xac:
                martin1_mode();
                reset();
                break;
            case 0x28:
                martin2_mode();
                reset();
                break;
            case 0x3c:
                scottie1_mode();
                reset();
                break;
            case 0xb8:
                scottie2_mode();
                reset();
                break;
            case 0xcc:
                scottieDX_mode();
                reset();
                break;
        }

        int sync_level = cnt_active && cnt_quantized == 0;
        int sync_pulse = !sync_level && sync_counter >= sync_length;
        sync_counter = sync_level ? sync_counter + 1 : 0;

        scanline_estimator(sync_level);

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
