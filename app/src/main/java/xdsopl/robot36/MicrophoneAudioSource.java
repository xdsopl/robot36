package xdsopl.robot36;

import android.media.AudioRecord;

/** Microphone configuration; PCM reads and cancellation belong to AudioRecordSource. */
public class MicrophoneAudioSource extends AudioRecordSource {
	private final int preset;

	public MicrophoneAudioSource(int sampleRate, int channels, int preset, int encoding) {
		super(sampleRate, channels, encoding);
		this.preset = preset;
	}

	@Override protected AudioRecord createRecord(int channelMask, int bufferBytes) {
		return new AudioRecord(preset, sampleRate, channelMask, encoding, bufferBytes);
	}
}
