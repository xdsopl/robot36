package xdsopl.robot36;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

public class FileAudioSourceTest {
	@Test public void turboDeliversEveryFrameWithoutRealtimeWaits() throws Exception {
		int rate = 48000, totalFrames = rate * 20 + 1;
		ByteBuffer data = ByteBuffer.allocate(totalFrames * 4).order(ByteOrder.LITTLE_ENDIAN);
		for (int frame = 0; frame < totalFrames; ++frame) {
			data.putShort((short) frame);
			data.putShort((short) ~frame);
		}
		byte[] wav = WavFileReaderTest.wav(1, 16, 2, rate, data.array());
		FileAudioSource source = new FileAudioSource(() -> new ByteArrayInputStream(wav), true);
		CountDownLatch released = new CountDownLatch(1);
		AtomicInteger received = new AtomicInteger(), completions = new AtomicInteger(), lastFrames = new AtomicInteger();
		AtomicReference<String> invalid = new AtomicReference<>();
		source.setAudioDataListener(new AudioSource.AudioDataListener() {
			@Override public void onAudioDataAvailable(float[] pcm, int frames, int sampleRate, int channels) {
				if (sampleRate != rate || channels != 2 || frames > rate / 50) invalid.set("Format or block size changed");
				int offset = received.getAndAdd(frames);
				for (int i = 0; i < frames; ++i) {
					int frame = offset + i;
					if (pcm[2 * i] != (short) frame / 32768f || pcm[2 * i + 1] != (short) ~frame / 32768f)
						invalid.set("Samples lost, reordered or scaled");
				}
				lastFrames.set(frames);
				Arrays.fill(pcm, Float.NaN); // Decoder may overwrite the borrowed buffer.
			}
			@Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
				if (state == AudioSource.AudioSourceState.COMPLETED) completions.incrementAndGet();
				if (state == AudioSource.AudioSourceState.COMPLETED || state == AudioSource.AudioSourceState.ERROR) source.release();
				if (state == AudioSource.AudioSourceState.RELEASED) released.countDown();
			}
		});
		try {
			source.prepare();
			source.start();
			assertTrue("Turbo waited for the 20-second audio timeline", released.await(3, TimeUnit.SECONDS));
			assertNull(source.getFailure());
			assertNull(invalid.get());
			assertEquals(totalFrames, received.get());
			assertEquals(1, lastFrames.get());
			assertEquals(1, completions.get());
		} finally { source.release(); }
	}

	@Test public void turboCanStopBetweenBlocksWithoutReportingCompletion() throws Exception {
		byte[] wav = WavFileReaderTest.wav(1, 8, 1, 8000, new byte[8000 * 20]);
		FileAudioSource source = new FileAudioSource(() -> new ByteArrayInputStream(wav), true);
		CountDownLatch released = new CountDownLatch(1);
		AtomicInteger blocks = new AtomicInteger(), completions = new AtomicInteger(), stops = new AtomicInteger();
		source.setAudioDataListener(new AudioSource.AudioDataListener() {
			@Override public void onAudioDataAvailable(float[] pcm, int frames, int rate, int channels) {
				blocks.incrementAndGet();
				source.stop();
			}
			@Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
				if (state == AudioSource.AudioSourceState.COMPLETED) completions.incrementAndGet();
				if (state == AudioSource.AudioSourceState.STOPPED) stops.incrementAndGet();
				if (state == AudioSource.AudioSourceState.STOPPED || state == AudioSource.AudioSourceState.ERROR
						|| state == AudioSource.AudioSourceState.COMPLETED) source.release();
				if (state == AudioSource.AudioSourceState.RELEASED) released.countDown();
			}
		});
		try {
			source.prepare();
			source.start();
			assertTrue(released.await(3, TimeUnit.SECONDS));
			assertNull(source.getFailure());
			assertEquals(1, blocks.get());
			assertEquals(1, stops.get());
			assertEquals(0, completions.get());
		} finally { source.release(); }
	}

	@Test public void stoppingDuringPlaybackDrainReleasesMonitorBeforeNextInputOpens() throws Exception {
		byte[] wav = WavFileReaderTest.wav(1, 8, 1, 8000, new byte[] {(byte) 128});
		CountDownLatch draining = new CountDownLatch(1), cancelled = new CountDownLatch(1), nextOpened = new CountDownLatch(1);
		AtomicInteger monitorClosed = new AtomicInteger(), closedAtNextOpen = new AtomicInteger();
		AudioSessionManager manager = new AudioSessionManager(new AudioSessionManager.SessionCallback() {
			@Override public void onPcmDataAvailable(float[] pcm, int frames, int rate, int channels, long generation) { }
			@Override public void onSourceStateChanged(AudioSource.AudioSourceState state, String message, long generation) { }
		});
		try {
			manager.startSession(new FileAudioSource(() -> new ByteArrayInputStream(wav), false), new AudioSessionManager.Monitor() {
				@Override public void write(float[] pcm, int frames, int rate, int channels) { }
				@Override public void finish() throws IOException {
					draining.countDown();
					try { if (!cancelled.await(3, TimeUnit.SECONDS)) throw new IOException("Monitor was not cancelled"); }
					catch (InterruptedException e) { throw new IOException(e); }
				}
				@Override public void cancel() { cancelled.countDown(); }
				@Override public void close() { monitorClosed.incrementAndGet(); }
			});
			assertTrue(draining.await(3, TimeUnit.SECONDS));
			manager.startSession(new FileAudioSource(() -> {
				closedAtNextOpen.set(monitorClosed.get());
				nextOpened.countDown();
				return new ByteArrayInputStream(wav);
			}, false));
			assertTrue(nextOpened.await(3, TimeUnit.SECONDS));
			assertEquals(1, closedAtNextOpen.get());
		} finally { cancelled.countDown(); manager.release(); }
	}

	@Test public void actualFileFormatAndRealtimeDurationSurviveBufferReuse() throws Exception {
		byte[] data = new byte[2 * 2 * 9601]; // Stereo, 200 ms plus a single final frame.
		Arrays.fill(data, (byte) 0);
		byte[] wav = WavFileReaderTest.wav(1, 16, 2, 48000, data);
		FileAudioSource source = new FileAudioSource(() -> new ByteArrayInputStream(wav), false);
		CountDownLatch released = new CountDownLatch(1);
		List<Integer> frameCounts = new ArrayList<>();
		AtomicReference<String> invalid = new AtomicReference<>();
		source.setAudioDataListener(new AudioSource.AudioDataListener() {
			@Override public void onAudioDataAvailable(float[] pcm, int frames, int rate, int channels) {
				if (rate != 48000 || channels != 2) invalid.set("File format changed");
				for (int i = 0; i < frames * channels; ++i) if (pcm[i] != 0) invalid.set("Stale sample");
				frameCounts.add(frames);
				Arrays.fill(pcm, Float.NaN);
			}
			@Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
				if (state == AudioSource.AudioSourceState.COMPLETED || state == AudioSource.AudioSourceState.ERROR) source.release();
				if (state == AudioSource.AudioSourceState.RELEASED) released.countDown();
			}
		});
		try {
			source.prepare();
			long start = System.nanoTime();
			source.start();
			assertTrue(released.await(3, TimeUnit.SECONDS));
			assertTrue("Realtime source ran too fast", System.nanoTime() - start >= 190_000_000L);
			assertNull(source.getFailure());
			assertNull(invalid.get());
			assertEquals(9601, frameCounts.stream().mapToInt(Integer::intValue).sum());
			assertEquals(Integer.valueOf(1), frameCounts.get(frameCounts.size() - 1));
		} finally { source.release(); }
	}

	@Test public void monitorSeesOriginalPcmAndFinishesBeforeSessionCompletion() throws Exception {
		byte[] wav = WavFileReaderTest.wav(1, 8, 1, 8000, new byte[] {(byte) 160});
		List<String> order = new ArrayList<>();
		CountDownLatch released = new CountDownLatch(1);
		AtomicReference<Float> heard = new AtomicReference<>();
		AudioSessionManager.Monitor monitor = new AudioSessionManager.Monitor() {
			@Override public void write(float[] pcm, int frames, int rate, int channels) { heard.set(pcm[0]); order.add("play"); }
			@Override public void finish() { order.add("drain"); }
			@Override public void cancel() { }
			@Override public void close() { order.add("close"); }
		};
		AudioSessionManager manager = new AudioSessionManager(new AudioSessionManager.SessionCallback() {
			@Override public void onPcmDataAvailable(float[] pcm, int frames, int rate, int channels, long session) {
				order.add("decode"); Arrays.fill(pcm, Float.NaN);
			}
			@Override public void onSourceStateChanged(AudioSource.AudioSourceState state, String message, long session) {
				if (state == AudioSource.AudioSourceState.COMPLETED) order.add("complete");
				if (state == AudioSource.AudioSourceState.RELEASED) released.countDown();
			}
		});
		try {
			manager.startSession(new FileAudioSource(() -> new ByteArrayInputStream(wav), false), monitor);
			assertTrue(released.await(3, TimeUnit.SECONDS));
			assertEquals(0.25f, heard.get(), 0);
			assertEquals(Arrays.asList("play", "decode", "drain", "complete", "close"), order);
		} finally { manager.release(); }
	}

	@Test public void playbackFailureClosesBothConsumersWithoutFalseCompletion() throws Exception {
		byte[] wav = WavFileReaderTest.wav(1, 16, 1, 8000, new byte[1600]);
		AtomicInteger errors = new AtomicInteger(), completions = new AtomicInteger(), framesReceived = new AtomicInteger();
		CountDownLatch closed = new CountDownLatch(1);
		AudioSessionManager manager = new AudioSessionManager(new AudioSessionManager.SessionCallback() {
			@Override public void onPcmDataAvailable(float[] pcm, int frames, int rate, int channels, long session) { framesReceived.addAndGet(frames); }
			@Override public void onSourceStateChanged(AudioSource.AudioSourceState state, String message, long session) {
				if (state == AudioSource.AudioSourceState.ERROR) errors.incrementAndGet();
				if (state == AudioSource.AudioSourceState.COMPLETED) completions.incrementAndGet();
			}
		});
		FileAudioSource source = new FileAudioSource(() -> new ByteArrayInputStream(wav), false);
		try {
			manager.startSession(source, new AudioSessionManager.Monitor() {
				@Override public void write(float[] pcm, int frames, int rate, int channels) throws IOException { throw new IOException("Output disconnected"); }
				@Override public void finish() { fail("Failed playback cannot drain"); }
				@Override public void cancel() { }
				@Override public void close() { closed.countDown(); }
			});
			assertTrue(closed.await(3, TimeUnit.SECONDS));
			assertEquals(1, errors.get());
			assertEquals(0, completions.get());
			assertEquals(0, framesReceived.get());
			assertEquals(AudioSource.AudioSourceState.RELEASED, source.getState());
		} finally { manager.release(); }
	}
}
