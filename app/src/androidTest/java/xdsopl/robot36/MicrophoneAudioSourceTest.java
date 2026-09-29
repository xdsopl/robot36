package xdsopl.robot36;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Device regression coverage; grant the target app microphone permission before running. */
@RunWith(AndroidJUnit4.class)
public class MicrophoneAudioSourceTest {
	private void capture(int rate, int channels, int encoding) throws Exception {
		Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
		assertEquals("Grant microphone permission before running device tests",
				PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO));
		MicrophoneAudioSource source = new MicrophoneAudioSource(rate, channels, MediaRecorder.AudioSource.MIC, encoding);
		CountDownLatch released = new CountDownLatch(1);
		AtomicReference<String> invalid = new AtomicReference<>();
		int[] blocks = {0};
		source.setAudioDataListener(new AudioSource.AudioDataListener() {
			@Override public void onAudioDataAvailable(float[] pcm, int frames, int actualRate, int actualChannels) {
				if (actualRate != source.getFormat().getSampleRate() || actualChannels != channels
						|| frames <= 0 || frames > pcm.length / channels)
					invalid.set("Incorrect format or valid frame count");
				for (int i = 0; i < frames * actualChannels; ++i)
					if (Float.isNaN(pcm[i]) || Float.isInfinite(pcm[i]) || Math.abs(pcm[i]) > 1)
						invalid.set("Invalid microphone PCM scale or stale buffer contents");
				// Simulate Decoder's destructive access to the borrowed buffer.
				Arrays.fill(pcm, Float.NaN);
				if (++blocks[0] == 5) source.release();
			}
			@Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
				if (state == AudioSource.AudioSourceState.ERROR) source.release();
				if (state == AudioSource.AudioSourceState.RELEASED) released.countDown();
			}
		});
		try {
			source.prepare();
			source.start();
			assertTrue("Capture did not stop", released.await(5, TimeUnit.SECONDS));
			assertNull(source.getFailure());
			assertNull(invalid.get());
			assertEquals(5, blocks[0]);
			assertEquals(AudioSource.AudioSourceState.RELEASED, source.getState());
		} finally {
			source.release();
			assertTrue("Recorder was not released", released.await(5, TimeUnit.SECONDS));
		}
	}

	@Test public void pcm16MonoCanReuseTheDecoderBuffer() throws Exception {
		capture(44100, 1, AudioFormat.ENCODING_PCM_16BIT);
	}
	@Test public void floatMonoCanReuseTheDecoderBuffer() throws Exception {
		capture(48000, 1, AudioFormat.ENCODING_PCM_FLOAT);
	}
	@Test public void pcm16StereoDeliversCompleteFrames() throws Exception {
		capture(48000, 2, AudioFormat.ENCODING_PCM_16BIT);
	}
}
