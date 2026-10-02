package xdsopl.robot36;

import android.content.Context;
import android.net.Uri;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FlacFileReaderTest {
	static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
	static File fixture(String asset) throws IOException {
		// The suffix must not make a lossless file show the MP3 warning.
		File file = File.createTempFile("flac-reader-", ".mp3", context().getCacheDir());
		try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("flac/" + asset);
				FileOutputStream out = new FileOutputStream(file)) {
			byte[] buffer = new byte[4096];
			int count;
			while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
		}
		return file;
	}

	private static int sample(int frame, int channel, int bits) {
		if (frame == 0) return -(1 << (bits - 1));
		if (frame == 1) return (1 << (bits - 1)) - 1;
		int phase = frame * (channel == 0 ? 31 : 47) % 2048 - 1024;
		return (phase << (bits - 12)) + (bits == 24 ? frame % 251 - 125 : 0);
	}

	private void checkAudio(String asset, int rate, int channels, int bits, int expectedFrames) throws Exception {
		File file = fixture(asset);
		try {
			Uri uri = Uri.fromFile(file);
			assertEquals(AudioFileReaders.Type.FLAC, AudioFileReaders.detect(context(), uri));
			try (PcmFileReader reader = AudioFileReaders.open(context(), uri, AudioFileReaders.Type.FLAC)) {
				assertEquals(rate, reader.getFormat().getSampleRate());
				assertEquals(channels, reader.getFormat().getChannels());
				float[] block = new float[173 * channels + channels - 1];
				int total = 0;
				while (true) {
					Arrays.fill(block, Float.NaN);
					int frames = reader.read(block);
					assertTrue(frames >= 0 && frames <= 173);
					for (int i = frames * channels; i < block.length; ++i) assertTrue(Float.isNaN(block[i]));
					if (frames == 0) break;
					assertTrue("Extra decoded frames", total + frames <= expectedFrames);
					for (int frame = 0; frame < frames; ++frame) {
						for (int channel = 0; channel < channels; ++channel) {
							float expected = sample(total + frame, channel, bits) / (float) (1 << (bits - 1));
							// System extractors can quantize 24-bit FLAC to PCM16. Allow at
							// most one PCM16 step, while 16-bit input must remain exact.
							assertEquals("PCM frame " + (total + frame) + ", channel " + channel,
									expected, block[frame * channels + channel], bits > 16 ? 1f / 32768 : 0f);
						}
					}
					total += frames;
				}
				assertEquals("Missing final PCM frames", expectedFrames, total);
				assertEquals(0, reader.read(block));
			}
		} finally { file.delete(); }
	}

	@Test public void mono16BitMatchesOriginalPcmThroughEof() throws Exception {
		checkAudio("mono-16.flac", 32000, 1, 16, 10037);
	}

	@Test public void stereo24BitMatchesPcmWithinPlatformPrecision() throws Exception {
		checkAudio("stereo-24.flac", 48000, 2, 24, 15013);
	}

	@Test public void realtimeAndTurboDeliverEveryFrameIncludingShortTail() throws Exception {
		File file = fixture("silence.flac");
		try {
			for (boolean turbo : new boolean[] {false, true}) {
				CountDownLatch released = new CountDownLatch(1);
				AtomicInteger received = new AtomicInteger(), completed = new AtomicInteger();
				AtomicReference<String> failure = new AtomicReference<>();
				AudioSessionManager manager = new AudioSessionManager(new AudioSessionManager.SessionCallback() {
					@Override public void onPcmDataAvailable(float[] pcm, int frames, int rate, int channels, long session) {
						received.addAndGet(frames);
						Arrays.fill(pcm, Float.NaN); // Monitoring must consume PCM before the decoder mutates it.
					}
					@Override public void onSourceStateChanged(AudioSource.AudioSourceState state, String message, long session) {
						if (state == AudioSource.AudioSourceState.ERROR) failure.set(message);
						if (state == AudioSource.AudioSourceState.COMPLETED) completed.incrementAndGet();
						if (state == AudioSource.AudioSourceState.RELEASED) released.countDown();
					}
				});
				try {
					long start = System.nanoTime();
					manager.startSession(new FileAudioSource(() -> AudioFileReaders.open(context(), Uri.fromFile(file), AudioFileReaders.Type.FLAC), turbo),
							turbo ? null : new AudioTrackPlayer());
					assertTrue(released.await(5, TimeUnit.SECONDS));
					assertNull(failure.get());
					assertEquals(24017, received.get());
					assertEquals(1, completed.get());
					if (!turbo) assertTrue(System.nanoTime() - start >= 500_000_000L);
				} finally { manager.release(); }
			}
		} finally { file.delete(); }
	}

	@Test public void stoppingFlacReleasesWithoutCompletion() throws Exception {
		File file = fixture("stereo-24.flac");
		FileAudioSource source = new FileAudioSource(() -> AudioFileReaders.open(context(), Uri.fromFile(file), AudioFileReaders.Type.FLAC), true);
		CountDownLatch released = new CountDownLatch(1);
		AtomicInteger blocks = new AtomicInteger(), completions = new AtomicInteger();
		source.setAudioDataListener(new AudioSource.AudioDataListener() {
			@Override public void onAudioDataAvailable(float[] pcm, int frames, int rate, int channels) { blocks.incrementAndGet(); source.stop(); }
			@Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
				if (state == AudioSource.AudioSourceState.COMPLETED) completions.incrementAndGet();
				if (state == AudioSource.AudioSourceState.STOPPED || state == AudioSource.AudioSourceState.ERROR
						|| state == AudioSource.AudioSourceState.COMPLETED) source.release();
				if (state == AudioSource.AudioSourceState.RELEASED) released.countDown();
			}
		});
		try {
			source.prepare(); source.start();
			assertTrue(released.await(3, TimeUnit.SECONDS));
			assertNull(source.getFailure());
			assertEquals(1, blocks.get());
			assertEquals(0, completions.get());
		} finally { source.release(); file.delete(); }
	}
}
