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
static float2 cnt_fmd_prev;
static float cnt_fmd(float2 baseband)
{
    float phase = carg(cdiv(baseband, cnt_fmd_prev));
    cnt_fmd_prev = baseband;
    return clamp(cnt_fmd_scale * phase, -1.0f, 1.0f);
}

static float dat_fmd_scale;
static float2 dat_fmd_prev;
static float dat_fmd(float2 baseband)
{
    float phase = carg(cdiv(baseband, dat_fmd_prev));
    dat_fmd_prev = baseband;
    return clamp(dat_fmd_scale * phase, -1.0f, 1.0f);
}

static int sample_rate, mode, even_hpos, minimum_length;
static int calibration_length, calibration_countdown;
static int leader_timeout, break_timeout, vis_timeout;
static int calibration_progress, leader_counter, leader_length;
static int break_length, break_counter, vis_counter, vis_length;
static int buffer_length, bitmap_width, bitmap_height;
static int sync_length, sync_counter, vpos, hpos;
static int save_cnt, save_dat, seperator_counter;
static int u_sep_begin, u_sep_end, v_sep_begin, v_sep_end;
static int y_begin, y_end, u_begin, u_end, v_begin, v_end;
static int r_begin, r_end, b_begin, b_end, g_begin, g_end;

static const int mode_raw = 0;
static const int mode_robot36 = 1;
static const int mode_yuv = 2;
static const int mode_rgb = 3;

