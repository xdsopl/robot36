package xdsopl.robot36;

/**
 * Planned responsibility: Microphone AudioRecord configuration and blocking PCM reads.
 *
 * Preserve the existing sample-rate, channel, recording-preset and encoding choices.
 * Read on the source worker and convert PCM16 samples using a fixed full-scale
 * divisor; preserve floating-point input without per-block peak normalization.
 * Deliver only complete valid frames in approximately 20 ms blocks.
 * Own the AudioRecord until reading and callbacks have ended, then release it.
 * Leave permission requests and decoder channel selection to their existing owners.
 *
 * Responsibility-only scaffold. No runtime type is declared in this UI step.
 * Add the implementation and its tests in the corresponding integration commit.
 */
