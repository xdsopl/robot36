package xdsopl.robot36;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.RequiresApi;

/** Foreground projection owner, bound until the Activity's capture session ends. */
@RequiresApi(29)
public final class AudioCaptureService extends Service {
	private static final String CHANNEL = "audio_capture";
	private static final String STOP = "xdsopl.robot36.STOP_CAPTURE";
	private static final int NOTIFICATION = 1;
	private MediaProjection projection;
	private Runnable onStopped;
	private final MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
		@Override public void onStop() { stopCapture(); }
	};

	final class LocalBinder extends Binder {
		AudioCaptureService getService() { return AudioCaptureService.this; }
	}

	@Override public IBinder onBind(Intent intent) { return new LocalBinder(); }

	/** Called on the main thread with a fresh grant. Never retain the result Intent. */
	MediaProjection startCapture(int resultCode, Intent data, Runnable stopped) {
		if (projection != null) throw new IllegalStateException("Capture already started");
		onStopped = stopped;
		NotificationManager notifications = getSystemService(NotificationManager.class);
		notifications.createNotificationChannel(new NotificationChannel(CHANNEL,
				getString(R.string.capture_notification_channel), NotificationManager.IMPORTANCE_LOW));
		PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
		PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, AudioCaptureService.class).setAction(STOP), PendingIntent.FLAG_IMMUTABLE);
		Notification notification = new Notification.Builder(this, CHANNEL)
				.setSmallIcon(R.drawable.ic_screen_share_24)
				.setContentTitle(getString(R.string.capture_listening))
				.setContentText(getString(R.string.capture_notification_text))
				.setContentIntent(open).setOngoing(true)
				.addAction(new Notification.Action.Builder(null, getString(R.string.capture_stop), stop).build())
				.build();
		// Android requires foreground state before getMediaProjection(), even for audio only.
		startForeground(NOTIFICATION, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
		projection = getSystemService(MediaProjectionManager.class).getMediaProjection(resultCode, data);
		if (projection == null) throw new IllegalStateException("No media projection was granted");
		projection.registerCallback(projectionCallback, new Handler(Looper.getMainLooper()));
		return projection;
	}

	void stopCapture() {
		MediaProjection previous = projection;
		projection = null;
		if (previous != null) {
			try {
				previous.unregisterCallback(projectionCallback);
				previous.stop();
			} catch (RuntimeException e) { Log.w("Robot36", "Projection already stopped", e); }
		}
		stopForeground(STOP_FOREGROUND_REMOVE);
		Runnable stopped = onStopped;
		onStopped = null;
		if (stopped != null) stopped.run();
		stopSelf();
	}

	@Override public int onStartCommand(Intent intent, int flags, int startId) {
		// The notification can only stop a session. No cached grant or automatic restart.
		if (intent != null && STOP.equals(intent.getAction())) stopCapture();
		else stopSelf();
		return START_NOT_STICKY;
	}

	@Override public boolean onUnbind(Intent intent) { stopCapture(); return false; }
	@Override public void onDestroy() { stopCapture(); super.onDestroy(); }
}
