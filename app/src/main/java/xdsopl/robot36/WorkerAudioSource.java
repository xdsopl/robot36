package xdsopl.robot36;

/**
 * Planned responsibility: Serialized preparation, reading and cleanup shared by audio sources.
 *
 * Own the worker that accesses the input resource throughout a single-use session.
 * Coordinate stop and release without destroying a resource used by an active read
 * or callback. Reject repeated preparation and start requests.
 * Publish ordered state changes and preserve the cause of input failures.
 * Let concrete sources supply resource opening, PCM reads and resource closing.
 * Do not select an input, acquire projection permission, or update Android views.
 *
 * Responsibility-only scaffold. No runtime type is declared in this UI step.
 * Add the implementation and its tests in the corresponding integration commit.
 */
