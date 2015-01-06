/*
Copyright 2015 Ahmet Inan <xdsopl@googlemail.com>

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

#ifndef UTILS_RSH
#define UTILS_RSH

static inline uchar4 rgb(uchar r, uchar g, uchar b) { return (uchar4){ b, g, r, 255 }; }
static inline uchar4 yuv(uchar y, uchar u, uchar v)
{
    uchar4 bgra = rsYuvToRGBA_uchar4(y, u, v);
    return rgb(bgra[0], bgra[1], bgra[2]);
}

#endif