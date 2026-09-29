package xdsopl.robot36;

/**
 * Planned responsibility: WAV file input that supplies the same PCM stream as live inputs.
 *
 * Open the user-selected content URI and parse RIFF chunks, including unknown chunks
 * and required padding, before reading the PCM data. Validate the actual file format.
 * Convert supported PCM encodings to the shared float PCM representation using fixed
 * full-scale conversion for integer samples and preserving the scale of float input.
 * Report actual frame counts, including the final short block. Reject malformed or
 * truncated input.
 * Support realtime pacing and unpaced Turbo delivery without skipping samples or
 * changing the sample rate. End-of-file means input delivery has ended; it does not
 * by itself prove that an SSTV image is complete. Do not add compressed-format or
 * resampling interfaces before a concrete implementation requires them.
 *
 * Responsibility-only scaffold. No runtime type is declared in this UI step.
 * Add the implementation and its tests in the corresponding integration commit.
 */
