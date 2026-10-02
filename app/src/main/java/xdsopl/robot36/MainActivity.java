/*
Robot36

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.ShareActionProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.MenuItemCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class MainActivity extends AppCompatActivity {


	private Bitmap scopeBitmap;
	private PixelBuffer scopeBuffer;
	private ImageView scopeView;
	private Bitmap waterfallPlotBitmap;
	private PixelBuffer waterfallPlotBuffer;
	private ImageView waterfallPlotView;
	private Bitmap peakMeterBitmap;
	private PixelBuffer peakMeterBuffer;
	private ImageView peakMeterView;
	private PixelBuffer imageBuffer;
	private ShortTimeFourierTransform stft;
	private float[] recordBuffer;
	private Decoder decoder;
	private Menu menu;
	private String currentMode;
	private String language;
	private Complex input;
	private int recordRate;
	private int recordChannel;
	private int audioSource;
	private int audioFormat;
	private int decoderChannel;
	private long decoderSession = -1;
	private boolean resumed;
	private final Handler audioUi = new Handler(Looper.getMainLooper());
	private AudioSessionManager audioSessions;
	private boolean fileMode, filePlaying;
	private boolean captureMode, capturePermissionPending;
	private AudioCaptureService captureService;
	private ServiceConnection captureConnection;
	private boolean turboDecode;
	private long lastPreviewNanos;
	private Uri selectedFile;
	private String selectedFileName;
	private final ActivityResultLauncher<String[]> filePicker = registerForActivityResult(
			new ActivityResultContracts.OpenDocument(), this::selectFile);
	private final ActivityResultLauncher<Intent> capturePermission = registerForActivityResult(
			new ActivityResultContracts.StartActivityForResult(), result -> {
		if (!capturePermissionPending || !captureMode) return;
		capturePermissionPending = false;
		if (result.getResultCode() != RESULT_OK || result.getData() == null) {
			setStatus(R.string.capture_permission_denied);
			updateInputControls();
			return;
		}
		bindCapture(result.getResultCode(), result.getData());
	});
	private final ActivityResultLauncher<String[]> captureAudioPermission = registerForActivityResult(
			new ActivityResultContracts.RequestMultiplePermissions(), grants -> {
		if (!capturePermissionPending || !captureMode) return;
		// Notifications offer a Stop action, but denying them must not block capture.
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
			launchCapturePermission();
		else {
			capturePermissionPending = false;
			setStatus(R.string.audio_permission_denied);
			updateInputControls();
		}
	});
	private int fgColor;
	private int thinColor;
	private int tintColor;
	private boolean autoSave;
	private boolean showSpectrogram;
	private final int binWidthHz = 10;
	private final int[] freqMarkers = { 1100, 1300, 1500, 2300 };

	private final AudioSessionManager.SessionCallback audioListener = new AudioSessionManager.SessionCallback() {
		@Override public void onPcmDataAvailable(float[] buffer, int frames, int rate, int channels, long session) {
			// The UI consumer must own its copy even if cancellation interrupts our wait.
			float[] pcm = Arrays.copyOf(buffer, frames * channels);
			FutureTask<Void> consume = new FutureTask<>(() -> {
				if ((resumed || captureMode) && audioSessions.isCurrent(session))
					consumeAudio(pcm, rate, channels, session);
				return null;
			});
			audioUi.post(consume);
			try {
				// Keep at most one PCM block queued per source; do not drop live samples.
				consume.get();
			} catch (InterruptedException e) {
				consume.cancel(false);
				Thread.currentThread().interrupt();
			} catch (ExecutionException e) {
				Log.e("Robot36", "Audio decoding failed", e.getCause());
				audioUi.post(() -> {
					if (!audioSessions.isCurrent(session)) return;
					stopListening();
					setStatus(fileMode ? R.string.audio_file_error : captureMode ? R.string.capture_error : R.string.audio_recording_error);
				});
			}
		}

		@Override public void onSourceStateChanged(AudioSource.AudioSourceState state, String message, long session) {
			audioUi.post(() -> {
				if ((!resumed && !captureMode) || !audioSessions.isCurrent(session)) return;
				if (state == AudioSource.AudioSourceState.RUNNING) {
					if (fileMode || captureMode) {
						setTitle(currentMode == null ? getString(R.string.auto_mode) : currentMode);
						ActionBar bar = getSupportActionBar();
						if (bar != null) bar.setSubtitle(captureMode ? R.string.capture_listening
								: turboDecode ? R.string.audio_file_decoding : R.string.audio_file_playing);
					} else setStatus(R.string.listening);
				}
				else if (state == AudioSource.AudioSourceState.COMPLETED && fileMode) {
					if (decoder != null && decoderSession == session) {
						decoder.finish(decoderChannel);
						// Always show the final result, even if Turbo skipped its preview.
						processScope();
						processImage();
					}
					filePlaying = false;
					updateInputControls();
					setStatus(R.string.audio_file_completed);
				}
				else if (state == AudioSource.AudioSourceState.ERROR) {
					Log.e("Robot36", message);
					if (captureMode) stopListening();
					filePlaying = false;
					updateInputControls();
					setStatus(fileMode ? R.string.audio_file_error : captureMode ? R.string.capture_error : R.string.audio_recording_error);
					if (fileMode) new AlertDialog.Builder(MainActivity.this)
							.setTitle(R.string.audio_file_error).setMessage(message)
							.setPositiveButton(android.R.string.ok, null).show();
				}
			});
		}
	};

	private void consumeAudio(float[] pcm, int rate, int channels, long session) {
		if (decoderSession != session) {
			decoder = new Decoder(scopeBuffer, imageBuffer, getString(R.string.raw_mode), rate);
			decoder.setMode(currentMode);
			stft = new ShortTimeFourierTransform(rate / binWidthHz, 3);
			// Each source supplies its format; channel-selection preferences apply only to the mic.
			decoderChannel = channels == 1 ? 0 : (fileMode || captureMode ? 3 : (recordChannel == 0 ? 1 : recordChannel));
			decoderSession = session;
			lastPreviewNanos = 0;
		}
		boolean turbo = fileMode && turboDecode;
		long now = System.nanoTime();
		boolean preview = resumed && (!turbo || lastPreviewNanos == 0 || now - lastPreviewNanos >= 33_000_000L);
		if (preview) lastPreviewNanos = now;
		recordBuffer = pcm;
		if (preview) processPeakMeter();
		if (resumed && showSpectrogram) processSpectrogram(preview);
		boolean newLines = decoder.process(recordBuffer, decoderChannel);
		if (!showSpectrogram && preview) processFreqPlot();
		// Image completion must be checked for every block, independently of drawing.
		if (newLines) processImage();
		if (preview && (newLines || turbo)) {
			processScope();
			if (!decoder.currentMode.getName().contentEquals(getTitle())) setTitle(decoder.currentMode.getName());
		}
	}

	private void startListening() {
		if (!resumed || fileMode || captureMode) return;
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			setStatus(R.string.audio_permission_denied);
			return;
		}
		audioSessions.startSession(new MicrophoneAudioSource(recordRate, recordChannel == 0 ? 1 : 2, audioSource, audioFormat));
	}

	private void stopListening() {
		audioSessions.stopAndReleaseCurrentSession();
		stopCapture();
		filePlaying = false;
		updateInputControls();
	}

	private void requestCapture() {
		if (Build.VERSION.SDK_INT < 29 || !resumed || !captureMode) return;
		capturePermissionPending = true;
		updateInputControls();
		setStatus(R.string.capture_authorizing);
		ArrayList<String> permissions = new ArrayList<>();
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
			permissions.add(Manifest.permission.RECORD_AUDIO);
		if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
			permissions.add(Manifest.permission.POST_NOTIFICATIONS);
		if (permissions.isEmpty()) launchCapturePermission();
		else captureAudioPermission.launch(permissions.toArray(new String[0]));
	}

	private void launchCapturePermission() {
		try {
			capturePermission.launch(getSystemService(MediaProjectionManager.class).createScreenCaptureIntent());
		} catch (RuntimeException e) { captureFailed(e); }
	}

	private void bindCapture(int resultCode, Intent data) {
		if (Build.VERSION.SDK_INT < 29) return;
		setStatus(R.string.capture_starting);
		captureConnection = new ServiceConnection() {
			private Intent grant = data;
			@Override public void onServiceConnected(ComponentName name, IBinder binder) {
				if (captureConnection != this || !captureMode) return;
				captureService = ((AudioCaptureService.LocalBinder) binder).getService();
				Intent freshGrant = grant;
				grant = null;
				try {
					MediaProjection projection = captureService.startCapture(resultCode, freshGrant, () -> {
						if (captureConnection != this) return;
						stopListening();
						setStatus(R.string.capture_stopped);
					});
					audioSessions.startSession(new AudioPlaybackCaptureSource(projection));
					updateInputControls();
				} catch (RuntimeException e) { captureFailed(e); }
			}
			@Override public void onServiceDisconnected(ComponentName name) {
				if (captureConnection == this) captureFailed(new IOException("Capture service disconnected"));
			}
			@Override public void onBindingDied(ComponentName name) { onServiceDisconnected(name); }
			@Override public void onNullBinding(ComponentName name) { onServiceDisconnected(name); }
		};
		try {
			if (!bindService(new Intent(this, AudioCaptureService.class), captureConnection, Context.BIND_AUTO_CREATE))
				throw new IOException("Cannot bind capture service");
		} catch (IOException | RuntimeException e) { captureFailed(e); }
		updateInputControls();
	}

	private void captureFailed(Exception error) {
		Log.e("Robot36", "Playback capture failed", error);
		stopListening();
		setStatus(R.string.capture_error);
		if (resumed) new AlertDialog.Builder(this).setTitle(R.string.capture_error)
				.setMessage(error.toString()).setPositiveButton(android.R.string.ok, null).show();
	}

	private void stopCapture() {
		capturePermissionPending = false;
		AudioCaptureService previous = captureService;
		ServiceConnection connection = captureConnection;
		captureService = null;
		captureConnection = null; // Ignore the service's callback during owner-initiated cleanup.
		try {
			if (previous != null && Build.VERSION.SDK_INT >= 29) previous.stopCapture();
		} finally {
			// Even an unsuccessful bind can register a connection with the framework.
			if (connection != null) {
				try { unbindService(connection); }
				catch (IllegalArgumentException e) { Log.w("Robot36", "Capture service was not bound", e); }
			}
		}
	}

	private void startFile() {
		if (!resumed || !fileMode || selectedFile == null) return;
		Uri uri = selectedFile;
		filePlaying = true;
		updateInputControls();
		setStatus(R.string.audio_file_opening);
		audioSessions.startSession(new FileAudioSource(() -> getContentResolver().openInputStream(uri), turboDecode),
				turboDecode ? null : new AudioTrackPlayer());
	}

	private void selectFile(Uri uri) {
		if (uri == null) return;
		stopListening();
		fileMode = true;
		captureMode = false;
		selectedFile = uri;
		selectedFileName = uri.getLastPathSegment();
		try (Cursor cursor = getContentResolver().query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
			if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) selectedFileName = cursor.getString(0);
		} catch (RuntimeException e) { Log.w("Robot36", "Cannot read audio filename", e); }
		if (selectedFileName == null) selectedFileName = getString(R.string.audio_file_source);
		updateInputControls();
		setStatus(R.string.audio_file_ready);
	}

	private void bindInputControls() {
		findViewById(R.id.btn_source_mic).setOnClickListener(v -> {
			stopListening();
			fileMode = false;
			captureMode = false;
			updateInputControls();
			requestMicrophone();
		});
		findViewById(R.id.btn_source_file).setOnClickListener(v -> {
			if (fileMode) return;
			stopListening();
			fileMode = true;
			captureMode = false;
			updateInputControls();
			setStatus(selectedFile == null ? R.string.select_audio_file : R.string.audio_file_ready);
		});
		findViewById(R.id.btn_source_capture).setOnClickListener(v -> {
			if (captureMode && (capturePermissionPending || captureConnection != null)) {
				stopListening();
				setStatus(R.string.capture_stopped);
				return;
			}
			stopListening();
			fileMode = false;
			captureMode = true;
			requestCapture();
		});
		findViewById(R.id.btn_file_action).setOnClickListener(v -> {
			stopListening();
			if (selectedFile == null) filePicker.launch(new String[] {"audio/*", "application/octet-stream"});
			else {
				selectedFile = null;
				selectedFileName = null;
				updateInputControls();
				setStatus(R.string.select_audio_file);
			}
		});
		findViewById(R.id.btn_file_play_stop).setOnClickListener(v -> {
			if (filePlaying) { stopListening(); setStatus(R.string.audio_file_stopped); }
			else startFile();
		});
		CompoundButton turbo = findViewById(R.id.cb_turbo_decode);
		turbo.setChecked(turboDecode);
		turbo.setOnCheckedChangeListener((button, checked) -> turboDecode = checked);
		updateInputControls();
	}

	private void updateInputControls() {
		ImageButton mic = findViewById(R.id.btn_source_mic), file = findViewById(R.id.btn_source_file);
		ImageButton capture = findViewById(R.id.btn_source_capture);
		boolean microphone = !fileMode && !captureMode;
		mic.setSelected(microphone);
		file.setSelected(fileMode);
		capture.setSelected(captureMode);
		mic.setBackgroundResource(microphone ? R.drawable.bg_segmented_button : android.R.color.transparent);
		file.setBackgroundResource(fileMode ? R.drawable.bg_segmented_button : android.R.color.transparent);
		capture.setBackgroundResource(captureMode ? R.drawable.bg_segmented_button : android.R.color.transparent);
		mic.setImageTintList(ColorStateList.valueOf(microphone ? getColor(R.color.light_blue) : tintColor));
		file.setImageTintList(ColorStateList.valueOf(fileMode ? getColor(R.color.light_blue) : tintColor));
		capture.setImageTintList(ColorStateList.valueOf(captureMode ? getColor(R.color.light_blue) : tintColor));
		capture.setEnabled(Build.VERSION.SDK_INT >= 29 && !capturePermissionPending);
		capture.setAlpha(Build.VERSION.SDK_INT >= 29 ? 1f : 0.4f);
		capture.setContentDescription(getString(captureConnection != null ? R.string.capture_stop : R.string.playback_capture));
		file.setEnabled(true);
		file.setAlpha(1f);
		findViewById(R.id.file_control_container).setVisibility(fileMode ? View.VISIBLE : View.GONE);
		CompoundButton turbo = findViewById(R.id.cb_turbo_decode);
		turbo.setChecked(turboDecode);
		turbo.setEnabled(fileMode && !filePlaying);
		ImageButton action = findViewById(R.id.btn_file_action), play = findViewById(R.id.btn_file_play_stop);
		action.setEnabled(true);
		action.setImageResource(selectedFile == null ? R.drawable.ic_add_24 : R.drawable.ic_close_24);
		action.setContentDescription(getString(selectedFile == null ? R.string.select_audio_file : R.string.unload_audio_file));
		play.setEnabled(selectedFile != null);
		play.setAlpha(selectedFile == null ? 0.4f : 1f);
		play.setImageResource(filePlaying ? R.drawable.ic_stop_24 : R.drawable.ic_play_arrow_24);
		play.setContentDescription(getString(filePlaying ? R.string.stop_audio_file : R.string.play_audio_file));
		TextView filename = findViewById(R.id.tv_current_filename);
		filename.setText(selectedFileName);
		filename.setVisibility(fileMode && selectedFile != null ? View.VISIBLE : View.GONE);
		if (menu != null) {
			menu.findItem(R.id.action_audio_sample_rate).setEnabled(microphone);
			menu.findItem(R.id.action_audio_channel).setEnabled(microphone);
			menu.findItem(R.id.action_audio_source).setEnabled(microphone);
			menu.findItem(R.id.action_audio_format).setEnabled(microphone);
		}
	}

	private void setStatus(int id) {
		if (id == R.string.audio_file_ready || id == R.string.audio_file_stopped) {
			SpannableString title = new SpannableString(getText(id));
			float scale = id == R.string.audio_file_stopped ? 0.65f : 0.7f;
			title.setSpan(new RelativeSizeSpan(scale), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
			setTitle(title);
		} else setTitle(id);
		ActionBar bar = getSupportActionBar();
		if (bar != null) bar.setSubtitle(null);
	}

	private void setMode(String name) {
		int icon;
		if (name.equals(getString(R.string.auto_mode)))
			icon = R.drawable.baseline_auto_mode_24;
		else
			icon = R.drawable.baseline_lock_24;
		menu.findItem(R.id.action_toggle_mode).setIcon(icon);
		currentMode = name;
		if (decoder != null)
			decoder.setMode(currentMode);
	}

	private void setMode(int id) {
		setMode(getString(id));
	}

	private void autoMode() {
		setMode(R.string.auto_mode);
	}

	private void toggleMode() {
		if (decoder == null || currentMode != null && !currentMode.equals(getString(R.string.auto_mode)))
			autoMode();
		else
			setMode(decoder.currentMode.getName());
	}

	private void processPeakMeter() {
		float max = 0;
		for (float v : recordBuffer)
			max = Math.max(max, Math.abs(v));
		int pixels = peakMeterBuffer.height;
		int peak = pixels;
		if (max > 0)
			peak = (int) Math.round(Math.min(Math.max(-Math.PI * Math.log(max), 0), pixels));
		Arrays.fill(peakMeterBuffer.pixels, 0, peak, thinColor);
		Arrays.fill(peakMeterBuffer.pixels, peak, pixels, tintColor);
		peakMeterBitmap.setPixels(peakMeterBuffer.pixels, 0, peakMeterBuffer.width, 0, 0, peakMeterBuffer.width, peakMeterBuffer.height);
		peakMeterView.invalidate();
	}

	private double clamp(double x) {
		return Math.min(Math.max(x, 0), 1);
	}

	private int argb(double a, double r, double g, double b) {
		a = clamp(a);
		r = clamp(r);
		g = clamp(g);
		b = clamp(b);
		r *= a;
		g *= a;
		b *= a;
		r = Math.sqrt(r);
		g = Math.sqrt(g);
		b = Math.sqrt(b);
		int A = (int) Math.rint(255 * a);
		int R = (int) Math.rint(255 * r);
		int G = (int) Math.rint(255 * g);
		int B = (int) Math.rint(255 * b);
		return (A << 24) | (R << 16) | (G << 8) | B;
	}

	private int rainbow(double v) {
		v = clamp(v);
		double t = 4 * v - 2;
		return argb(4 * v, t, 1 - Math.abs(t), -t);
	}

	private void processSpectrogram(boolean preview) {
		boolean process = false;
		int channels = decoderChannel > 0 ? 2 : 1;
		for (int j = 0; j < recordBuffer.length / channels; ++j) {
			switch (decoderChannel) {
				case 1:
					input.set(recordBuffer[2 * j]);
					break;
				case 2:
					input.set(recordBuffer[2 * j + 1]);
					break;
				case 3:
					input.set(0.5f * (recordBuffer[2 * j] + recordBuffer[2 * j + 1]));
					break;
				case 4:
					input.set(recordBuffer[2 * j], recordBuffer[2 * j + 1]);
					break;
				default:
					input.set(recordBuffer[j]);
			}
			if (stft.push(input) && preview) {
				process = true;
				int stride = waterfallPlotBuffer.width;
				waterfallPlotBuffer.line = (waterfallPlotBuffer.line + waterfallPlotBuffer.height / 2 - 1) % (waterfallPlotBuffer.height / 2);
				int line = stride * waterfallPlotBuffer.line;
				double lowest = Math.log(1e-9);
				double highest = Math.log(1);
				double range = highest - lowest;
				int minFreq = 140;
				int minBin = minFreq / binWidthHz;
				for (int i = 0; i < stride; ++i)
					waterfallPlotBuffer.pixels[line + i] = rainbow((Math.log(stft.power[i + minBin]) - lowest) / range);
				for (int freq : freqMarkers)
					waterfallPlotBuffer.pixels[line + (freq - minFreq) / binWidthHz] = fgColor;
				System.arraycopy(waterfallPlotBuffer.pixels, line, waterfallPlotBuffer.pixels, line + stride * (waterfallPlotBuffer.height / 2), stride);
			}
		}
		if (process) {
			int width = waterfallPlotBitmap.getWidth();
			int height = waterfallPlotBitmap.getHeight();
			int stride = waterfallPlotBuffer.width;
			int offset = stride * waterfallPlotBuffer.line;
			waterfallPlotBitmap.setPixels(waterfallPlotBuffer.pixels, offset, stride, 0, 0, width, height);
			waterfallPlotView.invalidate();
		}
	}

	private void processFreqPlot() {
		int width = waterfallPlotBitmap.getWidth();
		int height = waterfallPlotBitmap.getHeight();
		int stride = waterfallPlotBuffer.width;
		waterfallPlotBuffer.line = (waterfallPlotBuffer.line + waterfallPlotBuffer.height / 2 - 1) % (waterfallPlotBuffer.height / 2);
		int line = stride * waterfallPlotBuffer.line;
		int channels = decoderChannel > 0 ? 2 : 1;
		int samples = recordBuffer.length / channels;
		int spread = 2;
		Arrays.fill(waterfallPlotBuffer.pixels, line, line + stride, 0);
		for (int i = 0; i < samples; ++i) {
			int x = Math.round((recordBuffer[i] + 2.5f) * 0.25f * stride);
			if (x >= spread && x < stride - spread)
				for (int j = -spread; j <= spread; ++j)
					waterfallPlotBuffer.pixels[line + x + j] += 1 + spread * spread - j * j;
		}
		int factor = 960 / samples;
		for (int i = 0; i < stride; ++i)
			waterfallPlotBuffer.pixels[line + i] = 0x00FFFFFF & fgColor | Math.min(factor * waterfallPlotBuffer.pixels[line + i], 255) << 24;
		System.arraycopy(waterfallPlotBuffer.pixels, line, waterfallPlotBuffer.pixels, line + stride * (waterfallPlotBuffer.height / 2), stride);
		int offset = stride * waterfallPlotBuffer.line;
		waterfallPlotBitmap.setPixels(waterfallPlotBuffer.pixels, offset, stride, 0, 0, width, height);
		waterfallPlotView.invalidate();
	}

	private void processScope() {
		int width = scopeBitmap.getWidth();
		int height = scopeBitmap.getHeight();
		int stride = scopeBuffer.width;
		int offset = stride * (scopeBuffer.line + scopeBuffer.height / 2 - height);
		scopeBitmap.setPixels(scopeBuffer.pixels, offset, stride, 0, 0, width, height);
		scopeView.invalidate();
	}

	private void processImage() {
		if (imageBuffer.line < imageBuffer.height)
			return;
		imageBuffer.line = -1;
		if (autoSave)
			storeBitmap(Bitmap.createBitmap(imageBuffer.pixels, imageBuffer.width, imageBuffer.height, Bitmap.Config.ARGB_8888));
	}

	private void setRecordRate(int newSampleRate) {
		if (recordRate == newSampleRate)
			return;
		recordRate = newSampleRate;
		updateRecordRateMenu();
		startListening();
	}

	private void setRecordChannel(int newChannelSelect) {
		if (recordChannel == newChannelSelect)
			return;
		recordChannel = newChannelSelect;
		updateRecordChannelMenu();
		startListening();
	}

	private void setAudioSource(int newAudioSource) {
		if (audioSource == newAudioSource)
			return;
		audioSource = newAudioSource;
		updateAudioSourceMenu();
		startListening();
	}

	private void setAudioFormat(int newAudioFormat) {
		if (audioFormat == newAudioFormat)
			return;
		audioFormat = newAudioFormat;
		updateAudioFormatMenu();
		startListening();
	}

	private void setShowSpectrogram(boolean newShowSpectrogram) {
		if (showSpectrogram == newShowSpectrogram)
			return;
		showSpectrogram = newShowSpectrogram;
		updateWaterfallPlotMenu();
	}

	private void updateWaterfallPlotMenu() {
		if (showSpectrogram)
			menu.findItem(R.id.action_show_spectrogram).setChecked(true);
		else
			menu.findItem(R.id.action_show_frequency_plot).setChecked(true);
	}

	private void setAutoSave(boolean newAutoSave) {
		if (autoSave == newAutoSave)
			return;
		autoSave = newAutoSave;
		updateAutoSaveMenu();
	}

	private void updateAutoSaveMenu() {
		if (autoSave)
			menu.findItem(R.id.action_enable_auto_save).setChecked(true);
		else
			menu.findItem(R.id.action_disable_auto_save).setChecked(true);
	}

	private void updateRecordRateMenu() {
		switch (recordRate) {
			case 8000:
				menu.findItem(R.id.action_set_record_rate_8000).setChecked(true);
				break;
			case 16000:
				menu.findItem(R.id.action_set_record_rate_16000).setChecked(true);
				break;
			case 32000:
				menu.findItem(R.id.action_set_record_rate_32000).setChecked(true);
				break;
			case 44100:
				menu.findItem(R.id.action_set_record_rate_44100).setChecked(true);
				break;
			case 48000:
				menu.findItem(R.id.action_set_record_rate_48000).setChecked(true);
				break;
		}
	}

	private void updateRecordChannelMenu() {
		switch (recordChannel) {
			case 0:
				menu.findItem(R.id.action_set_record_channel_default).setChecked(true);
				break;
			case 1:
				menu.findItem(R.id.action_set_record_channel_first).setChecked(true);
				break;
			case 2:
				menu.findItem(R.id.action_set_record_channel_second).setChecked(true);
				break;
			case 3:
				menu.findItem(R.id.action_set_record_channel_summation).setChecked(true);
				break;
			case 4:
				menu.findItem(R.id.action_set_record_channel_analytic).setChecked(true);
				break;
		}
	}

	private void updateAudioSourceMenu() {
		switch (audioSource) {
			case MediaRecorder.AudioSource.DEFAULT:
				menu.findItem(R.id.action_set_source_default).setChecked(true);
				break;
			case MediaRecorder.AudioSource.MIC:
				menu.findItem(R.id.action_set_source_microphone).setChecked(true);
				break;
			case MediaRecorder.AudioSource.CAMCORDER:
				menu.findItem(R.id.action_set_source_camcorder).setChecked(true);
				break;
			case MediaRecorder.AudioSource.VOICE_RECOGNITION:
				menu.findItem(R.id.action_set_source_voice_recognition).setChecked(true);
				break;
			case MediaRecorder.AudioSource.UNPROCESSED:
				menu.findItem(R.id.action_set_source_unprocessed).setChecked(true);
				break;
		}
	}

	private void updateAudioFormatMenu() {
		menu.findItem(audioFormat == AudioFormat.ENCODING_PCM_FLOAT ? R.id.action_set_floating_point : R.id.action_set_fixed_point).setChecked(true);
	}

	private final int permissionID = 1;

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode != permissionID) return;
		for (int i = 0; i < permissions.length; ++i) {
			if (!permissions[i].equals(Manifest.permission.RECORD_AUDIO)) continue;
			if (i < grantResults.length && grantResults[i] == PackageManager.PERMISSION_GRANTED)
				startListening();
			else if (!fileMode && !captureMode)
				setStatus(R.string.audio_permission_denied);
		}
	}

	private void requestMicrophone() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
			startListening();
		else
			ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.RECORD_AUDIO }, permissionID);
	}

	@Override
	protected void onSaveInstanceState(@NonNull Bundle state) {
		state.putInt("nightMode", AppCompatDelegate.getDefaultNightMode());
		state.putInt("recordRate", recordRate);
		state.putInt("recordChannel", recordChannel);
		state.putInt("audioSource", audioSource);
		state.putInt("audioFormat", audioFormat);
		state.putBoolean("autoSave", autoSave);
		state.putBoolean("showSpectrogram", showSpectrogram);
		state.putString("language", language);
		state.putBoolean("fileMode", fileMode);
		state.putBoolean("captureMode", captureMode);
		state.putBoolean("turboDecode", turboDecode);
		state.putString("selectedFile", selectedFile == null ? null : selectedFile.toString());
		state.putString("selectedFileName", selectedFileName);
		super.onSaveInstanceState(state);
	}

	private void storeSettings() {
		SharedPreferences pref = getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor edit = pref.edit();
		edit.putInt("nightMode", AppCompatDelegate.getDefaultNightMode());
		edit.putInt("recordRate", recordRate);
		edit.putInt("recordChannel", recordChannel);
		edit.putInt("audioSource", audioSource);
		edit.putInt("audioFormat", audioFormat);
		edit.putBoolean("autoSave", autoSave);
		edit.putBoolean("showSpectrogram", showSpectrogram);
		edit.putBoolean("turboDecode", turboDecode);
		edit.putString("language", language);
		edit.apply();
	}

	@Override
	protected void onCreate(Bundle state) {
		final int defaultSampleRate = 44100;
		final int defaultChannelSelect = 0;
		final int defaultAudioSource = MediaRecorder.AudioSource.MIC;
		final int defaultAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
		final boolean defaultAutoSave = true;
		final boolean defaultShowSpectrogram = true;
		final String defaultLanguage = "system";
		if (state == null) {
			SharedPreferences pref = getPreferences(Context.MODE_PRIVATE);
			AppCompatDelegate.setDefaultNightMode(pref.getInt("nightMode", AppCompatDelegate.getDefaultNightMode()));
			recordRate = pref.getInt("recordRate", defaultSampleRate);
			recordChannel = pref.getInt("recordChannel", defaultChannelSelect);
			audioSource = pref.getInt("audioSource", defaultAudioSource);
			audioFormat = pref.getInt("audioFormat", defaultAudioFormat);
			autoSave = pref.getBoolean("autoSave", defaultAutoSave);
			showSpectrogram = pref.getBoolean("showSpectrogram", defaultShowSpectrogram);
			turboDecode = pref.getBoolean("turboDecode", false);
			language = pref.getString("language", defaultLanguage);
		} else {
			AppCompatDelegate.setDefaultNightMode(state.getInt("nightMode", AppCompatDelegate.getDefaultNightMode()));
			recordRate = state.getInt("recordRate", defaultSampleRate);
			recordChannel = state.getInt("recordChannel", defaultChannelSelect);
			audioSource = state.getInt("audioSource", defaultAudioSource);
			audioFormat = state.getInt("audioFormat", defaultAudioFormat);
			autoSave = state.getBoolean("autoSave", defaultAutoSave);
			showSpectrogram = state.getBoolean("showSpectrogram", defaultShowSpectrogram);
			language = state.getString("language", defaultLanguage);
			fileMode = state.getBoolean("fileMode");
			captureMode = state.getBoolean("captureMode") && Build.VERSION.SDK_INT >= 29;
			turboDecode = state.getBoolean("turboDecode");
			String uri = state.getString("selectedFile");
			selectedFile = uri == null ? null : Uri.parse(uri);
			selectedFileName = state.getString("selectedFileName");
		}
		super.onCreate(state);
		setLanguage(language);
		Configuration config = getResources().getConfiguration();
		EdgeToEdge.enable(this);
		setContentView(config.orientation == Configuration.ORIENTATION_LANDSCAPE ? R.layout.activity_main_land : R.layout.activity_main);
		handleInsets();
		fgColor = getColor(R.color.fg);
		thinColor = getColor(R.color.thin);
		tintColor = getColor(R.color.tint);
		scopeBuffer = new PixelBuffer(640, 2 * 1280);
		waterfallPlotBuffer = new PixelBuffer(256, 2 * 256);
		peakMeterBuffer = new PixelBuffer(1, 16);
		imageBuffer = new PixelBuffer(800, 616);
		input = new Complex();
		createScope(config);
		createWaterfallPlot(config);
		createPeakMeter();
		audioSessions = new AudioSessionManager(audioListener);
		bindInputControls();
		if (captureMode) setStatus(R.string.capture_stopped);
		ArrayList<String> permissions = new ArrayList<>();
		if (!fileMode && !captureMode && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
			permissions.add(Manifest.permission.RECORD_AUDIO);
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
			permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
		if (!permissions.isEmpty())
			ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), permissionID);
	}

	private void handleInsets() {
		ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
			Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
			v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
			return insets;
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_main, menu);
		this.menu = menu;
		updateRecordRateMenu();
		updateRecordChannelMenu();
		updateAudioSourceMenu();
		updateAudioFormatMenu();
		updateWaterfallPlotMenu();
		updateAutoSaveMenu();
		updateInputControls();
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_store_scope) {
			storeScope();
			return true;
		}
		if (id == R.id.action_toggle_mode) {
			toggleMode();
			return true;
		}
		if (id == R.id.action_auto_mode) {
			autoMode();
			return true;
		}
		if (id == R.id.action_force_raw_mode) {
			setMode(R.string.raw_mode);
			return true;
		}
		if (id == R.id.action_force_hffax_mode) {
			setMode(R.string.hf_fax);
			return true;
		}
		if (id == R.id.action_force_robot36_color) {
			setMode(R.string.robot36_color);
			return true;
		}
		if (id == R.id.action_force_robot72_color) {
			setMode(R.string.robot72_color);
			return true;
		}
		if (id == R.id.action_force_pd50) {
			setMode(R.string.pd50);
			return true;
		}
		if (id == R.id.action_force_pd90) {
			setMode(R.string.pd90);
			return true;
		}
		if (id == R.id.action_force_pd120) {
			setMode(R.string.pd120);
			return true;
		}
		if (id == R.id.action_force_pd160) {
			setMode(R.string.pd160);
			return true;
		}
		if (id == R.id.action_force_pd180) {
			setMode(R.string.pd180);
			return true;
		}
		if (id == R.id.action_force_pd240) {
			setMode(R.string.pd240);
			return true;
		}
		if (id == R.id.action_force_pd290) {
			setMode(R.string.pd290);
			return true;
		}
		if (id == R.id.action_force_martin1) {
			setMode(R.string.martin1);
			return true;
		}
		if (id == R.id.action_force_martin2) {
			setMode(R.string.martin2);
			return true;
		}
		if (id == R.id.action_force_scottie1) {
			setMode(R.string.scottie1);
			return true;
		}
		if (id == R.id.action_force_scottie2) {
			setMode(R.string.scottie2);
			return true;
		}
		if (id == R.id.action_force_scottie_dx) {
			setMode(R.string.scottie_dx);
			return true;
		}
		if (id == R.id.action_force_wraase_sc2_180) {
			setMode(R.string.wraase_sc2_180);
			return true;
		}
		if (id == R.id.action_set_record_rate_8000) {
			setRecordRate(8000);
			return true;
		}
		if (id == R.id.action_set_record_rate_16000) {
			setRecordRate(16000);
			return true;
		}
		if (id == R.id.action_set_record_rate_32000) {
			setRecordRate(32000);
			return true;
		}
		if (id == R.id.action_set_record_rate_44100) {
			setRecordRate(44100);
			return true;
		}
		if (id == R.id.action_set_record_rate_48000) {
			setRecordRate(48000);
			return true;
		}
		if (id == R.id.action_set_record_channel_default) {
			setRecordChannel(0);
			return true;
		}
		if (id == R.id.action_set_record_channel_first) {
			setRecordChannel(1);
			return true;
		}
		if (id == R.id.action_set_record_channel_second) {
			setRecordChannel(2);
			return true;
		}
		if (id == R.id.action_set_record_channel_summation) {
			setRecordChannel(3);
			return true;
		}
		if (id == R.id.action_set_record_channel_analytic) {
			setRecordChannel(4);
			return true;
		}
		if (id == R.id.action_set_source_default) {
			setAudioSource(MediaRecorder.AudioSource.DEFAULT);
			return true;
		}
		if (id == R.id.action_set_source_microphone) {
			setAudioSource(MediaRecorder.AudioSource.MIC);
			return true;
		}
		if (id == R.id.action_set_source_camcorder) {
			setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
			return true;
		}
		if (id == R.id.action_set_source_voice_recognition) {
			setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
			return true;
		}
		if (id == R.id.action_set_source_unprocessed) {
			setAudioSource(MediaRecorder.AudioSource.UNPROCESSED);
			return true;
		}
		if (id == R.id.action_set_floating_point) {
			setAudioFormat(AudioFormat.ENCODING_PCM_FLOAT);
			return true;
		}
		if (id == R.id.action_set_fixed_point) {
			setAudioFormat(AudioFormat.ENCODING_PCM_16BIT);
			return true;
		}
		if (id == R.id.action_show_spectrogram) {
			setShowSpectrogram(true);
			return true;
		}
		if (id == R.id.action_show_frequency_plot) {
			setShowSpectrogram(false);
			return true;
		}
		if (id == R.id.action_enable_auto_save) {
			setAutoSave(true);
			return true;
		}
		if (id == R.id.action_disable_auto_save) {
			setAutoSave(false);
			return true;
		}
		if (id == R.id.action_enable_night_mode) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
			return true;
		}
		if (id == R.id.action_disable_night_mode) {
			AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
			return true;
		}
		if (id == R.id.action_privacy_policy) {
			showTextPage(getString(R.string.privacy_policy_text));
			return true;
		}
		if (id == R.id.action_about) {
			showTextPage(getString(R.string.about_text, getVersionString(), getString(R.string.disclaimer)));
			return true;
		}
		if (id == R.id.action_english) {
			setLanguage("en-US");
			return true;
		}
		if (id == R.id.action_simplified_chinese) {
			setLanguage("zh-CN");
			return true;
		}
		if (id == R.id.action_russian) {
			setLanguage("ru");
			return true;
		}
		if (id == R.id.action_german) {
			setLanguage("de");
			return true;
		}
		if (id == R.id.action_brazilian_portuguese) {
			setLanguage("pt-BR");
			return true;
		}
		if (id == R.id.action_polish) {
			setLanguage("pl");
			return true;
		}
		if (id == R.id.action_ukrainian) {
			setLanguage("uk");
			return true;
		}
		if (id == R.id.action_latin_american_spanish) {
			setLanguage("es-r419");
			return true;
		}
		if (id == R.id.action_french) {
			setLanguage("fr");
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Nullable
	private String getVersionString() {
		String version;
		try {
			PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			version = packageInfo.versionName;
		} catch (PackageManager.NameNotFoundException ignored) {
			version = "N/A";
		}
		return version;
	}

	private void setLanguage(String language) {
		this.language = language;
		if (!language.equals("system"))
			AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language));
	}

	private void storeScope() {
		int width = scopeBuffer.width;
		int height = scopeBuffer.height / 2;
		int stride = scopeBuffer.width;
		int offset = stride * scopeBuffer.line;
		Bitmap bmp = Bitmap.createBitmap(scopeBuffer.pixels, offset, stride, width, height, Bitmap.Config.ARGB_8888);

		if (decoder != null)
		{
			bmp = decoder.currentMode.postProcessScopeImage(bmp);
		}

		storeBitmap(bmp);
	}

	private void createScope(Configuration config) {
		int screenWidthDp = config.screenWidthDp;
		int screenHeightDp = config.screenHeightDp;
		int waterfallPlotHeightDp = 64;
		int topFunctionBarHeightDp = 48;
		if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
			screenWidthDp /= 2;
		else
			screenHeightDp -= (waterfallPlotHeightDp + topFunctionBarHeightDp);
		int actionBarHeightDp = 64;
		screenHeightDp -= actionBarHeightDp;
		int width = scopeBuffer.width;
		int height = Math.min(Math.max((width * screenHeightDp) / screenWidthDp, 496), scopeBuffer.height / 2);
		scopeBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		int stride = scopeBuffer.width;
		int offset = stride * (scopeBuffer.line + scopeBuffer.height / 2 - height);
		scopeBitmap.setPixels(scopeBuffer.pixels, offset, stride, 0, 0, width, height);
		scopeView = findViewById(R.id.scope);
		scopeView.setScaleType(ImageView.ScaleType.FIT_CENTER);
		scopeView.setImageBitmap(scopeBitmap);
	}

	private void createWaterfallPlot(Configuration config) {
		int width = waterfallPlotBuffer.width;
		int height = waterfallPlotBuffer.height / 2;
		if (config.orientation != Configuration.ORIENTATION_LANDSCAPE)
			height /= 4;
		waterfallPlotBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		int stride = waterfallPlotBuffer.width;
		int offset = stride * waterfallPlotBuffer.line;
		waterfallPlotBitmap.setPixels(waterfallPlotBuffer.pixels, offset, stride, 0, 0, width, height);
		waterfallPlotView = findViewById(R.id.waterfall_plot);
		waterfallPlotView.setScaleType(ImageView.ScaleType.FIT_XY);
		waterfallPlotView.setImageBitmap(waterfallPlotBitmap);
	}

	private void createPeakMeter() {
		peakMeterBitmap = Bitmap.createBitmap(peakMeterBuffer.width, peakMeterBuffer.height, Bitmap.Config.ARGB_8888);
		peakMeterBitmap.setPixels(peakMeterBuffer.pixels, 0, peakMeterBuffer.width, 0, 0, peakMeterBuffer.width, peakMeterBuffer.height);
		peakMeterView = findViewById(R.id.peak_meter);
		peakMeterView.setScaleType(ImageView.ScaleType.FIT_XY);
		peakMeterView.setImageBitmap(peakMeterBitmap);
	}

	@Override
	public void onConfigurationChanged(@NonNull Configuration config) {
		super.onConfigurationChanged(config);
		setContentView(config.orientation == Configuration.ORIENTATION_LANDSCAPE ? R.layout.activity_main_land : R.layout.activity_main);
		handleInsets();
		createScope(config);
		createWaterfallPlot(config);
		createPeakMeter();
		bindInputControls();
	}

	private void showTextPage(String message) {
		View view = LayoutInflater.from(this).inflate(R.layout.text_page, null);
		TextView text = view.findViewById(R.id.message);
		text.setText(Html.fromHtml(message, Html.FROM_HTML_MODE_LEGACY));
		text.setMovementMethod(LinkMovementMethod.getInstance());
		AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.Theme_AlertDialog);
		builder.setNeutralButton(R.string.close, null);
		builder.setView(view);
		builder.show();
	}

	void storeBitmap(Bitmap bitmap) {
		Date date = new Date();
		String name = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(date);
		name += ".png";
		String title = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(date);
		ContentValues values = new ContentValues();
		File dir;
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
			dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
			if (!dir.exists() && !dir.mkdirs()) {
				showToast(R.string.creating_picture_directory_failed);
				return;
			}
			File file;
			try {
				file = new File(dir, name);
				FileOutputStream stream = new FileOutputStream(file);
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
				stream.close();
			} catch (IOException e) {
				showToast(R.string.creating_picture_file_failed);
				return;
			}
			values.put(MediaStore.Images.ImageColumns.DATA, file.toString());
		} else {
			values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
			values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/");
			values.put(MediaStore.Images.Media.IS_PENDING, 1);
		}
		values.put(MediaStore.Images.ImageColumns.TITLE, title);
		values.put(MediaStore.Images.ImageColumns.MIME_TYPE, "image/png");
		ContentResolver resolver = getContentResolver();
		Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
		if (uri == null) {
			showToast(R.string.storing_picture_failed);
			return;
		}
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
			try {
				ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri, "w");
				if (descriptor == null) {
					showToast(R.string.storing_picture_failed);
					return;
				}
				FileOutputStream stream = new FileOutputStream(descriptor.getFileDescriptor());
				bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
				stream.close();
				descriptor.close();
			} catch (IOException e) {
				showToast(R.string.storing_picture_failed);
				return;
			}
			values.clear();
			values.put(MediaStore.Images.Media.IS_PENDING, 0);
			resolver.update(uri, values, null, null);
		}
		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.putExtra(Intent.EXTRA_STREAM, uri);
		intent.setType("image/png");
		ShareActionProvider share = (ShareActionProvider) MenuItemCompat.getActionProvider(menu.findItem(R.id.menu_item_share));
		if (share != null)
			share.setShareIntent(intent);
		showToast(name);
	}

	private void showToast(String message) {
		Toast toast = Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0);
		toast.show();
	}

	private void showToast(int id) {
		showToast(getString(id));
	}

	@Override
	protected void onResume() {
		super.onResume();
		resumed = true;
		if (captureMode && decoder != null) processScope();
		startListening();
	}

	@Override
	protected void onPause() {
		resumed = false;
		boolean wasPlaying = filePlaying;
		// A bound foreground service keeps playback capture alive while another app plays.
		if (!captureMode) stopListening();
		if (wasPlaying) setStatus(R.string.audio_file_stopped);
		storeSettings();
		super.onPause();
	}

	@Override
	protected void onDestroy() {
		audioSessions.release();
		stopCapture();
		audioUi.removeCallbacksAndMessages(null);
		super.onDestroy();
	}
}
