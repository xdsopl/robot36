package xdsopl.robot36;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Serializes resource access and callbacks for one single-use input session. */
abstract class WorkerAudioSource implements AudioSource {
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "AudioSource"));
	private volatile AudioSourceState state = AudioSourceState.NEW;
	private volatile PcmFormat format;
	private volatile IOException failure;
	private volatile boolean cancelled;
	private volatile boolean releasing;
	private Thread workerThread;
	private AudioDataListener listener;
	private boolean prepared, started;
	// Only the worker closes resources, including partially opened inputs.
	private boolean closed;

	@Override public synchronized void setAudioDataListener(AudioDataListener listener) {
		if (prepared || cancelled || releasing || listener == null)
			throw new IllegalStateException("Register a listener before preparation");
		this.listener = listener;
	}

	@Override public void prepare() throws IOException {
		Future<?> task;
		synchronized (this) {
			if (prepared || cancelled || releasing || listener == null)
				throw new IllegalStateException("Source is single-use");
			prepared = true;
			task = worker.submit(() -> {
				enterWorker();
				try {
					if (!publishActive(AudioSourceState.INITIALIZING)) return;
					if (cancelled) return;
					format = openInput();
					if (format == null) throw new IOException("Input did not provide a PCM format");
					publishActive(AudioSourceState.READY);
				} catch (Exception e) {
					fail(e);
				} finally {
					if (cancelled || failure != null) finish();
					leaveWorker();
				}
			});
		}
		try {
			task.get();
		} catch (InterruptedException e) {
			stop();
			Thread.currentThread().interrupt();
			throw new InterruptedIOException("Audio preparation interrupted");
		} catch (ExecutionException e) {
			throw new IOException("Audio preparation failed", e.getCause());
		}
		if (failure != null) throw failure;
		if (cancelled) throw new InterruptedIOException("Audio preparation cancelled");
	}

	@Override public synchronized void start() {
		if (state != AudioSourceState.READY || started || cancelled || releasing)
			throw new IllegalStateException("Source is not ready");
		started = true;
		worker.execute(() -> {
			enterWorker();
			try {
				if (publishActive(AudioSourceState.RUNNING) && !cancelled) {
					readInput();
				}
			} catch (Exception e) {
				fail(e);
			} finally {
				finish();
				leaveWorker();
			}
		});
	}

	@Override public void stop() {
		synchronized (this) {
			if (!cancelLocked()) return;
			worker.execute(this::finish);
		}
		// A platform read may need more than Thread.interrupt(). Never close it here.
		interruptInput();
	}

	@Override public void release() {
		boolean interrupt;
		synchronized (this) {
			if (releasing) return;
			releasing = true;
			interrupt = cancelLocked();
			if (!worker.isShutdown()) worker.execute(this::finish);
		}
		if (interrupt) interruptInput();
	}

	private boolean cancelLocked() {
		if (cancelled || state == AudioSourceState.STOPPED || state == AudioSourceState.COMPLETED
				|| state == AudioSourceState.ERROR || state == AudioSourceState.RELEASED) return false;
		cancelled = true;
		if (workerThread != null) workerThread.interrupt();
		return true;
	}

	private synchronized void enterWorker() {
		workerThread = Thread.currentThread();
	}

	private synchronized void leaveWorker() {
		workerThread = null;
		Thread.interrupted();
	}

	private boolean publishActive(AudioSourceState next) {
		synchronized (this) {
			if (cancelled || releasing) return false;
			state = next;
		}
		listener.onStatusChanged(next, "");
		return true;
	}

	private void fail(Exception error) {
		synchronized (this) {
			if (cancelled || failure != null) return;
			failure = error instanceof IOException ? (IOException) error : new IOException("Audio input failed: " + error.getMessage(), error);
			state = AudioSourceState.ERROR;
		}
		listener.onStatusChanged(AudioSourceState.ERROR, failure.getMessage() == null ? "" : failure.getMessage());
	}

	private void finish() {
		if (state == AudioSourceState.RELEASED) return;
		Thread.interrupted();
		if (!closed) {
			closed = true;
			try { closeInput(); } catch (Exception e) { fail(e); }
		}
		if (cancelled && state != AudioSourceState.ERROR && state != AudioSourceState.COMPLETED
				&& state != AudioSourceState.STOPPED)
			publish(AudioSourceState.STOPPED, "");
		else if (state == AudioSourceState.RUNNING)
			publishActive(AudioSourceState.COMPLETED);
		if (releasing) {
			publish(AudioSourceState.RELEASED, "");
			synchronized (this) { worker.shutdown(); }
		}
	}

	private void publish(AudioSourceState next, String message) {
		state = next;
		if (listener != null) listener.onStatusChanged(next, message == null ? "" : message);
	}

	protected final boolean cancelled() { return cancelled; }

	protected final void emit(float[] pcm, int frames) {
		if (cancelled) return;
		PcmFormat actual = getFormat();
		if (frames <= 0 || frames > pcm.length / actual.getChannels())
			throw new IllegalArgumentException("Invalid PCM frame count");
		listener.onAudioDataAvailable(pcm, frames, actual.getSampleRate(), actual.getChannels());
	}

	@Override public final AudioSourceState getState() { return state; }
	@Override public final IOException getFailure() { return failure; }
	@Override public final PcmFormat getFormat() {
		if (format == null) throw new IllegalStateException("Format not established");
		return format;
	}

	/** Nonblocking cancellation signal; resource release belongs to closeInput(). */
	protected void interruptInput() { }
	protected abstract PcmFormat openInput() throws IOException;
	protected abstract void readInput() throws IOException;
	protected abstract void closeInput() throws IOException;
}
