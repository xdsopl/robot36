package xdsopl.robot36;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import androidx.annotation.RequiresApi;

/** The foreground service owns the projection; this class owns only its AudioRecord. */
@RequiresApi(29)
public class AudioPlaybackCaptureSource extends MicrophoneAudioSource {
    private final MediaProjection projection;

    public AudioPlaybackCaptureSource(MediaProjection projection, int sampleRate, int channels, int encoding) {
        super(sampleRate, channels, MediaRecorder.AudioSource.DEFAULT, encoding);
        this.projection = projection;
    }

    @Override public AudioSourceType getType() { return AudioSourceType.PLAYBACK_CAPTURE; }

    @Override protected AudioRecord createRecord(int channelMask, int bufferBytes) {
        AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build();
        return new AudioRecord.Builder()
                .setAudioFormat(new AudioFormat.Builder().setEncoding(encoding)
                        .setSampleRate(sampleRate).setChannelMask(channelMask).build())
                .setBufferSizeInBytes(bufferBytes)
                .setAudioPlaybackCaptureConfig(config)
                .build();
    }
}
