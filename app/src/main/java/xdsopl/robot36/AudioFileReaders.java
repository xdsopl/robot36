package xdsopl.robot36;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;

/** Selects a reader from file contents, never from the filename or provider MIME type. */
final class AudioFileReaders {
	enum Type { WAV, MP3, FLAC }

	private AudioFileReaders() { }

	static Type detect(Context context, Uri uri) throws IOException {
		boolean flacContainer;
		try (InputStream input = context.getContentResolver().openInputStream(uri)) {
			if (input == null) throw new IOException("Cannot open audio file");
			byte[] header = new byte[12];
			int count = 0, value;
			while (count < header.length) {
				checkInterrupted();
				if ((value = input.read()) < 0) break;
				header[count++] = (byte) value;
			}
			if (count == 12 && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
					&& header[8] == 'W' && header[9] == 'A' && header[10] == 'V' && header[11] == 'E')
				return Type.WAV;
			flacContainer = count >= 4 && header[0] == 'f' && header[1] == 'L' && header[2] == 'a' && header[3] == 'C';
		}
		MediaExtractor extractor = new MediaExtractor();
		try {
			checkInterrupted();
			extractor.setDataSource(context, uri, null);
			return trackType(extractor.getTrackFormat(audioTrack(extractor, flacContainer)), flacContainer);
		} finally { extractor.release(); }
	}

	private static Type trackType(MediaFormat format, boolean flacContainer) {
		String mime = format.getString(MediaFormat.KEY_MIME);
		if (MediaFormat.MIMETYPE_AUDIO_MPEG.equals(mime)) return Type.MP3;
		if (MediaFormat.MIMETYPE_AUDIO_FLAC.equals(mime)) return Type.FLAC;
		// Native FLAC extractors may already decode to PCM. Only accept raw tracks
		// from a recognized FLAC container, not arbitrary unsupported file formats.
		if (flacContainer && MediaFormat.MIMETYPE_AUDIO_RAW.equals(mime)) return Type.FLAC;
		return null;
	}

	static int audioTrack(MediaExtractor extractor, boolean flacContainer) throws IOException {
		for (int i = 0; i < extractor.getTrackCount(); ++i) {
			checkInterrupted();
			if (trackType(extractor.getTrackFormat(i), flacContainer) != null) return i;
		}
		throw new IOException("Unsupported audio file. Choose a PCM/IEEE-float WAV, MP3 or FLAC file.");
	}

	static PcmFileReader open(Context context, Uri uri, Type type) throws IOException {
		checkInterrupted();
		if (type == Type.WAV) return WavFileReader.open(context.getContentResolver().openInputStream(uri));
		if (type == Type.MP3 || type == Type.FLAC) return MediaCodecFileReader.open(context, uri, type);
		throw new IOException("Audio file type has not been identified");
	}

	static void checkInterrupted() throws InterruptedIOException {
		if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Audio file reading cancelled");
	}
}
