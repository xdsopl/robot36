package xdsopl.robot36;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Synthetic SSTV only: no recordings or image fixtures are stored in the repository. */
public class DecoderEndOfInputTest {
	private static final class Signal {
		final int rate;
		float[] samples;
		int size;
		double phase;
		Signal(int rate) { this.rate = rate; samples = new float[rate]; }
		void tone(double frequency, double seconds) {
			int count = (int) Math.round(seconds * rate);
			if (size + count > samples.length) samples = Arrays.copyOf(samples, Math.max(size + count, samples.length * 2));
			for (int i = 0; i < count; ++i) {
				samples[size++] = (float) (0.5 * Math.sin(phase));
				phase = (phase + 2 * Math.PI * frequency / rate) % (2 * Math.PI);
			}
		}
		void header(int vis) {
			tone(1900, .3); tone(1200, .01); tone(1900, .3); tone(1200, .03);
			int code = vis | ((Integer.bitCount(vis) & 1) << 7);
			for (int i = 0; i < 8; ++i) tone((code & (1 << i)) == 0 ? 1300 : 1100, .03);
			tone(1200, .03);
		}
	}

	private static Signal signal(String mode, int rate) {
		Signal s = new Signal(rate);
		switch (mode) {
			case "PD 50":
			case "PD 290": {
				boolean longMode = mode.equals("PD 290");
				s.header(longMode ? 94 : 93);
				double channel = longMode ? .2288 : .09152;
				for (int i = 0; i < (longMode ? 308 : 128); ++i) {
					s.tone(1200, .02); s.tone(1500, .00208);
					s.tone(2100, channel); s.tone(1900, channel);
					s.tone(1900, channel); s.tone(1700, channel);
				}
				break;
			}
			case "Robot 36 Color":
				s.header(8);
				for (int i = 0; i < 240; ++i) {
					s.tone(1200, .009); s.tone(1500, .003); s.tone(2100, .088);
					s.tone(i % 2 == 0 ? 1500 : 2300, .0045); s.tone(1900, .0015); s.tone(1900, .044);
				}
				break;
			case "Martin 2":
				s.header(40);
				for (int i = 0; i < 256; ++i) {
					s.tone(1200, .004862); s.tone(1500, .000572);
					for (int channel = 0; channel < 3; ++channel) {
						s.tone(1700 + 200 * channel, .073216); s.tone(1500, .000572);
					}
				}
				break;
			case "Scottie 1":
			case "Scottie 2": {
				boolean scottie1 = mode.equals("Scottie 1");
				double channel = scottie1 ? .138240 : .088064;
				s.header(scottie1 ? 60 : 56); s.tone(1200, .009);
				for (int i = 0; i < 256; ++i) {
					s.tone(1500, .0015); s.tone(1700, channel);
					s.tone(1500, .0015); s.tone(1900, channel);
					s.tone(1200, .009); s.tone(1500, .0015); s.tone(2100, channel);
				}
				break;
			}
			default: throw new AssertionError(mode);
		}
		return s;
	}

	private static Decoder decode(Signal signal, int size, int channels, PixelBuffer image) {
		Decoder decoder = new Decoder(new PixelBuffer(800, 64), image, "Raw", signal.rate);
		for (int start = 0; start < size; start += signal.rate / 50) {
			int frames = Math.min(signal.rate / 50, size - start);
			float[] pcm = new float[frames * channels];
			for (int i = 0; i < frames; ++i)
				for (int c = 0; c < channels; ++c) pcm[i * channels + c] = signal.samples[start + i] / channels;
			decoder.process(pcm, channels == 1 ? 0 : 3);
		}
		return decoder;
	}

	@Test public void eofCompletesBufferedLinesLikeContinuedReception() {
		String[] modes = {"PD 50", "PD 290", "Robot 36 Color", "Martin 2", "Scottie 2", "PD 50", "Robot 36 Color",
				"Scottie 1", "Scottie 1", "Scottie 1", "Scottie 1"};
		int[] rates = {8000, 8000, 8000, 8000, 11025, 44100, 48000, 8000, 11025, 44100, 48000};
		for (int test = 0; test < modes.length; ++test) {
			Signal signal = signal(modes[test], rates[test]);
			int channels = test % 2 + 1;
			PixelBuffer image = new PixelBuffer(800, 616);
			Decoder decoder = decode(signal, signal.size, channels, image);
			assertEquals(modes[test], decoder.currentMode.getName());
			assertTrue(modes[test] + " must reproduce a pending final line", image.line >= 0 && image.line < image.height);
			assertTrue(modes[test], decoder.finish(channels == 1 ? 0 : 3));
			assertEquals(modes[test] + " at " + rates[test] + " Hz", image.height, image.line);
			assertFalse(decoder.finish(channels == 1 ? 0 : 3));

			PixelBuffer reference = new PixelBuffer(800, 616);
			Decoder live = decode(signal, signal.size, channels, reference);
			for (int i = 0; i < 100 && reference.line < reference.height; ++i)
				live.process(new float[(signal.rate / 50) * channels], channels == 1 ? 0 : 3);
			assertEquals(reference.height, reference.line);
			assertFalse("An already complete image must not be emitted again", live.finish(channels == 1 ? 0 : 3));
			assertArrayEquals(modes[test], Arrays.copyOf(reference.pixels, reference.width * reference.height),
					Arrays.copyOf(image.pixels, image.width * image.height));
		}
	}

	@Test public void eofDoesNotCompleteFarTruncatedOrHeaderOnlyInput() {
		for (String mode : new String[] {"PD 50", "Robot 36 Color", "Martin 2", "Scottie 1", "Scottie 2"}) {
			Signal signal = signal(mode, 8000);
			PixelBuffer image = new PixelBuffer(800, 616);
			// Silence may fill a short missing tail, but must not finish arbitrarily
			// truncated files. Two seconds exceeds the fixed EOF padding budget.
			Decoder decoder = decode(signal, signal.size - 2 * signal.rate, 1, image);
			decoder.finish(0);
			assertTrue(mode, image.line >= 0 && image.line < image.height);
			int lines = image.line;
			assertFalse(decoder.finish(0));
			assertEquals(lines, image.line);
		}
		Signal header = new Signal(8000);
		header.header(93);
		PixelBuffer image = new PixelBuffer(800, 616);
		Decoder decoder = decode(header, header.size, 1, image);
		assertFalse(decoder.finish(0));
		assertTrue(image.line < image.height);
	}
}
