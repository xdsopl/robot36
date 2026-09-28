package xdsopl.robot36;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

/**
 * Helper class using AudioTrack to play float PCM audio samples through speakers.
 * Used during non-Turbo file decoding to provide real-time audio playback.
 */
public class AudioTrackPlayer {

    private AudioTrack audioTrack;
    private short[] pcmShortBuffer;
    private boolean isPlaying;

    public synchronized void prepare(int sampleRate, int channels) {
        stop();

        int channelConfig = channels == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBufferSize, sampleRate * channels * 2);

        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfig)
                .build();

        audioTrack = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            audioTrack.play();
            isPlaying = true;
        } else {
            audioTrack.release();
            audioTrack = null;
            isPlaying = false;
        }
    }

    public synchronized void write(float[] floatBuffer, int frameCount, int channels) {
        if (!isPlaying || audioTrack == null) {
            return;
        }

        int totalSamples = frameCount * channels;
        if (pcmShortBuffer == null || pcmShortBuffer.length < totalSamples) {
            pcmShortBuffer = new short[totalSamples];
        }

        for (int i = 0; i < totalSamples; i++) {
            float sample = floatBuffer[i];
            if (sample > 1.0f) sample = 1.0f;
            if (sample < -1.0f) sample = -1.0f;
            pcmShortBuffer[i] = (short) (sample * 32767.0f);
        }

        audioTrack.write(pcmShortBuffer, 0, totalSamples);
    }

    public synchronized void stop() {
        isPlaying = false;
        if (audioTrack != null) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                }
                audioTrack.release();
            } catch (Exception ignored) {
            } finally {
                audioTrack = null;
            }
        }
    }

    public synchronized boolean isPlaying() {
        return isPlaying;
    }
}
