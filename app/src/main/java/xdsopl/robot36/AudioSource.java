package xdsopl.robot36;

/**
 * Planned responsibility: A single audio-input session and its PCM delivery contract.
 *
 * Define the interleaved float PCM contract shared by all input sources, including
 * the actual sample rate and channel count. Integer PCM uses fixed full-scale
 * conversion; float PCM retains its input scale. Do not normalize each block's volume.
 * Carry the valid frame count so short reads never expose stale buffer contents.
 * Document borrowed-buffer ownership: the consumer may modify samples in place, and
 * the producer must not reuse a block until the synchronous callback returns.
 * Describe preparation, start, cancellation, completion, failure and resource release.
 * Keep Android permission prompts, UI rendering and SSTV interpretation outside this
 * boundary. Concrete sources will share this contract when they are introduced.
 *
 * Responsibility-only scaffold. No runtime type is declared in this UI step.
 * Add the implementation and its tests in the corresponding integration commit.
 */
