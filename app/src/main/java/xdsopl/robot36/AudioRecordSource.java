package xdsopl.robot36;

import android.media.AudioFormat;
import android.media.AudioRecord;
import java.io.IOException;

/** Blocking AudioRecord reads with fixed-scale conversion to the shared PCM contract. */
abstract class AudioRecordSource extends WorkerAudioSource {
	protected final int sampleRate, channels, encoding;
	private final Object recordLock = new Object();
	private AudioRecord record;

	AudioRecordSource(int sampleRate, int channels, int encoding) {
		this.sampleRate = sampleRate;
		this.channels = channels;
		this.encoding = encoding;
	}

	protected abstract AudioRecord createRecord(int channelMask, int bufferBytes);

	@Override protected PcmFormat openInput() throws IOException {
		if (sampleRate <= 0 || (channels != 1 && channels != 2))
			throw new IOException("Unsupported AudioRecord sample rate or channel count");
		if (encoding != AudioFormat.ENCODING_PCM_16BIT && encoding != AudioFormat.ENCODING_PCM_FLOAT)
			throw new IOException("Unsupported AudioRecord PCM encoding");
		int mask = channels == 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO;
		int min = AudioRecord.getMinBufferSize(sampleRate, mask, encoding);
		if (min <= 0) throw new IOException("Unsupported AudioRecord format: " + min);
		int bytesPerFrame = channels * (encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2);
		AudioRecord opened = createRecord(mask, Math.max(min, sampleRate / 5 * bytesPerFrame));
		synchronized (recordLock) { record = opened; }
		if (opened.getState() != AudioRecord.STATE_INITIALIZED)
			throw new IOException("AudioRecord initialization failed");
		return new PcmFormat(opened.getSampleRate(), opened.getChannelCount());
	}

	@Override protected void readInput() throws IOException {
		synchronized (recordLock) {
			if (cancelled()) return;
			record.startRecording();
			if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
				throw new IOException("AudioRecord did not start");
		}
		int channelCount = getFormat().getChannels();
		int count = Math.max(1, getFormat().getSampleRate() / 50) * channelCount;
		float[] pcm = new float[count];
		short[] shorts = encoding == AudioFormat.ENCODING_PCM_16BIT ? new short[count] : null;
		while (!cancelled()) {
			int n = shorts == null
					? record.read(pcm, 0, count, AudioRecord.READ_BLOCKING)
					: record.read(shorts, 0, count, AudioRecord.READ_BLOCKING);
			if (cancelled()) break;
			if (n < 0) throw new IOException("AudioRecord read failed: " + n);
			if (n == 0) continue;
			if (n % channelCount != 0) throw new IOException("Incomplete AudioRecord frame");
			if (shorts != null)
				for (int i = 0; i < n; ++i) pcm[i] = shorts[i] / 32768.0f;
			else
				for (int i = 0; i < n; ++i)
					if (Float.isNaN(pcm[i]) || Float.isInfinite(pcm[i])) pcm[i] = 0;
			emit(pcm, n / channelCount);
		}
	}

	@Override protected void interruptInput() {
		// AudioRecord's native blocking read does not respond to Java interruption.
		// Stop it off the UI thread; release still waits for the read and callback.
		new Thread(() -> {
			synchronized (recordLock) {
				if (record != null && record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
					try { record.stop(); } catch (IllegalStateException ignored) { }
				}
			}
		}, "AudioRecordStop").start();
	}

	@Override protected void closeInput() {
		synchronized (recordLock) {
			if (record == null) return;
			try {
				if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) record.stop();
			} finally {
				record.release();
				record = null;
			}
		}
	}
}
