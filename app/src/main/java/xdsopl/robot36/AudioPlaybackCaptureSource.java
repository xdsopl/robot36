package xdsopl.robot36;

/**
 * Planned responsibility: Android playback audio capture through AudioRecord.
 *
 * Implement AudioSource and use the same PcmFormat, PCM callback and lifecycle states
 * as microphone input. Obtain authorization outside the source before prepare().
 * Accept an authorized MediaProjection and configure eligible playback audio usages.
 * Reuse the live-input PCM reading path and format handling instead of implementing
 * a separate decoder or a second conversion pipeline.
 * Own only the AudioRecord associated with this capture session.
 * The Activity requests authorization, and the foreground service owns the projection.
 * Restrict construction to supported Android versions and surface setup/read failures
 * through the shared audio-source contract. Do not retain or reuse authorization grants.
 *
 * Responsibility-only scaffold. No playback-capture implementation is connected yet.
 * Add the implementation and its tests in the corresponding integration commit.
 */
