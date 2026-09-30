package xdsopl.robot36;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import androidx.annotation.RequiresApi;

/** Captures eligible playback audio. The foreground service owns the projection. */
@RequiresApi(29)
public final class AudioPlaybackCaptureSource extends AudioRecordSource {
	private final MediaProjection projection;

	public AudioPlaybackCaptureSource(MediaProjection projection) {
		super(48000, 2, AudioFormat.ENCODING_PCM_16BIT);
		this.projection = projection;
	}

	@Override protected AudioRecord createRecord(int channelMask, int bufferBytes) {
		AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
				.addMatchingUsage(AudioAttributes.USAGE_MEDIA)
				.addMatchingUsage(AudioAttributes.USAGE_GAME)
				.addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
				.build();
		return new AudioRecord.Builder()
				.setAudioFormat(new AudioFormat.Builder().setSampleRate(sampleRate)
						.setChannelMask(channelMask).setEncoding(encoding).build())
				.setBufferSizeInBytes(bufferBytes)
				.setAudioPlaybackCaptureConfig(config)
				.build();
	}
}