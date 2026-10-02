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
	enum Type { WAV, MP3 }

	private AudioFileReaders() { }

	static Type detect(Context context, Uri uri) throws IOException {
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
		}
		MediaExtractor extractor = new MediaExtractor();
		try {
			checkInterrupted();
			extractor.setDataSource(context, uri, null);
			mp3Track(extractor);
			return Type.MP3;
		} finally { extractor.release(); }
	}

	static int mp3Track(MediaExtractor extractor) throws IOException {
		for (int i = 0; i < extractor.getTrackCount(); ++i) {
			checkInterrupted();
			String mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
			if (MediaFormat.MIMETYPE_AUDIO_MPEG.equals(mime)) return i;
		}
		throw new IOException("Unsupported audio file. Choose a PCM/IEEE-float WAV or MP3 file.");
	}

	static PcmFileReader open(Context context, Uri uri, Type type) throws IOException {
		checkInterrupted();
		if (type == Type.WAV) return WavFileReader.open(context.getContentResolver().openInputStream(uri));
		if (type == Type.MP3) return MediaCodecFileReader.open(context, uri);
		throw new IOException("Audio file type has not been identified");
	}

	static void checkInterrupted() throws InterruptedIOException {
		if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Audio file reading cancelled");
	}
}
