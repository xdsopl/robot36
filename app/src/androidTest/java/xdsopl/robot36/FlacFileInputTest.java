package xdsopl.robot36;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.widget.CompoundButton;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;
import static xdsopl.robot36.FilePlaybackTest.*;

@RunWith(AndroidJUnit4.class)
public class FlacFileInputTest {
	@Test public void selectedFlacHasNoLossyNoticeAndCanReplayInBothModes() throws Exception {
		File file = FlacFileReaderTest.fixture("silence.flac");
		Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
		IntentFilter filter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
		filter.addCategory(Intent.CATEGORY_OPENABLE);
		filter.addDataType("*/*");
		Instrumentation.ActivityMonitor picker = instrumentation.addMonitor(filter,
				new Instrumentation.ActivityResult(Activity.RESULT_OK, new Intent().setData(Uri.fromFile(file))), true);
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			boolean[] originalTurbo = new boolean[1];
			scenario.onActivity(activity -> {
				originalTurbo[0] = (boolean) field(activity, "turboDecode");
				activity.findViewById(R.id.btn_source_file).performClick();
				activity.findViewById(R.id.btn_file_action).performClick();
			});
			try {
				await(scenario, activity -> activity.findViewById(R.id.btn_file_play_stop).isEnabled());
				scenario.onActivity(activity -> {
					assertEquals(AudioFileReaders.Type.FLAC, field(activity, "selectedFileType"));
					assertNull(field(activity, "fileNotice"));
					assertFalse((boolean) field(activity, "fileNoticePending"));
				});
				scenario.recreate();
				for (boolean turbo : new boolean[] {false, true}) {
					scenario.onActivity(activity -> {
						assertEquals(AudioFileReaders.Type.FLAC, field(activity, "selectedFileType"));
						((CompoundButton) activity.findViewById(R.id.cb_turbo_decode)).setChecked(turbo);
						activity.findViewById(R.id.btn_file_play_stop).performClick();
						assertTrue((boolean) field(activity, "filePlaying"));
						assertNull(field(activity, "fileNotice"));
						Object monitor = field(field(activity, "audioSessions"), "currentMonitor");
						if (turbo) assertNull(monitor); else assertNotNull(monitor);
					});
					await(scenario, activity -> !(boolean) field(activity, "filePlaying"));
					scenario.onActivity(activity -> {
						assertEquals(activity.getString(R.string.audio_file_completed), activity.getTitle().toString());
						AudioSource source = (AudioSource) field(field(activity, "audioSessions"), "currentSource");
						assertEquals(48000, source.getFormat().getSampleRate());
						assertEquals(2, source.getFormat().getChannels());
					});
				}
			} finally {
				scenario.onActivity(activity -> ((CompoundButton) activity.findViewById(R.id.cb_turbo_decode)).setChecked(originalTurbo[0]));
			}
		} finally { instrumentation.removeMonitor(picker); file.delete(); }
	}
}
