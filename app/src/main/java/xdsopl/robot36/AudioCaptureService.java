package xdsopl.robot36;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import androidx.core.app.NotificationCompat;

/** Owns the foreground lifetime and the one-use projection authorization. */
public class AudioCaptureService extends Service {
    private static final String CHANNEL = "audio_capture_channel";
    private final LocalBinder binder = new LocalBinder();
    private MediaProjection projection;
    private AudioSource source;
    private Listener listener;
    private boolean foreground;

    interface Listener {
        void onReady(MediaProjection projection);
        void onStopped(String error);
    }
    public final class LocalBinder extends Binder {
        AudioCaptureService getService() { return AudioCaptureService.this; }
    }
    private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override public void onStop() {
            if (source != null) source.release();
            if (listener != null) {
                Listener notify = listener;
                listener = null;
                notify.onStopped("");
            }
            stopSelf();
        }
    };

    private void promote() {
        if (foreground) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Audio capture", NotificationManager.IMPORTANCE_LOW));
        }
        android.app.Notification notification = new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_screen_share_24)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.playback_capture))
                .setOngoing(true).build();
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1001, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        else startForeground(1001, notification);
        foreground = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        // Never restart with an expired projection grant after process death.
        try { promote(); } catch (RuntimeException e) { stopSelf(); }
        return START_NOT_STICKY;
    }

    void begin(int resultCode, Intent data, Listener listener) {
        this.listener = listener;
        try {
            if (projection != null) throw new IllegalStateException("Projection already started");
            promote(); // Must precede getMediaProjection, including on Android 14.
            MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            projection = manager.getMediaProjection(resultCode, data);
            if (projection == null) throw new IllegalStateException("Projection authorization unavailable");
            projection.registerCallback(projectionCallback, new Handler(Looper.getMainLooper()));
            listener.onReady(projection);
        } catch (RuntimeException e) {
            this.listener = null;
            listener.onStopped(e.toString());
            stopSelf();
        }
    }

    void attachSource(AudioSource source) { this.source = source; }
    void detachListener() { listener = null; }
    @Override public IBinder onBind(Intent intent) { return binder; }

    @Override public void onDestroy() {
        if (source != null) source.release();
        if (projection != null) {
            projection.unregisterCallback(projectionCallback);
            projection.stop();
            projection = null;
        }
        if (listener != null) {
            Listener notify = listener;
            listener = null;
            notify.onStopped("");
        }
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }
}
