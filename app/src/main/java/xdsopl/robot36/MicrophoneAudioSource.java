package xdsopl.robot36;

import android.media.AudioFormat;
import android.media.AudioRecord;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** AudioRecord access is owned by the source worker until its last read returns. */
public class MicrophoneAudioSource extends WorkerAudioSource {
    protected final int sampleRate, channels, encoding;
    private final int preset;
    private AudioRecord record;

    public MicrophoneAudioSource(int sampleRate, int channels, int preset, int encoding) {
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.preset = preset;
        this.encoding = encoding;
    }

    @Override public AudioSourceType getType() { return AudioSourceType.MICROPHONE; }

    protected AudioRecord createRecord(int channelMask, int bufferBytes) {
        return new AudioRecord(preset, sampleRate, channelMask, encoding, bufferBytes);
    }

    @Override protected PcmFormat openInput() throws IOException {
        if (channels != 1 && channels != 2)
            throw new IOException("Only mono and stereo input are supported");
        if (encoding != AudioFormat.ENCODING_PCM_16BIT && encoding != AudioFormat.ENCODING_PCM_FLOAT)
            throw new IOException("Unsupported AudioRecord PCM encoding");
        int mask = channels == 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
        int min = AudioRecord.getMinBufferSize(sampleRate, mask, encoding);
        if (min <= 0) throw new IOException("Unsupported AudioRecord format: " + min);
        int bytesPerFrame = channels * (encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2);
        record = createRecord(mask, Math.max(min, sampleRate / 5 * bytesPerFrame));
        if (record.getState() != AudioRecord.STATE_INITIALIZED)
            throw new IOException("AudioRecord initialization failed");
        return new PcmFormat(record.getSampleRate(), channels);
    }

    @Override protected void readInput() throws IOException {
        record.startRecording();
        if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
            throw new IOException("AudioRecord did not start");
        int count = Math.max(1, getFormat().getSampleRate() / 50) * channels;
        float[] pcm = new float[count];
        short[] shorts = encoding == AudioFormat.ENCODING_PCM_16BIT ? new short[count] : null;
        while (!cancelled()) {
            int n = shorts == null
                    ? record.read(pcm, 0, count, AudioRecord.READ_BLOCKING)
                    : record.read(shorts, 0, count, AudioRecord.READ_BLOCKING);
            if (cancelled()) break;
            if (n < 0) throw new IOException("AudioRecord read failed: " + n);
            if (n == 0) continue;
            if (n % channels != 0) throw new IOException("Incomplete AudioRecord frame");
            if (shorts != null)
                for (int i = 0; i < n; ++i) pcm[i] = shorts[i] / 32768.0f;
            else
                for (int i = 0; i < n; ++i)
                    if (Float.isNaN(pcm[i]) || Float.isInfinite(pcm[i])) pcm[i] = 0;
            emit(pcm, n / channels);
        }
    }

    @Override protected void closeInput() {
        if (record == null) return;
        try {
            if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) record.stop();
        } finally {
            record.release();
            record = null;
        }
    }
}

/**
 * Single-use lifecycle shared by all sources. Cleanup is queued behind preparation
 * and reads; release never destroys a resource still used by a callback.
 */
abstract class WorkerAudioSource implements AudioSource {
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "AudioSource"));
    private volatile AudioSourceState state = AudioSourceState.NEW;
    private volatile PcmFormat format;
    private volatile IOException failure;
    private volatile boolean cancelled, releasing;
    private volatile Thread workerThread;
    private AudioDataListener listener;
    private boolean prepared, started;

    @Override public synchronized void setAudioDataListener(AudioDataListener listener) {
        if (prepared || state != AudioSourceState.NEW || listener == null)
            throw new IllegalStateException("Register a listener before preparation");
        this.listener = listener;
    }

    @Override public void prepare() throws IOException {
        Future<?> task;
        synchronized (this) {
            if (prepared || cancelled || releasing || listener == null)
                throw new IllegalStateException("Source is single-use");
            prepared = true;
            task = worker.submit(() -> {
                workerThread = Thread.currentThread();
                try {
                    if (cancelled) return;
                    publish(AudioSourceState.INITIALIZING, "");
                    format = openInput();
                    if (!cancelled) publish(AudioSourceState.READY, "");
                } catch (Exception e) {
                    fail(e);
                } finally {
                    if (cancelled || failure != null) finish();
                    workerThread = null;
                }
            });
        }
        try {
            task.get();
        } catch (InterruptedException e) {
            stop();
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Audio preparation interrupted");
        } catch (ExecutionException e) {
            throw new IOException("Audio preparation failed", e.getCause());
        }
        if (failure != null) throw failure;
        if (cancelled) throw new InterruptedIOException("Audio preparation cancelled");
    }

    @Override public synchronized void start() {
        if (state != AudioSourceState.READY || started || cancelled || releasing)
            throw new IllegalStateException("Source is not ready");
        started = true;
        worker.execute(() -> {
            workerThread = Thread.currentThread();
            try {
                if (!cancelled) {
                    publish(AudioSourceState.RUNNING, "");
                    readInput();
                    if (!cancelled) publish(AudioSourceState.COMPLETED, "");
                }
            } catch (Exception e) {
                fail(e);
            } finally {
                finish();
                workerThread = null;
            }
        });
    }

    @Override public synchronized void stop() {
        if (cancelled || state == AudioSourceState.RELEASED) return;
        cancelled = true;
        Thread thread = workerThread;
        if (thread != null) thread.interrupt();
        if (!worker.isShutdown()) worker.execute(this::finish);
    }

    @Override public synchronized void release() {
        if (releasing) return;
        releasing = true;
        stop();
        if (!worker.isShutdown()) worker.execute(this::finish);
    }

    private void fail(Exception e) {
        if (cancelled) return;
        failure = e instanceof IOException ? (IOException) e : new IOException("Audio input failed", e);
        publish(AudioSourceState.ERROR, failure.getMessage());
    }

    private void finish() {
        if (state == AudioSourceState.RELEASED) return;
        try { closeInput(); } catch (Exception e) { fail(e); }
        if (cancelled && state != AudioSourceState.ERROR && state != AudioSourceState.COMPLETED
                && state != AudioSourceState.STOPPED)
            publish(AudioSourceState.STOPPED, "");
        if (releasing) {
            publish(AudioSourceState.RELEASED, "");
            worker.shutdown();
        }
        Thread.interrupted();
    }

    private void publish(AudioSourceState next, String message) {
        state = next;
        if (listener != null) listener.onStatusChanged(next, message == null ? "" : message);
    }

    protected final boolean cancelled() { return cancelled; }
    protected final void emit(float[] pcm, int frames) {
        if (!cancelled && frames > 0)
            listener.onAudioDataAvailable(pcm, frames, format.getSampleRate(), format.getChannels());
    }
    @Override public final AudioSourceState getState() { return state; }
    @Override public final IOException getFailure() { return failure; }
    @Override public final PcmFormat getFormat() {
        if (format == null) throw new IllegalStateException("Format not established");
        return format;
    }
    protected abstract PcmFormat openInput() throws IOException;
    protected abstract void readInput() throws IOException;
    protected abstract void closeInput() throws IOException;
}
