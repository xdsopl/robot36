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

#ifndef PULSE_RSH
#define PULSE_RSH

typedef struct {
    int counter, length, buildup;
} pulse_t;

static pulse_t init_pulse(float length, float buildup, int rate)
{
    return (pulse_t){ 0, round((length * rate) / 1000.0f), round((buildup * rate) / 1000.0f) };
}

static int pulse_detected(pulse_t *pulse, int level)
{
    int trailing_edge = !level && pulse->counter >= pulse->length;
    pulse->counter = level ? pulse->counter + 1 : 0;
    return trailing_edge;
}

static void fake_pulse(pulse_t *pulse)
{
    pulse->counter = pulse->length;
}

static int trailing_edge_position(pulse_t *pulse)
{
    return -pulse->buildup;
}

#endif