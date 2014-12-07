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

#ifndef CALIBRATION_DETECTOR_RSH
#define CALIBRATION_DETECTOR_RSH

#include "constants.rsh"
#include "state.rsh"

static int calibration_detected(float dat_value, int dat_active, int cnt_active, int cnt_quantized)
{
    static int progress, countdown;
    static int leader_counter, break_counter;

    progress = countdown ? progress : 0;
    countdown -= !!countdown;

    int leader_quantized = round(filter(&leader_lowpass, dat_value));
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

static int calibration_detector(float dat_value, int dat_active, int cnt_active, int cnt_quantized)
{
    switch (calibration_detected(dat_value, dat_active, cnt_active, cnt_quantized)) {
        case 0x88:
            return mode_robot36;
        case 0x0c:
            return mode_robot72;
        case 0xac:
            return mode_martin1;
        case 0x28:
            return mode_martin1;
        case 0x3c:
            return mode_scottie1;
        case 0xb8:
            return mode_scottie2;
        case 0xcc:
            return mode_scottieDX;
        default:
            return -1;
    }
    return -1;
}

#endif