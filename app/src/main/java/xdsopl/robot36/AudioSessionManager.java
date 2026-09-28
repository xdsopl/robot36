package xdsopl.robot36;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.OpenableColumns;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Selection is independent of playback. A generation identifies every audio session. */
public class AudioSessionManager {
    public enum SourceMode { MICROPHONE, FILE, PLAYBACK_CAPTURE }

    public interface SessionCallback {
        void onPcmDataAvailable(float[] buffer, int frameCount, int sampleRate, int channels, long generation);
        void onSourceStateChanged(AudioSource.AudioSourceState state, String message, long generation);
        void onFileSelected(String fileName);
        void onFileUnloaded();
        void onPlaybackStateChanged(boolean isPlaying);
    }

    private final Context context;
    private final SessionCallback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService preparation = Executors.newSingleThreadExecutor();
    private volatile long generation;
    private volatile AudioSource currentSource;
    private SourceMode currentMode = SourceMode.MICROPHONE;
    private Uri selectedFileUri;
    private String selectedFileName;
    private boolean turbo, released;
    private int rate = 44100, channels = 1;
    private int preset = MediaRecorder.AudioSource.MIC, encoding = AudioFormat.ENCODING_PCM_16BIT;
    private ServiceConnection connection;
    private AudioCaptureService captureService;

