package xdsopl.robot36;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.net.Uri;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FilePlaybackTest {
	private static Object field(Object owner, String name) {
		try { Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
		catch (ReflectiveOperationException e) { throw new AssertionError(e); }
	}
	private static void set(Object owner, String name, Object value) {
		try { Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); f.set(owner, value); }
		catch (ReflectiveOperationException e) { throw new AssertionError(e); }
	}
	private static void await(ActivityScenario<MainActivity> scenario, java.util.function.Predicate<MainActivity> condition) throws Exception {
		long deadline = System.nanoTime() + 5_000_000_000L;
		AtomicBoolean passed = new AtomicBoolean();
		do {
			scenario.onActivity(activity -> passed.set(condition.test(activity)));
			if (passed.get()) return;
			Thread.sleep(20);
		} while (System.nanoTime() < deadline);
		fail("Activity did not reach the expected file-playback state");
	}

	@Test public void fileFormatOverridesMicrophoneSettingsAndLeavesMicrophoneStoppedAtEof() throws Exception {
		Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
		File file = new File(instrumentation.getTargetContext().getCacheDir(), "file-input-test.wav");
		int rate = 48000, channels = 2, frames = 12000;
		ByteBuffer wav = ByteBuffer.allocate(44 + frames * channels * 4).order(ByteOrder.LITTLE_ENDIAN);
		// Reproduce an encoder header that counts one more frame than it writes.
		wav.put(new byte[] {'R','I','F','F'}).putInt(wav.capacity() - 8 + channels * 4).put(new byte[] {'W','A','V','E','f','m','t',' '});
		wav.putInt(16).putShort((short) 3).putShort((short) channels).putInt(rate).putInt(rate * channels * 4);
		wav.putShort((short) (channels * 4)).putShort((short) 32).put(new byte[] {'d','a','t','a'}).putInt((frames + 1) * channels * 4);
		// Silence exercises the playback path without emitting test tones near the user.
		try (FileOutputStream out = new FileOutputStream(file)) { out.write(wav.array()); }
		IntentFilter pickerFilter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
		pickerFilter.addCategory(Intent.CATEGORY_OPENABLE);
		pickerFilter.addDataType("*/*");
		Instrumentation.ActivityMonitor picker = instrumentation.addMonitor(pickerFilter,
				new Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent().setData(Uri.fromFile(file))), true);
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			int[] original = new int[3];
			scenario.onActivity(activity -> {
				original[0] = (int) field(activity, "recordRate");
				original[1] = (int) field(activity, "recordChannel");
				original[2] = (int) field(activity, "audioFormat");
				set(activity, "recordRate", 8000);
				set(activity, "recordChannel", 4);
				set(activity, "audioFormat", AudioFormat.ENCODING_PCM_16BIT);
				activity.findViewById(R.id.btn_source_file).performClick();
			});
			try {
				instrumentation.waitForIdleSync();
				assertEquals("Switching modes must not open the picker", 0, picker.getHits());
				scenario.onActivity(activity -> {
					assertTrue((boolean) field(activity, "fileMode"));
					assertNull(field(activity, "selectedFile"));
					activity.findViewById(R.id.btn_file_action).performClick();
				});
				await(scenario, activity -> field(activity, "selectedFile") != null);
				scenario.onActivity(activity -> activity.findViewById(R.id.btn_file_play_stop).performClick());
				await(scenario, activity -> field(activity, "recordBuffer") != null && (int) field(activity, "decoderChannel") == 3);
				scenario.onActivity(activity -> {
					AudioSource source = (AudioSource) field(field(activity, "audioSessions"), "currentSource");
					assertEquals(48000, source.getFormat().getSampleRate());
					assertEquals(2, source.getFormat().getChannels());
					AudioManager audio = activity.getSystemService(AudioManager.class);
					assertTrue("Microphone still recording in file mode", audio.getActiveRecordingConfigurations().isEmpty());
				});
				await(scenario, activity -> !(boolean) field(activity, "filePlaying"));
				scenario.onActivity(activity -> {
					assertEquals(activity.getString(R.string.audio_file_completed), activity.getTitle().toString());
					assertTrue(activity.getSystemService(AudioManager.class).getActiveRecordingConfigurations().isEmpty());
				});
				// Leaving and returning must keep File mode stopped, with its selection intact.
				scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
				scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
				scenario.onActivity(activity -> {
					assertTrue((boolean) field(activity, "fileMode"));
					assertFalse((boolean) field(activity, "filePlaying"));
					assertNotNull(field(activity, "selectedFile"));
				});
				// Rebuilding the layout must reconnect the file controls without starting a mic.
				scenario.onActivity(activity -> {
					android.content.res.Configuration config = new android.content.res.Configuration(activity.getResources().getConfiguration());
					config.orientation = android.content.res.Configuration.ORIENTATION_LANDSCAPE;
					activity.onConfigurationChanged(config);
					assertTrue(activity.findViewById(R.id.btn_file_play_stop).hasOnClickListeners());
					activity.findViewById(R.id.btn_file_play_stop).performClick();
					assertTrue((boolean) field(activity, "filePlaying"));
					activity.findViewById(R.id.btn_file_play_stop).performClick();
					assertFalse((boolean) field(activity, "filePlaying"));
					config.orientation = android.content.res.Configuration.ORIENTATION_PORTRAIT;
					activity.onConfigurationChanged(config);
					// Restore mic preferences before returning to its live source.
					set(activity, "recordRate", original[0]); set(activity, "recordChannel", original[1]);
					set(activity, "audioFormat", original[2]);
					activity.findViewById(R.id.btn_source_mic).performClick();
				});
				await(scenario, activity -> !activity.getSystemService(AudioManager.class).getActiveRecordingConfigurations().isEmpty());
			} finally {
				scenario.onActivity(activity -> {
					set(activity, "recordRate", original[0]); set(activity, "recordChannel", original[1]);
					set(activity, "audioFormat", original[2]);
				});
			}
		} finally { instrumentation.removeMonitor(picker); file.delete(); }
	}

	@Test public void playerDrainsShortTailAndCancellationDoesNotBlock() throws Exception {
		AudioTrackPlayer player = new AudioTrackPlayer();
		try {
			long start = System.nanoTime();
			player.write(new float[2400], 2400, 48000, 1);
			player.finish();
			assertTrue("Tail was discarded", System.nanoTime() - start >= 40_000_000L);
			player.cancel();
			assertThrows(java.io.InterruptedIOException.class, () -> player.write(new float[2], 2, 48000, 1));
		} finally { player.close(); }
	}
}
