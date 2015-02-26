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

static int calibration_detected(int cnt_quantized)
{
    static int ssb_counter;
    int ssb_level = cnt_quantized == 0;
    int ssb_pulse = !ssb_level && ssb_counter >= ssb_length;
    ssb_counter = ssb_level ? ssb_counter + 1 : 0;

    static int bit_pos, vis_pos, vis_code;
    static int vis_counter, bit_counter;

    if (ssb_pulse) {
        vis_code = 0;
        bit_pos = 0;
        vis_pos = 2 * bit_length;
        bit_counter = 0;
        vis_counter = bit_length;
    }

    if (bit_pos >= 8)
        return -1;

    if (++vis_counter < vis_pos) {
        bit_counter += cnt_quantized;
        return -1;
    }

    if (2 * abs(bit_counter) < bit_length) {
        bit_pos = 8;
        return -1;
    }

    int bit_val = bit_counter < 0 ? 1 : 0;
    bit_counter = cnt_quantized;
    vis_code |= bit_val << bit_pos;
    vis_pos += bit_length;

    if (++bit_pos < 8)
        return -1;

    //RS_DEBUG(vis_code);
    return vis_code;
}

static int calibration_detector(int cnt_quantized)
{
    switch (calibration_detected(cnt_quantized)) {
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
        case 0xb7:
            return mode_wraaseSC2_180;
        case 0xdd:
            return mode_pd50;
        case 0x63:
            return mode_pd90;
        case 0x5f:
            return mode_pd120;
        case 0xe2:
            return mode_pd160;
        case 0x60:
            return mode_pd180;
        case 0xe1:
            return mode_pd240;
        case 0xde:
            return mode_pd290;
        default:
            return -1;
    }
    return -1;
}

#endif