    public AudioSessionManager(Context context, SessionCallback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public void updateAudioRecordConfig(int rate, int channels, int preset, int encoding) {
        boolean changed = this.rate != rate || this.channels != channels || this.preset != preset || this.encoding != encoding;
        this.rate = rate; this.channels = channels; this.preset = preset; this.encoding = encoding;
        if (changed && currentMode == SourceMode.MICROPHONE) stopAndReleaseCurrentSession();
    }
    public SourceMode getCurrentMode() { return currentMode; }
    public long getGeneration() { return generation; }
    public boolean isCurrent(long value) { return !released && generation == value; }
    public boolean isTurboEnabled() { return turbo; }
    public void setTurboEnabled(boolean turbo) { this.turbo = turbo; }
    public Uri getSelectedFileUri() { return selectedFileUri; }
    public String getSelectedFileName() { return selectedFileName; }
    public boolean isFileSelected() { return selectedFileUri != null; }
    public boolean isFilePlaying() { return currentMode == SourceMode.FILE && currentSource != null; }

    public void selectMode(SourceMode mode) {
        if (currentMode != mode) {
            stopAndReleaseCurrentSession();
            currentMode = mode;
        }
    }

    public void startMicrophoneSession() {
        if (released) return;
        selectMode(SourceMode.MICROPHONE);
        if (currentSource != null) return;
        stopAndReleaseCurrentSession();
        startSource(new MicrophoneAudioSource(rate, channels, preset, encoding), false);
    }

    public void startPlaybackCaptureSession(int resultCode, Intent data) {
        if (released) return;
        stopAndReleaseCurrentSession();
        currentMode = SourceMode.PLAYBACK_CAPTURE;
        final long session = generation;
        if (Build.VERSION.SDK_INT < 29) {
            callback.onSourceStateChanged(AudioSource.AudioSourceState.ERROR, "Capture requires Android 10+", session);
            return;
        }
        Intent intent = new Intent(context, AudioCaptureService.class);
        connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                if (!isCurrent(session)) return;
                captureService = ((AudioCaptureService.LocalBinder) binder).getService();
                captureService.begin(resultCode, data, new AudioCaptureService.Listener() {
                    @Override public void onReady(MediaProjection projection) {
                        if (!isCurrent(session)) return;
                        AudioSource source = new AudioPlaybackCaptureSource(projection, rate, channels, encoding);
                        captureService.attachSource(source);
                        startSource(source, false);
                    }
                    @Override public void onStopped(String error) {
                        if (!isCurrent(session)) return;
                        stopAndReleaseCurrentSession();
                        callback.onSourceStateChanged(error.isEmpty() ? AudioSource.AudioSourceState.STOPPED
                                : AudioSource.AudioSourceState.ERROR, error, generation);
                    }
                });
            }
            @Override public void onServiceDisconnected(ComponentName name) {
                if (!isCurrent(session)) return;
                stopAndReleaseCurrentSession();
                callback.onSourceStateChanged(AudioSource.AudioSourceState.STOPPED, "", generation);
            }
        };
        try {
            context.startForegroundService(intent);
            if (!context.bindService(intent, connection, Context.BIND_AUTO_CREATE))
                throw new IllegalStateException("Cannot bind audio capture service");
        } catch (RuntimeException e) {
            stopAndReleaseCurrentSession();
            callback.onSourceStateChanged(AudioSource.AudioSourceState.ERROR, e.toString(), generation);
        }
    }

    public void selectFile(Uri uri) {
        selectMode(SourceMode.FILE);
        stopFilePlayback();
        selectedFileUri = uri;
        selectedFileName = queryFileName(uri);
        callback.onFileSelected(selectedFileName);
    }
    public void startFilePlayback() {
        if (selectedFileUri == null || released) return;
        stopAndReleaseCurrentSession();
        currentMode = SourceMode.FILE;
        boolean monitor = !turbo;
        FileAudioSource source = new FileAudioSource(context, selectedFileUri,
                turbo ? AudioSource.DecodePacing.TURBO : AudioSource.DecodePacing.REALTIME);
        callback.onPlaybackStateChanged(true);
        startSource(source, monitor);
    }
    public void stopFilePlayback() {
        if (currentMode == SourceMode.FILE) stopAndReleaseCurrentSession();
        callback.onPlaybackStateChanged(false);
    }
    public void unloadFile() {
        stopFilePlayback();
        selectedFileUri = null;
        selectedFileName = null;
        callback.onFileUnloaded();
    }

    public void stopAndReleaseCurrentSession() {
        ++generation; // Invalidate even PCM that has already been posted to the UI.
        AudioSource previous = currentSource;
        currentSource = null;
        if (previous != null) previous.release();
        if (captureService != null) {
            captureService.detachListener();
            captureService = null;
        }
        if (connection != null) {
            try { context.unbindService(connection); } catch (IllegalArgumentException ignored) { }
            connection = null;
            context.stopService(new Intent(context, AudioCaptureService.class));
        }
    }

    public void release() {
        stopAndReleaseCurrentSession();
        released = true;
        preparation.shutdown();
    }

    private void startSource(AudioSource source, boolean monitor) {
        final long session = generation;
        final AudioTrackPlayer player = monitor ? new AudioTrackPlayer() : null;
        currentSource = source;
        source.setAudioDataListener(new AudioSource.AudioDataListener() {
            @Override public void onAudioDataAvailable(float[] pcm, int frames, int sampleRate, int channels) {
                if (!isCurrent(session)) return;
                if (player != null) player.write(pcm, frames, channels);
                if (isCurrent(session)) callback.onPcmDataAvailable(pcm, frames, sampleRate, channels, session);
            }
            @Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
                boolean terminal = state == AudioSource.AudioSourceState.COMPLETED
                        || state == AudioSource.AudioSourceState.ERROR || state == AudioSource.AudioSourceState.STOPPED;
                if (terminal || state == AudioSource.AudioSourceState.RELEASED) {
                    if (player != null) player.stop();
                    if (terminal) source.release();
                }
                if (isCurrent(session)) callback.onSourceStateChanged(state, message, session);
                if (terminal) main.post(() -> {
                    if (!isCurrent(session) || currentSource != source) return;
                    currentSource = null;
                    if (currentMode == SourceMode.FILE) callback.onPlaybackStateChanged(false);
                    if (currentMode == SourceMode.PLAYBACK_CAPTURE) stopAndReleaseCurrentSession();
                });
            }
        });
        preparation.execute(() -> {
            try {
                if (!isCurrent(session)) return;
                source.prepare();
                if (!isCurrent(session)) return;
                if (player != null) {
                    AudioSource.PcmFormat format = source.getFormat();
                    player.prepare(format.getSampleRate(), format.getChannels());
                }
                if (isCurrent(session)) source.start();
            } catch (Exception e) {
                if (isCurrent(session) && source.getFailure() == null)
                    callback.onSourceStateChanged(AudioSource.AudioSourceState.ERROR, e.toString(), session);
                main.post(() -> {
                    if (isCurrent(session) && currentSource == source) {
                        currentSource = null;
                        callback.onPlaybackStateChanged(false);
                        if (currentMode == SourceMode.PLAYBACK_CAPTURE) stopAndReleaseCurrentSession();
                    }
                });
            } finally {
                if (!isCurrent(session) || source.getState() != AudioSource.AudioSourceState.RUNNING
                        && source.getState() != AudioSource.AudioSourceState.READY) {
                    source.release();
                    if (player != null) player.stop();
                }
            }
        });
    }

    private String queryFileName(Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) return cursor.getString(index);
            }
        } catch (RuntimeException ignored) { }
        return uri.getLastPathSegment() == null ? "audio.wav" : uri.getLastPathSegment();
    }
}
