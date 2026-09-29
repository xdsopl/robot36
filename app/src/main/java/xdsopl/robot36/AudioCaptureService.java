package xdsopl.robot36;

/**
 * Planned responsibility: Foreground lifetime and MediaProjection ownership for playback capture.
 *
 * Enter the required foreground state before obtaining and using the projection.
 * Register for system projection termination and coordinate release of the attached
 * audio source when authorization ends or the service is destroyed.
 * Expose the ready/stopped boundary to the session owner through a local connection.
 * Do not restart with an expired projection grant after process death.
 * Keep decoding, image buffers and Activity views outside this service. Introduce the
 * Manifest declaration and foreground notification with the working implementation.
 *
 * Responsibility-only scaffold. No runtime type is declared in this UI step.
 * Add the implementation and its tests in the corresponding integration commit.
 */
