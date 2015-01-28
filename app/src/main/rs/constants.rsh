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

#ifndef CONSTANTS_RSH
#define CONSTANTS_RSH

static const int decoder_raw = 0;
static const int decoder_robot36 = 1;
static const int decoder_yuv = 2;
static const int decoder_rgb = 3;
static const int decoder_scottie = 4;

static const int mode_raw = 0;
static const int mode_robot36 = 1;
static const int mode_robot72 = 2;
static const int mode_martin1 = 3;
static const int mode_martin2 = 4;
static const int mode_scottie1 = 5;
static const int mode_scottie2 = 6;
static const int mode_scottieDX = 7;
static const int mode_wrasseSC2_180 = 8;

static const float sync_buildup_ms = 1.1f;
static const float scanline_tolerance = 0.05f;
static const float robot36_scanline_ms = 150.0f;
static const float robot72_scanline_ms = 300.0f;
static const float martin1_scanline_ms = 446.446f;
static const float martin2_scanline_ms = 226.798f;
static const float scottie1_scanline_ms = 428.22f;
static const float scottie2_scanline_ms = 277.692f;
static const float scottieDX_scanline_ms = 1050.3f;
static const float wrasseSC2_180_scanline_ms = 711.0225f;

#endif