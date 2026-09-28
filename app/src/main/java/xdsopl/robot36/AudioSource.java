package xdsopl.robot36;

import java.io.IOException;

/**
 * A single audio-input session supplying interleaved, normalized float PCM.
 *
 * <p>Implementation boundaries:
 * <ul>
 * <li>MicrophoneAudioSource: AudioRecord configuration and blocking reads on a worker.</li>
 * <li>AudioPlaybackCaptureSource: the same read loop with a playback-capture configuration.
 * The Android authorization flow and foreground service belong to the session owner.</li>
 * <li>FileAudioSource: a chunk-aware WAV reader initially, with an interchangeable
 * PCM reader for future MediaExtractor/MediaCodec support. File pacing is separate
 * from parsing and never changes the sample rate or skips samples.</li>
 * </ul>
 *
 * <p>The owner serializes listener registration, prepare and start. Stop and release
 * may be requested from any thread, including a listener callback. Callbacks for
 * one source must never overlap and must be invoked without holding internal locks.
 * Listeners must return promptly, must not throw, and must marshal UI work to the
 * main thread. The owner must ignore stale callbacks after switching sessions.
 *
 * <p>Each instance is single-use: NEW -> INITIALIZING -> READY -> RUNNING, followed
 * by COMPLETED, ERROR, or STOPPING -> STOPPED. Release is allowed from any state
 * and ends in RELEASED after cleanup. Create a new instance to restart or retry.
 * No PCM is emitted after STOPPED, COMPLETED or ERROR; RELEASED is the final callback.
 * The owner must release every instance, including after preparation failure or EOF.
 *
 * <p>This contract does not connect the source to Decoder yet. The future adapter
 * must honor valid frame counts, preserve channel selection (including I/Q), and
 * split input into approximately 20 ms blocks even during Turbo decoding.
 */
public interface AudioSource {

    /** Input identity, independent of MediaRecorder.AudioSource recording presets. */
    enum AudioSourceType {
        MICROPHONE,
        PLAYBACK_CAPTURE,
        FILE
    }

    enum AudioSourceState {
        NEW,
        INITIALIZING,
        READY,
        RUNNING,
        STOPPING,
        STOPPED,
        /** All file PCM callbacks returned; this does not imply a complete SSTV image. */
        COMPLETED,
        ERROR,
        RELEASED
    }

    /**
     * File-session configuration, not a property of individual PCM blocks.
     * Live sources always follow the device clock and do not support TURBO.
     */
    enum DecodePacing {
        /** Pace by cumulative frames / sample rate against a monotonic clock. */
        REALTIME,
        /** Apply backpressure from the consumer, but introduce no timing waits. */
        TURBO
    }

    /**
     * Actual PCM format established by prepare and fixed for the entire session.
     * Encoding is always float PCM; source byte encoding stays inside the reader.
     * Channel count describes layout, not Decoder's channel-selection setting.
     */
    final class PcmFormat {
        private final int sampleRate;
        private final int channels;

        public PcmFormat(int sampleRate, int channels) {
            if (sampleRate <= 0)
                throw new IllegalArgumentException("Sample rate must be positive");
            if (channels != 1 && channels != 2)
                throw new IllegalArgumentException("Only mono and stereo PCM are supported");
            this.sampleRate = sampleRate;
            this.channels = channels;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public int getChannels() {
            return channels;
        }
    }

    interface AudioDataListener {
        /**
         * Synchronously consumes one nonempty PCM block on the source worker.
         *
         * <p>Only [0, frameCount * channels) is valid; the array may be larger.
         * Stereo layout is L, R, L, R (or I, Q when selected by the consumer).
         * Samples are finite, signed floats on a nominal [-1, 1] full-scale range.
         * PCM16 conversion divides by 32768; do not normalize each block by its peak.
         *
         * <p>The listener may modify valid samples in place (Decoder does).
         * The source must not reuse the buffer until this callback returns.
         * The listener must copy valid samples before retaining them for asynchronous
         * work. Waveform analysis or monitoring must precede destructive decoding.
         *
         * <p>Short reads and the final partial block carry their actual frame count.
         * Never emit stale array contents, empty blocks, or synthetic EOF padding.
         * Sources must not silently drop PCM to keep up with their consumers.
         *
         * @param buffer borrowed PCM buffer, valid only during this callback
         * @param frameCount positive number of frames, at most buffer.length / channels
         * @param sampleRate actual sample rate matching getFormat()
         * @param channels actual channel count matching getFormat()
         */
        void onAudioDataAvailable(float[] buffer, int frameCount, int sampleRate, int channels);

        /**
         * Reports ordered transitions; may run on the preparation or source worker.
         * The state is published before this callback. Message is non-null diagnostic
         * text, possibly empty; UI should localize labels using state and source type.
         * For ERROR, getFailure() is already available.
         */
        void onStatusChanged(AudioSourceState state, String message);
    }

    /**
     * Registers a non-null listener while NEW, before prepare. Replacing it after
     * preparation begins throws IllegalStateException. Registration emits no callback.
     */
    void setAudioDataListener(AudioDataListener listener);

    /**
     * Opens resources and determines the actual format without emitting PCM.
     * Call once, off the UI thread, with a listener installed and external permissions
     * already obtained. READY is published only after the format is available.
     *
     * @throws IOException on setup failure; publish ERROR and retain the cause
     * @throws java.io.InterruptedIOException if canceled; publish STOPPED, not ERROR
     * @throws IllegalStateException unless NEW with a listener installed
     */
    void prepare() throws IOException;

    /**
     * Starts asynchronous delivery from READY and returns promptly.
     * RUNNING precedes the first PCM callback. Startup/read failures publish ERROR
     * with getFailure(); a repeated or out-of-order call throws IllegalStateException.
     */
    void start();

    /**
     * Idempotent, nonblocking cancellation request. Unblocks reads and pacing waits.
     * An in-flight callback may finish; STOPPED is sent only after delivery has ended.
     * During preparation, cancellation must also prevent a subsequent READY transition.
     * Does nothing after STOPPED, COMPLETED, ERROR or RELEASED.
     */
    void stop();

    /**
     * Idempotent, nonblocking request to cancel and release all owned resources.
     * Cleanup must wait for active reads/callbacks without joining the calling worker.
     * RELEASED is sent only after cleanup; no callback follows it.
     */
    void release();

    /** Stable input identity; callable from any thread, including before prepare. */
    AudioSourceType getType();

    /** Thread-safe current state, initially NEW. */
    AudioSourceState getState();

    /**
     * Returns the immutable actual format from any thread after successful prepare.
     * Remains available after stop/release; throws IllegalStateException if no format
     * was established. New sources or changed formats require fresh decoder state.
     */
    PcmFormat getFormat();

    /**
     * Thread-safe failure detail, null until a failure occurs; retained after release.
     * Implementations wrap platform failures in IOException with the original cause.
     * User cancellation and normal EOF are not failures.
     */
    IOException getFailure();
}
