package xdsopl.robot36;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Grant audio/notification permissions first. The optional real-grant test needs consent on the phone. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29)
public class PlaybackCaptureTest {
	private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();

	@Before public void permissions() {
		assertEquals("Grant microphone permission before running device tests", PackageManager.PERMISSION_GRANTED,
				instrumentation.getTargetContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO));
		if (Build.VERSION.SDK_INT >= 33) {
			assertEquals("Grant notification permission before running device tests", PackageManager.PERMISSION_GRANTED,
					instrumentation.getTargetContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS));
		}
	}

	private static Object field(Object owner, String name) {
		try { Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner); }
		catch (ReflectiveOperationException e) { throw new AssertionError(e); }
	}

	private static AudioSource source(MainActivity activity) {
		return (AudioSource) field(field(activity, "audioSessions"), "currentSource");
	}

	private static void await(ActivityScenario<MainActivity> scenario, Predicate<MainActivity> condition, int seconds) throws Exception {
		long deadline = System.nanoTime() + seconds * 1_000_000_000L;
		AtomicBoolean passed = new AtomicBoolean();
		do {
			scenario.onActivity(activity -> passed.set(condition.test(activity)));
			if (passed.get()) return;
			Thread.sleep(20);
		} while (System.nanoTime() < deadline);
		fail("Activity did not reach the expected playback-capture state");
	}

	private void rejectedGrant(int resultCode, Intent data, int expectedStatus) throws Exception {
		MediaProjectionManager manager = instrumentation.getTargetContext().getSystemService(MediaProjectionManager.class);
		Instrumentation.ActivityMonitor consent = instrumentation.addMonitor(
				manager.createScreenCaptureIntent().getComponent().getClassName(),
				new Instrumentation.ActivityResult(resultCode, data), true);
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			scenario.onActivity(activity -> activity.findViewById(R.id.btn_source_capture).performClick());
			await(scenario, activity -> activity.getString(expectedStatus).contentEquals(activity.getTitle()), 5);
			assertEquals(1, consent.getHits());
			scenario.onActivity(activity -> {
				assertNull(source(activity));
				assertNull(field(activity, "captureConnection"));
				assertNull(field(activity, "captureService"));
				assertFalse((boolean) field(activity, "capturePermissionPending"));
				assertTrue(activity.findViewById(R.id.btn_source_capture).isEnabled());
			});
			await(scenario, activity -> activity.getSystemService(AudioManager.class).getActiveRecordingConfigurations().isEmpty(), 5);
			// Returning from the consent UI, or recreating the Activity, must not open the mic.
			scenario.recreate();
			scenario.onActivity(activity -> {
				assertTrue((boolean) field(activity, "captureMode"));
				assertNull(source(activity));
				activity.findViewById(R.id.btn_source_mic).performClick();
			});
			await(scenario, activity -> source(activity) instanceof MicrophoneAudioSource
					&& source(activity).getState() == AudioSource.AudioSourceState.RUNNING, 5);
		} finally { instrumentation.removeMonitor(consent); }
	}

	@Test public void cancelledConsentLeavesCaptureStoppedAndMicrophoneCanRestart() throws Exception {
		rejectedGrant(Activity.RESULT_CANCELED, null, R.string.capture_permission_denied);
	}

	@Test public void invalidGrantReportsErrorAndReleasesTheServiceWithoutCrashing() throws Exception {
		rejectedGrant(Activity.RESULT_OK, new Intent(), R.string.capture_error);
	}

	@Test public void realGrantKeepsDecodingInBackgroundAndStopsWhenRevoked() throws Exception {
		// Opt in with: -e captureConsent manual. Consent is never mocked or persisted.
		assumeTrue("manual".equals(InstrumentationRegistry.getArguments().getString("captureConsent")));
		try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
			scenario.onActivity(activity -> activity.findViewById(R.id.btn_source_capture).performClick());
			// Grant permission on the phone; ActivityScenario cannot act on a stopped Activity.
			long deadline = System.nanoTime() + 90_000_000_000L;
			while (scenario.getState() != Lifecycle.State.RESUMED && System.nanoTime() < deadline) Thread.sleep(100);
			await(scenario, activity -> source(activity) instanceof AudioPlaybackCaptureSource
					&& source(activity).getState() == AudioSource.AudioSourceState.RUNNING, 90);
			AudioSource[] captured = new AudioSource[1];
			float[][] previous = new float[1][];
			scenario.onActivity(activity -> {
				captured[0] = source(activity);
				assertEquals(48000, captured[0].getFormat().getSampleRate());
				assertEquals(2, captured[0].getFormat().getChannels());
				assertNull(field(field(activity, "audioSessions"), "currentMonitor"));
			});
			await(scenario, activity -> (int) field(activity, "decoderChannel") == 3, 5);
			scenario.moveToState(Lifecycle.State.CREATED);
			scenario.onActivity(activity -> previous[0] = (float[]) field(activity, "recordBuffer"));
			await(scenario, activity -> field(activity, "recordBuffer") != previous[0], 5);
			scenario.moveToState(Lifecycle.State.RESUMED);
			scenario.onActivity(activity -> {
				assertSame(captured[0], source(activity));
				assertNull(captured[0].getFailure());
				AudioCaptureService service = (AudioCaptureService) field(activity, "captureService");
				((MediaProjection) field(service, "projection")).stop();
			});
			await(scenario, activity -> source(activity) == null && field(activity, "captureService") == null, 5);
			await(scenario, activity -> captured[0].getState() == AudioSource.AudioSourceState.RELEASED, 5);
			scenario.onActivity(activity -> {
				assertTrue((boolean) field(activity, "captureMode"));
				assertEquals(activity.getString(R.string.capture_stopped), activity.getTitle().toString());
				assertNull(field(activity, "captureConnection"));
				assertTrue(activity.findViewById(R.id.btn_source_capture).isEnabled());
			});
		}
	}
}
