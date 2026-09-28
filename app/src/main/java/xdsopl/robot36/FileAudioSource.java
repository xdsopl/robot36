package xdsopl.robot36;

import android.content.Context;
import android.net.Uri;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.LockSupport;

/** Chunk-aware RIFF/WAVE PCM reader. Pacing never changes or drops samples. */
public class FileAudioSource extends WorkerAudioSource {
    private final Context context;
    private final Uri uri;
    private final File file;
    private final DecodePacing pacing;
    private InputStream stream;
    private int encoding, bits, blockAlign;
    private long remaining;

    public FileAudioSource(Context context, Uri uri, DecodePacing pacing) {
        this.context = context.getApplicationContext();
        this.uri = uri;
        this.file = null;
        this.pacing = pacing;
    }

    public FileAudioSource(File file, DecodePacing pacing) {
        this.context = null;
        this.uri = null;
        this.file = file;
        this.pacing = pacing;
    }

    @Override public AudioSourceType getType() { return AudioSourceType.FILE; }

    @Override protected PcmFormat openInput() throws IOException {
        InputStream input = file != null ? new FileInputStream(file) : context.getContentResolver().openInputStream(uri);
        if (input == null) throw new IOException("Cannot open audio file");
        stream = new BufferedInputStream(input);
        byte[] header = readExactly(12);
        if (!tag(header, 0).equals("RIFF") || !tag(header, 8).equals("WAVE"))
            throw new IOException("Expected a RIFF/WAVE file; compressed formats are not supported yet");
        long riffRemaining = unsignedInt(header, 4) - 4;
        PcmFormat format = null;
        while (!cancelled() && riffRemaining >= 8) {
            byte[] chunk = readExactly(8);
            long length = unsignedInt(chunk, 4);
            long padded = length + (length & 1);
            riffRemaining -= 8;
            if (padded > riffRemaining) throw new IOException("WAV chunk exceeds RIFF size");
            String id = tag(chunk, 0);
            if (id.equals("fmt ")) {
                if (length < 16) throw new IOException("Invalid WAV format chunk");
                byte[] fmt = readExactly(16);
                ByteBuffer b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN);
                encoding = b.getShort() & 65535;
                int channels = b.getShort() & 65535;
                int rate = b.getInt();
                long byteRate = b.getInt() & 0xffffffffL;
                blockAlign = b.getShort() & 65535;
                bits = b.getShort() & 65535;
                if (channels != 1 && channels != 2) throw new IOException("WAV must be mono or stereo");
                if (rate < 8000 || rate > 192000) throw new IOException("Unsupported WAV sample rate: " + rate);
                if (!((encoding == 1 && (bits == 8 || bits == 16 || bits == 24 || bits == 32))
                        || (encoding == 3 && bits == 32)))
                    throw new IOException("Unsupported WAV encoding/bit depth: " + encoding + "/" + bits);
                if (blockAlign != channels * (bits / 8) || byteRate != (long) rate * blockAlign)
                    throw new IOException("Invalid WAV frame alignment");
                format = new PcmFormat(rate, channels);
                skipExactly(padded - 16);
            } else if (id.equals("data")) {
                if (format == null) throw new IOException("WAV data precedes format");
                if (length % blockAlign != 0) throw new IOException("Incomplete WAV PCM frame");
                remaining = length;
                return format;
            } else {
                skipExactly(padded);
            }
            riffRemaining -= padded;
        }
        throw new IOException("WAV has no PCM data chunk");
    }

    @Override protected void readInput() throws IOException {
        PcmFormat format = getFormat();
        int framesPerBlock = Math.max(1, format.getSampleRate() / 50);
        byte[] bytes = new byte[framesPerBlock * blockAlign];
        float[] pcm = new float[framesPerBlock * format.getChannels()];
        long framesSent = 0;
        long start = System.nanoTime();
        while (!cancelled() && remaining > 0) {
            int count = (int) Math.min(remaining, bytes.length);
            readFully(bytes, count);
            if (cancelled()) break;
            remaining -= count;
            int frames = count / blockAlign;
            ByteBuffer b = ByteBuffer.wrap(bytes, 0, count).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < frames * format.getChannels(); ++i) {
                float value;
                if (encoding == 3) value = b.getFloat();
                else if (bits == 8) value = ((b.get() & 255) - 128) / 128.0f;
                else if (bits == 16) value = b.getShort() / 32768.0f;
                else if (bits == 24) {
                    int v = (b.get() & 255) | ((b.get() & 255) << 8) | (b.get() << 16);
                    value = v / 8388608.0f;
                } else value = b.getInt() / 2147483648.0f;
                pcm[i] = Float.isNaN(value) || Float.isInfinite(value) ? 0 : value;
            }
            emit(pcm, frames);
            framesSent += frames;
            if (pacing == DecodePacing.REALTIME) {
                long deadline = start + framesSent * 1_000_000_000L / format.getSampleRate();
                long wait;
                while (!cancelled() && (wait = deadline - System.nanoTime()) > 0)
                    LockSupport.parkNanos(wait);
            }
        }
    }

    private static String tag(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }
    private static long unsignedInt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(offset) & 0xffffffffL;
    }
    private byte[] readExactly(int count) throws IOException {
        byte[] bytes = new byte[count];
        readFully(bytes, count);
        return bytes;
    }
    private void readFully(byte[] bytes, int count) throws IOException {
        int offset = 0;
        while (offset < count) {
            if (cancelled()) throw new java.io.InterruptedIOException("File input cancelled");
            int n = stream.read(bytes, offset, count - offset);
            if (n < 0) throw new EOFException("Truncated WAV file");
            if (n == 0) {
                int value = stream.read();
                if (value < 0) throw new EOFException("Truncated WAV file");
                bytes[offset++] = (byte) value;
            } else offset += n;
        }
    }
    private void skipExactly(long count) throws IOException {
        byte[] scratch = new byte[4096];
        while (count > 0) {
            int n = (int) Math.min(count, scratch.length);
            readFully(scratch, n);
            count -= n;
        }
    }
    @Override protected void closeInput() throws IOException {
        if (stream != null) {
            InputStream closing = stream;
            stream = null;
            closing.close();
        }
    }
}
