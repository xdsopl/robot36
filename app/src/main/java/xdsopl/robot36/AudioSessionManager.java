package xdsopl.robot36;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns one input at a time. Start, stop and release are serialized by the UI owner. */
public class AudioSessionManager {
	public interface SessionCallback {
		void onPcmDataAvailable(float[] buffer, int frameCount, int sampleRate, int channels, long generation);
		void onSourceStateChanged(AudioSource.AudioSourceState state, String message, long generation);
	}

	private final SessionCallback callback;
	private final ExecutorService preparation = Executors.newSingleThreadExecutor(r -> new Thread(r, "AudioSession"));
	private volatile long generation;
	private volatile boolean released;
	private AudioSource currentSource;
	private CountDownLatch previousRelease = new CountDownLatch(0);

	public AudioSessionManager(SessionCallback callback) {
		this.callback = callback;
	}

	public boolean isCurrent(long session) {
		return !released && generation == session;
	}

	public void startSession(AudioSource source) {
		if (released) throw new IllegalStateException("Session manager is released");
		stopAndReleaseCurrentSession();
		final long session = generation;
		final CountDownLatch waitForPrevious = previousRelease;
		final CountDownLatch sourceReleased = new CountDownLatch(1);
		previousRelease = sourceReleased;
		currentSource = source;
		source.setAudioDataListener(new AudioSource.AudioDataListener() {
			@Override public void onAudioDataAvailable(float[] pcm, int frames, int rate, int channels) {
				if (isCurrent(session)) callback.onPcmDataAvailable(pcm, frames, rate, channels, session);
			}

			@Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
				if (state == AudioSource.AudioSourceState.RELEASED) sourceReleased.countDown();
				if (state == AudioSource.AudioSourceState.COMPLETED || state == AudioSource.AudioSourceState.ERROR
						|| state == AudioSource.AudioSourceState.STOPPED)
					source.release();
				if (isCurrent(session)) callback.onSourceStateChanged(state, message, session);
			}
		});
		preparation.execute(() -> {
			try {
				// Includes sources stopped before their preparation task began.
				waitForPrevious.await();
				if (!isCurrent(session)) return;
				source.prepare();
				if (isCurrent(session)) source.start();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (IOException | RuntimeException e) {
				if (isCurrent(session) && source.getFailure() == null)
					callback.onSourceStateChanged(AudioSource.AudioSourceState.ERROR, e.toString(), session);
				source.release();
			} finally {
				if (!isCurrent(session)) source.release();
			}
		});
	}

	public void stopAndReleaseCurrentSession() {
		++generation; // Invalidate PCM already queued for the UI, too.
		AudioSource previous = currentSource;
		currentSource = null;
		if (previous != null) previous.release();
	}

	public void release() {
		if (released) return;
		released = true;
		stopAndReleaseCurrentSession();
		preparation.shutdown();
	}
}
