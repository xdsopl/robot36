package xdsopl.robot36;

/**
 * Planned responsibility: Input selection and ownership of the active audio session.
 *
 * Keep the selected input mode separate from whether a source is currently running.
 * Create a fresh source for each run and invalidate callbacks from earlier sessions.
 * Prepare inputs off the main thread and forward PCM with a session identity.
 * Own source switching and coordinate file monitoring and capture-service connections
 * as those inputs are integrated. Retain file selection independently of playback.
 * Leave resource-level read cancellation to the source, projection lifetime to the
 * capture service, and decoder state and view updates to MainActivity.
 *
 * Responsibility-only scaffold. No runtime type is declared in this UI step.
 * Add the implementation and its tests in the corresponding integration commit.
 */
