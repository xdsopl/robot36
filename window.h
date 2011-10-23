/*
robot36 - encode and decode images using SSTV in Robot 36 mode
Written in 2011 by <Ahmet Inan> <xdsopl@googlemail.com>
To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide. This software is distributed without any warranty.
You should have received a copy of the CC0 Public Domain Dedication along with this software. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
*/


#ifndef WINDOW_H
#define WINDOW_H
float sinc(float);
float hann(float, float, float);
float hamming(float, float, float);
float lanczos(float, float, float);
float gauss(float, float, float);
float kaiser(float, float, float);

#endif

