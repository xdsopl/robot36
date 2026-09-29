package xdsopl.robot36;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import java.io.IOException;
import java.io.InterruptedIOException;

/** Audible file monitoring. All native access is on the source worker; cancel only signals. */
public final class AudioTrackPlayer implements AudioSessionManager.Monitor {
	private volatile boolean cancelled;
	private AudioTrack track;
	private int rate, channels;
	private long writtenFrames, previousHead, headWrap;

	private void open(int rate, int channels) throws IOException {
		this.rate = rate;
		this.channels = channels;
		int mask = channels == 1 ? AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
		int min = AudioTrack.getMinBufferSize(rate, mask, AudioFormat.ENCODING_PCM_FLOAT);
		if (min <= 0) throw new IOException("Unsupported audio playback format");
		track = new AudioTrack.Builder()
				.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
						.setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
				.setAudioFormat(new AudioFormat.Builder().setSampleRate(rate).setChannelMask(mask)
						.setEncoding(AudioFormat.ENCODING_PCM_FLOAT).build())
				.setTransferMode(AudioTrack.MODE_STREAM)
				.setBufferSizeInBytes(Math.max(min, rate / 10 * channels * 4)).build();
		if (track.getState() != AudioTrack.STATE_INITIALIZED) throw new IOException("AudioTrack initialization failed");
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) track.setStartThresholdInFrames(1);
		track.play();
	}

	@Override public void write(float[] pcm, int frames, int rate, int channels) throws IOException {
		checkCancelled();
		if (track == null) open(rate, channels);
		if (this.rate != rate || this.channels != channels) throw new IOException("Playback format changed within session");
		int offset = 0, count = frames * channels;
		long stalledSince = System.nanoTime();
		while (offset < count) {
			checkCancelled();
			int n = track.write(pcm, offset, count - offset, AudioTrack.WRITE_NON_BLOCKING);
			if (n < 0 || n % channels != 0) throw new IOException("AudioTrack write failed: " + n);
			if (n == 0) {
				if (System.nanoTime() - stalledSince > 3_000_000_000L) throw new IOException("Audio playback stalled");
				waitBriefly();
			} else {
				offset += n;
				writtenFrames += n / channels;
				stalledSince = System.nanoTime();
			}
		}
	}

	private long playedFrames() {
		long head = Integer.toUnsignedLong(track.getPlaybackHeadPosition());
		if (head < previousHead) headWrap += 1L << 32;
		previousHead = head;
		return headWrap + head;
	}

	@Override public void finish() throws IOException {
		if (track == null) return;
		checkCancelled();
		long played = playedFrames();
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && played == 0
				&& writtenFrames < track.getBufferSizeInFrames()) {
			// Before API 31 a very short file may not reach the streaming start threshold.
			// MODE_STREAM stop plays its queued tail. No padding is sent to either consumer.
			track.stop();
			long deadline = System.nanoTime() + writtenFrames * 1_000_000_000L / rate + 150_000_000L;
			while (System.nanoTime() < deadline) waitBriefly();
			return;
		}
		long lastProgress = System.nanoTime();
		while (played < writtenFrames) {
			waitBriefly();
			long next = playedFrames();
			if (next != played) lastProgress = System.nanoTime();
			else if (System.nanoTime() - lastProgress > 3_000_000_000L) throw new IOException("Audio playback did not finish");
			played = next;
		}
	}

	private void checkCancelled() throws InterruptedIOException {
		if (cancelled || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Playback cancelled");
	}
	private void waitBriefly() throws InterruptedIOException {
		checkCancelled();
		try { Thread.sleep(5); }
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Playback cancelled");
		}
	}
	@Override public void cancel() { cancelled = true; }
	@Override public void close() {
		if (track == null) return;
		try { track.pause(); track.flush(); }
		finally { track.release(); track = null; }
	}
}