void debug_sync()
{
    save_cnt = 1;
    save_dat = 0;
    mode = mode_raw;
    const float sync_len_min = 0.002f;
    sync_length = sync_len_min * sample_rate;
}
void debug_image()
{
    save_dat = 1;
    save_cnt = 0;
    mode = mode_raw;
    const float sync_len_min = 0.002f;
    sync_length = sync_len_min * sample_rate;
}
void debug_both()
{
    save_cnt = 1;
    save_dat = 1;
    mode = mode_raw;
    const float sync_len_min = 0.002f;
    sync_length = sync_len_min * sample_rate;
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
}
void robot72_mode()
{
    save_dat = 1;
    save_cnt = 0;
    mode = mode_yuv;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_len = 0.009f;
    const float sync_porch_len = 0.003f;
    const float sep_porch_len = 0.0015f;
    const float y_scan_len = 0.138f;
    const float u_scan_len = 0.069f;
    const float v_scan_len = 0.069f;
    const float seperator_len = 0.0045f;
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
}
void martin1_mode()
{
    save_cnt = 0;
    save_dat = 1;
    mode = mode_rgb;
    const float tolerance = 0.5f;
    const float sync_len = 0.004862f;
    const float sync_porch_len = 0.000572f;
    const float r_scan_len = 0.146432f;
    const float g_scan_len = 0.146432f;
    const float b_scan_len = 0.146432f;
    const float seperator_len = 0.000572f;
    sync_length = tolerance * sync_len * sample_rate;
    g_begin = 0;
    g_end = g_begin + g_scan_len * sample_rate;
    b_begin = g_end + seperator_len * sample_rate;
    b_end = b_begin + b_scan_len * sample_rate;
    r_begin = b_end + seperator_len * sample_rate;
    r_end = r_begin + r_scan_len * sample_rate;
}
void martin2_mode()
{
    save_cnt = 0;
    save_dat = 1;
    mode = mode_rgb;
    const float tolerance = 0.5f;
    const float sync_len = 0.004862f;
    const float sync_porch_len = 0.000572f;
    const float r_scan_len = 0.073216f;
    const float g_scan_len = 0.073216f;
    const float b_scan_len = 0.073216f;
    const float seperator_len = 0.000572f;
    sync_length = tolerance * sync_len * sample_rate;
    g_begin = 0;
    g_end = g_begin + g_scan_len * sample_rate;
    b_begin = g_end + seperator_len * sample_rate;
    b_end = b_begin + b_scan_len * sample_rate;
    r_begin = b_end + seperator_len * sample_rate;
    r_end = r_begin + r_scan_len * sample_rate;
}
void scottie1_mode()
{
    save_cnt = 0;
    save_dat = 1;
    mode = mode_rgb;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_len = 0.009f;
    const float sync_porch_len = 0.0015f;
    const float r_scan_len = 0.138240f;
    const float g_scan_len = 0.138240f;
    const float b_scan_len = 0.138240f;
    const float seperator_len = 0.0015f;
    sync_length = tolerance * sync_len * sample_rate;
    r_begin = (sync_porch_len - settling_time) * sample_rate;
    r_end = r_begin + r_scan_len * sample_rate;
    g_begin = r_end + seperator_len * sample_rate;
    g_end = g_begin + g_scan_len * sample_rate;
    b_begin = g_end + seperator_len * sample_rate;
    b_end = b_begin + b_scan_len * sample_rate;
}
void scottie2_mode()
{
    save_cnt = 0;
    save_dat = 1;
    mode = mode_rgb;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_len = 0.009f;
    const float sync_porch_len = 0.0015f;
    const float r_scan_len = 0.088064f;
    const float g_scan_len = 0.088064f;
    const float b_scan_len = 0.088064f;
    const float seperator_len = 0.0015f;
    sync_length = tolerance * sync_len * sample_rate;
    r_begin = (sync_porch_len - settling_time) * sample_rate;
    r_end = r_begin + r_scan_len * sample_rate;
    g_begin = r_end + seperator_len * sample_rate;
    g_end = g_begin + g_scan_len * sample_rate;
    b_begin = g_end + seperator_len * sample_rate;
    b_end = b_begin + b_scan_len * sample_rate;
}
void scottieDX_mode()
{
    save_cnt = 0;
    save_dat = 1;
    mode = mode_rgb;
    const float tolerance = 0.8f;
    const float settling_time = 0.0011f;
    const float sync_len = 0.009f;
    const float sync_porch_len = 0.0015f;
    const float r_scan_len = 0.3456f;
    const float g_scan_len = 0.3456f;
    const float b_scan_len = 0.3456f;
    const float seperator_len = 0.0015f;
    sync_length = tolerance * sync_len * sample_rate;
    r_begin = (sync_porch_len - settling_time) * sample_rate;
    r_end = r_begin + r_scan_len * sample_rate;
    g_begin = r_end + seperator_len * sample_rate;
    g_end = g_begin + g_scan_len * sample_rate;
    b_begin = g_end + seperator_len * sample_rate;
    b_end = b_begin + b_scan_len * sample_rate;
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

    const float leader_tolerance = 0.3f;
    const float break_tolerance = 0.7f;
    const float timeout_tolerance = 1.1f;
    const float leader_len = 0.3f;
    const float break_len = 0.01f;
    const float vis_len = 0.3f;
    calibration_progress = 0;
    calibration_countdown = 0;
    leader_counter = 0;
    break_counter = 0;
    vis_counter = 0;
    leader_length = leader_tolerance * leader_len * sample_rate;
    break_length = break_tolerance * break_len * sample_rate;
    vis_length = vis_len * sample_rate;
    leader_timeout = timeout_tolerance * leader_len * sample_rate;
    break_timeout = timeout_tolerance * break_len * sample_rate;
    vis_timeout = timeout_tolerance * vis_len * sample_rate;

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
    if (seperator_counter > 0) {
        if (vpos < 1)
            vpos = 1;
        for (int i = 0; i < bitmap_width; ++i) {
            uchar even_y = value_buffer[i * (y_end-y_begin) / bitmap_width + y_begin];
            uchar u = value_buffer[i * (u_end-u_begin) / bitmap_width + u_begin];
            uchar odd_y = value_buffer[i * (y_end-y_begin) / bitmap_width + even_hpos + y_begin];
            uchar v = value_buffer[i * (v_end-v_begin) / bitmap_width + even_hpos + v_begin];
            pixel_buffer[bitmap_width * (vpos-1) + i] = rsYuvToRGBA_uchar4(even_y, u, v);
            pixel_buffer[bitmap_width * vpos + i] = rsYuvToRGBA_uchar4(odd_y, u, v);
        }
        even_hpos = hpos = 0;
    } else {
        for (int i = 0; i < bitmap_width; ++i)
            pixel_buffer[bitmap_width * vpos + i] = rgb(0, 0, 0);
        if (hpos < buffer_length)
            even_hpos = hpos;
        else
            even_hpos = hpos = 0;
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
    even_hpos = hpos = 0;
}
static void rgb_decoder()
{
   for (int i = 0; i < bitmap_width; ++i) {
        uchar r = value_buffer[i * (r_end-r_begin) / bitmap_width + r_begin];
        uchar g = value_buffer[i * (g_end-g_begin) / bitmap_width + g_begin];
        uchar b = value_buffer[i * (b_end-b_begin) / bitmap_width + b_begin];
        pixel_buffer[bitmap_width * vpos + i] = rgb(r, g, b);
    }
    even_hpos = hpos = 0;
}
static void raw_decoder()
{
    for (int i = 0; i < bitmap_width; ++i) {
        uchar value = value_buffer[i * hpos / bitmap_width];
        pixel_buffer[bitmap_width * vpos + i] = rgb(value, value, value);
    }
    even_hpos = hpos = 0;
}

static int calibration_detected(float dat_value, int cnt_active, int cnt_quantized)
{
    int dat_active = !cnt_active;
    calibration_progress = calibration_countdown ? calibration_progress : 0;
    calibration_countdown -= !!calibration_countdown;

    int leader_quantized = round(leader_lowpass(dat_value));
    int leader_level = dat_active && leader_quantized == 0;
    int leader_pulse = !leader_level && leader_counter >= leader_length;
    leader_counter = leader_level ? leader_counter + 1 : 0;
    if (leader_pulse) {
        if (calibration_progress == 2) {
            calibration_progress = 3;
            calibration_countdown = vis_timeout;
        } else {
            calibration_progress = 1;
            calibration_countdown = break_timeout;
        }
    }

    int break_level = cnt_active && cnt_quantized == 0;
    int break_pulse = !break_level && break_counter >= break_length;
    break_counter = break_level ? break_counter + 1 : 0;
    if (break_pulse) {
        if (calibration_progress == 1) {
            calibration_progress = 2;
            calibration_countdown = leader_timeout;
        } else if (calibration_progress != 3) {
            calibration_progress = 0;
        }
    }

    return calibration_progress > 2;
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
        value_buffer[hpos] = cnt_active ? cnt_level : dat_level;

        int cnt_quantized = round(cnt_value);
        int dat_quantized = round(dat_value);

        if (calibration_detected(dat_value, cnt_active, cnt_quantized)) {
            vpos = 0;
            hpos = 0;
            even_hpos = 0;
            seperator_counter = 0;
            sync_counter = sync_length;
            if (++vis_counter < vis_length)
                continue;
            calibration_progress = 0;
            calibration_countdown = 0;
            vis_counter = 0;
            for (int i = 0; i < bitmap_width * bitmap_height; ++i)
                pixel_buffer[i] = rgb(0, 0, 0);
        }

        int sync_level = cnt_active && cnt_quantized == 0;
        int sync_pulse = !sync_level && sync_counter >= sync_length;
        sync_counter = sync_level ? sync_counter + 1 : 0;

        int u_sep = u_sep_begin <= (hpos-even_hpos) && (hpos-even_hpos) <= u_sep_end;
        int v_sep = v_sep_begin <= (hpos-even_hpos) && (hpos-even_hpos) <= v_sep_end;
        seperator_counter += (u_sep || v_sep) ? dat_quantized : 0;

        if (++hpos >= buffer_length || sync_pulse) {
            if (hpos < minimum_length) {
                hpos = 0;
                continue;
            }
            switch (mode) {
                case mode_robot36:
                    robot36_decoder();
                    break;
                case mode_yuv:
                    yuv_decoder();
                    break;
                case mode_rgb:
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
