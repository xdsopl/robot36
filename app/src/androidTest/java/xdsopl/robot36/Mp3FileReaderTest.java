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
public class Mp3FileReaderTest {
	static Context context() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
	static File fixture(String asset) throws IOException {
		// A misleading suffix also verifies that detection uses contents, not extensions.
		File file = File.createTempFile("mp3-reader-", ".wav", context().getCacheDir());
		try (InputStream in = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("mp3/" + asset);
				FileOutputStream out = new FileOutputStream(file)) {
			byte[] buffer = new byte[4096];
			int count;
			while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
		}
		return file;
	}

	private void checkAudio(String asset, int rate, int... frequencies) throws Exception {
		File file = fixture(asset);
		try {
			Uri uri = Uri.fromFile(file);
			assertEquals(AudioFileReaders.Type.MP3, AudioFileReaders.detect(context(), uri));
			try (PcmFileReader reader = AudioFileReaders.open(context(), uri, AudioFileReaders.Type.MP3)) {
				int channels = frequencies.length;
				assertEquals(rate, reader.getFormat().getSampleRate());
				assertEquals(channels, reader.getFormat().getChannels());
				float[] all = new float[rate * channels], block = new float[173 * channels + channels - 1];
				int total = 0;
				while (true) {
					Arrays.fill(block, Float.NaN);
					int frames = reader.read(block);
					assertTrue(frames >= 0 && frames <= 173);
					for (int i = frames * channels; i < block.length; ++i) assertTrue(Float.isNaN(block[i]));
					if (frames == 0) break;
					System.arraycopy(block, 0, all, total * channels, frames * channels);
					total += frames;
				}
				assertEquals(0, reader.read(block));
				// Allow MP3 encoder/decoder padding, but reject missing initial/final codec frames.
				assertTrue("Audio tail lost: " + total, total >= rate * .58);
				assertTrue("Unexpected extra audio: " + total, total <= rate * .8);
				for (int channel = 0; channel < channels; ++channel) {
					int begin = rate / 10, end = rate / 2, crossings = 0;
					float peak = 0;
					for (int frame = begin; frame < end; ++frame) {
						float value = all[frame * channels + channel];
						assertFalse(Float.isNaN(value) || Float.isInfinite(value));
						peak = Math.max(peak, Math.abs(value));
						if (value > 0 && all[(frame - 1) * channels + channel] <= 0) ++crossings;
					}
					assertTrue("PCM scale changed: " + peak, peak > .15f && peak < .4f);
					assertEquals(frequencies[channel], crossings * (double) rate / (end - begin), 15);
				}
			}
		} finally { file.delete(); }
	}

	@Test public void decodesMonoCbrWithActualRateAndCompleteTail() throws Exception { checkAudio("mono-cbr.mp3", 32000, 1500); }
	@Test public void decodesStereoVbrWithoutMixingChannels() throws Exception { checkAudio("stereo-vbr.mp3", 44100, 1200, 2300); }

	@Test public void realtimeMonitoringAndTurboDeliverTheSameMp3Frames() throws Exception {
		File file = fixture("silence.mp3");
		int normalFrames = 0;
		try {
			for (boolean turbo : new boolean[] {false, true}) {
				CountDownLatch released = new CountDownLatch(1);
				AtomicInteger received = new AtomicInteger();
				AtomicReference<String> failure = new AtomicReference<>();
				AudioSessionManager manager = new AudioSessionManager(new AudioSessionManager.SessionCallback() {
					@Override public void onPcmDataAvailable(float[] pcm, int frames, int rate, int channels, long session) {
						received.addAndGet(frames);
						Arrays.fill(pcm, Float.NaN);
					}
					@Override public void onSourceStateChanged(AudioSource.AudioSourceState state, String message, long session) {
						if (state == AudioSource.AudioSourceState.ERROR) failure.set(message);
						if (state == AudioSource.AudioSourceState.RELEASED) released.countDown();
					}
				});
				try {
					long start = System.nanoTime();
					manager.startSession(new FileAudioSource(() -> AudioFileReaders.open(context(), Uri.fromFile(file), AudioFileReaders.Type.MP3), turbo),
							turbo ? null : new AudioTrackPlayer());
					assertTrue(released.await(5, TimeUnit.SECONDS));
					assertNull(failure.get());
					if (turbo) assertEquals(normalFrames, received.get());
					else {
						assertTrue(System.nanoTime() - start >= 550_000_000L);
						normalFrames = received.get();
						assertTrue(normalFrames >= 18560);
					}
				} finally { manager.release(); }
			}
		} finally { file.delete(); }
	}

	@Test public void stoppingMp3AfterOneBlockReleasesWithoutCompletion() throws Exception {
		File file = fixture("stereo-vbr.mp3");
		FileAudioSource source = new FileAudioSource(() -> AudioFileReaders.open(context(), Uri.fromFile(file), AudioFileReaders.Type.MP3), true);
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

	@Test public void invalidMp3DoesNotOpenAsAnAudioReader() throws Exception {
		File file = File.createTempFile("bad-audio-", ".mp3", context().getCacheDir());
		try {
			try (FileOutputStream out = new FileOutputStream(file)) { out.write(new byte[64]); }
			assertThrows(IOException.class, () -> AudioFileReaders.detect(context(), Uri.fromFile(file)));
			assertThrows(IOException.class, () -> MediaCodecFileReader.open(context(), Uri.fromFile(file)));
		} finally { file.delete(); }
	}
}
