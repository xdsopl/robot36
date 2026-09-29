package xdsopl.robot36;

import org.junit.Test;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static xdsopl.robot36.AudioSource.AudioSourceState.*;

public class AudioSourceLifecycleTest {
	private static void await(CountDownLatch latch) throws Exception {
		assertTrue("Worker did not reach the expected boundary", latch.await(3, TimeUnit.SECONDS));
	}

	private static void hold(CountDownLatch latch) {
		boolean interrupted = false;
		for (;;) {
			try { latch.await(); break; } catch (InterruptedException e) { interrupted = true; }
		}
		if (interrupted) Thread.currentThread().interrupt();
	}

	private static class Result implements AudioSource.AudioDataListener {
		final AudioSource source;
		final CountDownLatch released = new CountDownLatch(1);
		final List<AudioSource.AudioSourceState> states = Collections.synchronizedList(new ArrayList<>());
		volatile boolean callbackUnderLock;
		Result(AudioSource source) { this.source = source; }
		@Override public void onAudioDataAvailable(float[] pcm, int frames, int rate, int channels) { }
		@Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
			callbackUnderLock |= Thread.holdsLock(source);
			states.add(state);
			if (state == COMPLETED || state == ERROR || state == STOPPED) source.release();
			if (state == RELEASED) released.countDown();
		}
	}

	@Test public void shortStereoBlockUsesActualFramesAndAllowsInPlaceConsumption() throws Exception {
		float[] buffer = {-1, 0.5f, Float.NaN, Float.NaN};
		AtomicReference<float[]> received = new AtomicReference<>();
		AtomicInteger closed = new AtomicInteger();
		WorkerAudioSource source = new WorkerAudioSource() {
			@Override protected PcmFormat openInput() { return new PcmFormat(8000, 2); }
			@Override protected void readInput() { emit(buffer, 1); }
			@Override protected void closeInput() { closed.incrementAndGet(); }
		};
		Result result = new Result(source) {
			@Override public void onAudioDataAvailable(float[] pcm, int frames, int rate, int channels) {
				callbackUnderLock |= Thread.holdsLock(source);
				assertEquals(8000, rate);
				assertEquals(2, channels);
				received.set(Arrays.copyOf(pcm, frames * channels));
				Arrays.fill(pcm, 0);
			}
		};
		source.setAudioDataListener(result);
		try {
			source.prepare();
			source.start();
			await(result.released);
			assertArrayEquals(new float[] {-1, 0.5f}, received.get(), 0);
			assertEquals(0, buffer[0], 0);
			assertEquals(Arrays.asList(INITIALIZING, READY, RUNNING, COMPLETED, RELEASED), result.states);
			assertEquals(1, closed.get());
			assertFalse(result.callbackUnderLock);
			assertNull(source.getFailure());
		} finally { source.release(); }
	}

	@Test public void releaseWaitsForCallbackAndClosesOnce() throws Exception {
		CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
		AtomicInteger closed = new AtomicInteger();
		WorkerAudioSource source = new WorkerAudioSource() {
			@Override protected PcmFormat openInput() { return new PcmFormat(8000, 1); }
			@Override protected void readInput() { emit(new float[] {0.25f}, 1); }
			@Override protected void closeInput() { closed.incrementAndGet(); }
		};
		Result result = new Result(source) {
			@Override public void onAudioDataAvailable(float[] pcm, int frames, int rate, int channels) {
				entered.countDown();
				hold(resume);
			}
		};
		source.setAudioDataListener(result);
		try {
			source.prepare();
			source.start();
			await(entered);
			source.release();
			source.release();
			assertFalse(result.released.await(30, TimeUnit.MILLISECONDS));
			assertEquals(0, closed.get());
			resume.countDown();
			await(result.released);
			assertEquals(1, closed.get());
			assertEquals(Arrays.asList(INITIALIZING, READY, RUNNING, STOPPED, RELEASED), result.states);
		} finally { resume.countDown(); source.release(); }
	}

	@Test public void cancelDuringPreparationNeverPublishesReady() throws Exception {
		CountDownLatch opening = new CountDownLatch(1), resume = new CountDownLatch(1);
		AtomicInteger closed = new AtomicInteger();
		WorkerAudioSource source = new WorkerAudioSource() {
			@Override protected PcmFormat openInput() {
				opening.countDown();
				hold(resume);
				return new PcmFormat(8000, 1);
			}
			@Override protected void readInput() { fail("Cancelled source must never read"); }
			@Override protected void closeInput() { closed.incrementAndGet(); }
		};
		Result result = new Result(source);
		source.setAudioDataListener(result);
		FutureTask<IOException> prepare = new FutureTask<>(() -> {
			try { source.prepare(); return null; } catch (IOException e) { return e; }
		});
		new Thread(prepare).start();
		try {
			await(opening);
			source.release();
			resume.countDown();
			assertTrue(prepare.get(3, TimeUnit.SECONDS) instanceof InterruptedIOException);
			await(result.released);
			assertEquals(Arrays.asList(INITIALIZING, STOPPED, RELEASED), result.states);
			assertEquals(1, closed.get());
			assertNull(source.getFailure());
		} finally { resume.countDown(); source.release(); }
	}

	@Test public void releaseFromInitializingCallbackNeverOpensInput() throws Exception {
		WorkerAudioSource source = new WorkerAudioSource() {
			@Override protected PcmFormat openInput() { fail("Cancelled before opening"); return null; }
			@Override protected void readInput() { fail(); }
			@Override protected void closeInput() { }
		};
		Result result = new Result(source) {
			@Override public void onStatusChanged(AudioSource.AudioSourceState state, String message) {
				if (state == INITIALIZING) source.release();
				super.onStatusChanged(state, message);
			}
		};
		source.setAudioDataListener(result);
		try {
			assertThrows(InterruptedIOException.class, source::prepare);
			await(result.released);
			assertEquals(Arrays.asList(INITIALIZING, STOPPED, RELEASED), result.states);
			assertFalse(result.callbackUnderLock);
		} finally { source.release(); }
	}

	@Test public void preparationFailureRetainsCauseAndCleansUp() throws Exception {
		IOException failure = new IOException("Cannot open test input");
		AtomicInteger closed = new AtomicInteger();
		WorkerAudioSource source = new WorkerAudioSource() {
			@Override protected PcmFormat openInput() throws IOException { throw failure; }
			@Override protected void readInput() { fail(); }
			@Override protected void closeInput() { closed.incrementAndGet(); }
		};
		Result result = new Result(source);
		source.setAudioDataListener(result);
		try {
			assertSame(failure, assertThrows(IOException.class, source::prepare));
			await(result.released);
			assertSame(failure, source.getFailure());
			assertEquals(Arrays.asList(INITIALIZING, ERROR, RELEASED), result.states);
			assertEquals(1, closed.get());
			assertThrows(IllegalStateException.class, source::start);
			assertThrows(IllegalStateException.class, source::prepare);
		} finally { source.release(); }
	}

	@Test public void newSessionWaitsForPreviousCallbackAndRejectsOldGeneration() throws Exception {
		CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1), next = new CountDownLatch(1);
		AtomicInteger closed = new AtomicInteger();
		AtomicReference<Long> firstGeneration = new AtomicReference<>(), lastGeneration = new AtomicReference<>();
		WorkerAudioSource first = new WorkerAudioSource() {
			@Override protected PcmFormat openInput() { return new PcmFormat(8000, 1); }
			@Override protected void readInput() { emit(new float[] {1}, 1); }
			@Override protected void closeInput() { closed.incrementAndGet(); }
		};
		WorkerAudioSource second = new WorkerAudioSource() {
			@Override protected PcmFormat openInput() { return new PcmFormat(16000, 1); }
			@Override protected void readInput() { emit(new float[] {2}, 1); }
			@Override protected void closeInput() { }
		};
		AudioSessionManager manager = new AudioSessionManager(new AudioSessionManager.SessionCallback() {
			@Override public void onPcmDataAvailable(float[] pcm, int frames, int rate, int channels, long session) {
				if (pcm[0] == 1) {
					firstGeneration.set(session);
					entered.countDown();
					hold(resume);
				} else {
					lastGeneration.set(session);
					next.countDown();
				}
			}
			@Override public void onSourceStateChanged(AudioSource.AudioSourceState state, String message, long session) { }
		});
		try {
			manager.startSession(first);
			await(entered);
			manager.stopAndReleaseCurrentSession();
			manager.startSession(second);
			assertFalse(manager.isCurrent(firstGeneration.get()));
			assertFalse(next.await(30, TimeUnit.MILLISECONDS));
			assertEquals(0, closed.get());
			resume.countDown();
			await(next);
			assertEquals(1, closed.get());
			assertTrue(manager.isCurrent(lastGeneration.get()));
			manager.release();
			assertFalse(manager.isCurrent(lastGeneration.get()));
		} finally { resume.countDown(); manager.release(); }
	}
}
