package xdsopl.robot36;

import android.content.Context;
import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Platform decoding into PCM frames. All native access stays on the source worker. */
final class MediaCodecFileReader implements PcmFileReader {
	private MediaExtractor extractor;
	private MediaCodec codec;
	private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
	private AudioSource.PcmFormat format;
	private int encoding, bytesPerFrame;
	private int outputIndex = -1;
	private ByteBuffer output;
	private boolean inputEnded, outputEnded, closed;

	private MediaCodecFileReader() { }

	static MediaCodecFileReader open(Context context, Uri uri) throws IOException {
		MediaCodecFileReader reader = new MediaCodecFileReader();
		try {
			reader.extractor = new MediaExtractor();
			reader.extractor.setDataSource(context, uri, null);
			int track = AudioFileReaders.mp3Track(reader.extractor);
			MediaFormat input = reader.extractor.getTrackFormat(track);
			reader.extractor.selectTrack(track);
			// Only MP3 is enabled. Other codecs need their own compatibility tests first.
			reader.codec = MediaCodec.createDecoderByType(input.getString(MediaFormat.KEY_MIME));
			input.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
			reader.codec.configure(input, null, null, 0);
			reader.codec.start();
			// Establish the actual output format and retain the first PCM buffer for read().
			reader.nextOutput();
			if (reader.format == null) throw new IOException("Decoder did not provide a PCM format");
			return reader;
		} catch (IOException | RuntimeException e) {
			try { reader.close(); } catch (RuntimeException closeError) { e.addSuppressed(closeError); }
			throw e;
		}
	}

	@Override public AudioSource.PcmFormat getFormat() { return format; }

	private void updateFormat(MediaFormat actual) throws IOException {
		int rate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE);
		int channels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
		if (rate < 8000 || rate > 192000 || (channels != 1 && channels != 2))
			throw new IOException("Audio must be mono or stereo PCM at 8-192 kHz");
		int pcmEncoding = actual.containsKey(MediaFormat.KEY_PCM_ENCODING)
				? actual.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
		if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT && pcmEncoding != AudioFormat.ENCODING_PCM_FLOAT)
			throw new IOException("Unsupported decoder PCM encoding: " + pcmEncoding);
		if (format != null && (format.getSampleRate() != rate || format.getChannels() != channels))
			throw new IOException("Audio format changed within the file");
		format = new AudioSource.PcmFormat(rate, channels);
		encoding = pcmEncoding;
		bytesPerFrame = channels * (encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2);
	}

	private boolean feedInput() throws IOException {
		if (inputEnded) return false;
		int index = codec.dequeueInputBuffer(0);
		if (index < 0) return false;
		ByteBuffer input = codec.getInputBuffer(index);
		if (input == null) throw new IOException("Decoder input buffer unavailable");
		input.clear();
		int size = extractor.readSampleData(input, 0);
		if (size < 0) {
			codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
			inputEnded = true;
		} else {
			if (size == 0 || size > input.capacity()) throw new IOException("Invalid compressed audio frame size");
			if ((extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0)
				throw new IOException("Encrypted audio is not supported");
			codec.queueInputBuffer(index, 0, size, Math.max(0, extractor.getSampleTime()), 0);
			extractor.advance();
		}
		return true;
	}

	private boolean nextOutput() throws IOException {
		long lastProgress = System.nanoTime();
		while (!outputEnded) {
			AudioFileReaders.checkInterrupted();
			if (feedInput()) lastProgress = System.nanoTime();
			int index = codec.dequeueOutputBuffer(info, 10_000);
			if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
				updateFormat(codec.getOutputFormat());
				lastProgress = System.nanoTime();
			} else if (index >= 0) {
				outputIndex = index;
				updateFormat(codec.getOutputFormat(index));
				outputEnded = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
				if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
					output = codec.getOutputBuffer(index);
					if (output == null || info.offset < 0 || info.size > output.capacity() - info.offset
							|| info.size % bytesPerFrame != 0)
						throw new IOException("Invalid decoder PCM buffer");
					output.limit(info.offset + info.size).position(info.offset);
					output.order(ByteOrder.nativeOrder());
					return true;
				}
				releaseOutput();
				lastProgress = System.nanoTime();
			} else if (System.nanoTime() - lastProgress > 5_000_000_000L) {
				throw new IOException("Audio decoder stopped producing PCM");
			}
		}
		return false;
	}

	@Override public int read(float[] pcm) throws IOException {
		if (closed) throw new IOException("Audio reader is closed");
		int channels = format.getChannels(), capacity = pcm.length / channels;
		if (capacity == 0) throw new IllegalArgumentException("PCM buffer holds no frames");
		int frames = 0;
		while (frames < capacity) {
			AudioFileReaders.checkInterrupted();
			if (output == null && !nextOutput()) break;
			int count = Math.min(capacity - frames, output.remaining() / bytesPerFrame);
			for (int i = frames * channels, end = (frames + count) * channels; i < end; ++i) {
				float sample = encoding == AudioFormat.ENCODING_PCM_FLOAT ? output.getFloat() : output.getShort() / 32768.0f;
				if (Float.isNaN(sample) || Float.isInfinite(sample)) throw new IOException("Non-finite decoded PCM sample");
				pcm[i] = sample;
			}
			frames += count;
			if (!output.hasRemaining()) releaseOutput();
		}
		return frames;
	}

	private void releaseOutput() {
		codec.releaseOutputBuffer(outputIndex, false);
		outputIndex = -1;
		output = null;
	}

	@Override public void close() {
		if (closed) return;
		closed = true;
		output = null;
		try { if (codec != null) codec.release(); }
		finally { if (extractor != null) extractor.release(); }
	}
}
