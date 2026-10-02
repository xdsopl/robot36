package xdsopl.robot36;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

/** Streams file PCM with realtime pacing or unrestricted Turbo delivery. */
public final class FileAudioSource extends WorkerAudioSource {
	public interface ReaderFactory {
		/**
		 * Opens a fresh reader with its PCM format established, on the source worker.
		 * The source owns the returned reader. The factory must close any resources
		 * it opens if initialization fails before returning a reader.
		 */
		PcmFileReader open() throws IOException;
	}
	private final ReaderFactory readerFactory;
	private final boolean turbo;
	private PcmFileReader reader;

	public FileAudioSource(ReaderFactory readerFactory, boolean turbo) {
		this.readerFactory = readerFactory;
		this.turbo = turbo;
	}

	@Override protected PcmFormat openInput() throws IOException {
		reader = readerFactory.open();
		if (reader == null) throw new IOException("Cannot open audio file");
		return reader.getFormat();
	}

	@Override protected void readInput() throws IOException {
		int rate = getFormat().getSampleRate();
		float[] pcm = new float[Math.max(1, rate / 50) * getFormat().getChannels()];
		long start = System.nanoTime(), framesSent = 0;
		while (!cancelled()) {
			int frames = reader.read(pcm);
			if (frames == 0) return; // No invented samples or decoder flushing at EOF.
			emit(pcm, frames);
			if (turbo) continue;
			framesSent += frames;
			// An absolute deadline avoids accumulating decoder/playback processing time.
			long deadline = start + framesSent / rate * 1_000_000_000L
					+ framesSent % rate * 1_000_000_000L / rate;
			try {
				long remaining;
				while (!cancelled() && (remaining = deadline - System.nanoTime()) > 0)
					TimeUnit.NANOSECONDS.sleep(remaining);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new InterruptedIOException("File playback cancelled");
			}
		}
	}

	@Override protected void closeInput() throws IOException {
		if (reader != null) reader.close();
	}
}
