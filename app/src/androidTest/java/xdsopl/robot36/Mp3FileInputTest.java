package xdsopl.robot36;

import android.net.Uri;
import android.view.View;
import android.widget.CompoundButton;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.material.snackbar.Snackbar;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static org.junit.Assert.*;
import static xdsopl.robot36.FilePlaybackTest.*;

@RunWith(AndroidJUnit4.class)
public class Mp3FileInputTest {
	private static void select(MainActivity activity, File file) {
		try {
			Method select = MainActivity.class.getDeclaredMethod("selectFile", Uri.class);
			select.setAccessible(true);
			select.invoke(activity, Uri.fromFile(file));
		} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
	}
	private static boolean noticeShown(MainActivity activity) {
		Snackbar notice = (Snackbar) field(activity, "fileNotice");
		return notice != null && notice.isShown();
	}
	private static File wavWithMp3Suffix() throws Exception {
		File file = File.createTempFile("pcm-file-", ".mp3", Mp3FileReaderTest.context().getCacheDir());
		ByteBuffer data = ByteBuffer.allocate(44 + 160).order(ByteOrder.LITTLE_ENDIAN);
		data.put(new byte[] {'R','I','F','F'}).putInt(data.capacity() - 8).put(new byte[] {'W','A','V','E','f','m','t',' '});
		data.putInt(16).putShort((short) 1).putShort((short) 1).putInt(8000).putInt(16000);
		data.putShort((short) 2).putShort((short) 16).put(new byte[] {'d','a','t','a'}).putInt(160);
		try (FileOutputStream out = new FileOutputStream(file)) { out.write(data.array()); }
		return file;
	}

	@Test public void noticeIsTransientNonBlockingAndShownForEverySelection() throws Exception {
		File file = Mp3FileReaderTest.fixture("silence.mp3");
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			boolean[] originalTurbo = new boolean[1];
			scenario.onActivity(activity -> {
				originalTurbo[0] = (boolean) field(activity, "turboDecode");
				((CompoundButton) activity.findViewById(R.id.cb_turbo_decode)).setChecked(true);
				select(activity, file);
			});
			try {
				await(scenario, Mp3FileInputTest::noticeShown);
				Snackbar[] firstNotice = new Snackbar[1];
				scenario.onActivity(activity -> {
					firstNotice[0] = (Snackbar) field(activity, "fileNotice");
					assertTrue(activity.findViewById(R.id.btn_file_play_stop).isEnabled());
					assertEquals(View.GONE, firstNotice[0].getView().findViewById(com.google.android.material.R.id.snackbar_action).getVisibility());
					activity.findViewById(R.id.btn_file_play_stop).performClick();
					assertTrue((boolean) field(activity, "filePlaying"));
				});
				await(scenario, activity -> !(boolean) field(activity, "filePlaying"));
				scenario.onActivity(activity -> {
					assertEquals(activity.getString(R.string.audio_file_completed), activity.getTitle().toString());
					assertTrue(noticeShown(activity));
				});
				await(scenario, activity -> !noticeShown(activity));
				scenario.onActivity(activity -> select(activity, file));
				await(scenario, Mp3FileInputTest::noticeShown);
				scenario.onActivity(activity -> {
					assertNotSame(firstNotice[0], field(activity, "fileNotice"));
					activity.findViewById(R.id.btn_source_mic).performClick();
					assertNull(field(activity, "fileNotice"));
				});
			} finally {
				scenario.onActivity(activity -> ((CompoundButton) activity.findViewById(R.id.cb_turbo_decode)).setChecked(originalTurbo[0]));
			}
		} finally { file.delete(); }
	}

	@Test public void wavWithMisleadingSuffixDoesNotShowLossyNotice() throws Exception {
		File file = wavWithMp3Suffix();
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			scenario.onActivity(activity -> select(activity, file));
			await(scenario, activity -> activity.findViewById(R.id.btn_file_play_stop).isEnabled());
			scenario.onActivity(activity -> {
				assertEquals(AudioFileReaders.Type.WAV, field(activity, "selectedFileType"));
				assertNull(field(activity, "fileNotice"));
			});
		} finally { file.delete(); }
	}

	@Test public void newerWavSelectionDiscardsPendingMp3Notice() throws Exception {
		File mp3 = Mp3FileReaderTest.fixture("silence.mp3"), wav = wavWithMp3Suffix();
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			scenario.onActivity(activity -> { select(activity, mp3); select(activity, wav); });
			await(scenario, activity -> activity.findViewById(R.id.btn_file_play_stop).isEnabled());
			scenario.onActivity(activity -> {
				assertEquals(Uri.fromFile(wav), field(activity, "selectedFile"));
				assertEquals(AudioFileReaders.Type.WAV, field(activity, "selectedFileType"));
				assertNull(field(activity, "fileNotice"));
			});
		} finally { mp3.delete(); wav.delete(); }
	}
}
