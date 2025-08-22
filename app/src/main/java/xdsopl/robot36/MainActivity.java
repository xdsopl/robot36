/*
Robot36

Copyright 2024 Ahmet Inan <xdsopl@gmail.com>
*/

package xdsopl.robot36;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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
import java.util.List;
import java.util.Locale;

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
	private short[] shortBuffer;
	private float[] recordBuffer;
	private AudioRecord audioRecord;
	private Decoder decoder;
	private Menu menu;
	private String currentMode;
	private String language;
	private Complex input;
	private int recordRate;
	private int recordChannel;
	private int audioSource;
	private int audioFormat;
	private int fgColor;
	private int thinColor;
	private int tintColor;
	private boolean autoSave;
	private boolean showSpectrogram;
	private final int binWidthHz = 10;
	private final int[] freqMarkers = { 1100, 1300, 1500, 2300 };

	private void setStatus(int id) {
		setTitle(id);
	}

	private void setStatus(String str) {
		setTitle(str);
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

	private final AudioRecord.OnRecordPositionUpdateListener recordListener = new AudioRecord.OnRecordPositionUpdateListener() {
		@Override
		public void onMarkerReached(AudioRecord ignore) {
		}

		@Override
		public void onPeriodicNotification(AudioRecord audioRecord) {
			if (shortBuffer == null) {
				audioRecord.read(recordBuffer, 0, recordBuffer.length, AudioRecord.READ_BLOCKING);
			} else {
				audioRecord.read(shortBuffer, 0, shortBuffer.length, AudioRecord.READ_BLOCKING);
				for (int i = 0; i < shortBuffer.length; ++i)
					recordBuffer[i] = .000030517578125f * shortBuffer[i];
			}
			processPeakMeter();
			if (showSpectrogram)
				processSpectrogram();
			boolean newLines = decoder.process(recordBuffer, recordChannel);
			if (!showSpectrogram)
				processFreqPlot();
			if (newLines) {
				processScope();
				processImage();
				setStatus(decoder.currentMode.getName());
			}
		}
	};

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

	private void processSpectrogram() {
		boolean process = false;
		int channels = recordChannel > 0 ? 2 : 1;
		for (int j = 0; j < recordBuffer.length / channels; ++j) {
			switch (recordChannel) {
				case 1:
					input.set(recordBuffer[2 * j]);
					break;
				case 2:
					input.set(recordBuffer[2 * j + 1]);
					break;
				case 3:
					input.set(recordBuffer[2 * j] + recordBuffer[2 * j + 1]);
					break;
				case 4:
					input.set(recordBuffer[2 * j], recordBuffer[2 * j + 1]);
					break;
				default:
					input.set(recordBuffer[j]);
			}
			if (stft.push(input)) {
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
		int channels = recordChannel > 0 ? 2 : 1;
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

	private void initAudioRecord() {
		boolean rateChanged = true;
		if (audioRecord != null) {
			rateChanged = audioRecord.getSampleRate() != recordRate;
			boolean channelChanged = audioRecord.getChannelCount() != (recordChannel == 0 ? 1 : 2);
			boolean sourceChanged = audioRecord.getAudioSource() != audioSource;
			boolean formatChanged = audioRecord.getAudioFormat() != audioFormat;
			if (!rateChanged && !channelChanged && !sourceChanged && !formatChanged)
				return;
			stopListening();
			audioRecord.release();
			audioRecord = null;
		}
		int channelConfig = AudioFormat.CHANNEL_IN_MONO;
		int channelCount = 1;
		if (recordChannel != 0) {
			channelCount = 2;
			channelConfig = AudioFormat.CHANNEL_IN_STEREO;
		}
		int sampleSize = audioFormat == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
		int frameSize = sampleSize * channelCount;
		int readsPerSecond = 50;
		int bufferSize = Integer.highestOneBit(recordRate) * frameSize;
		int frameCount = recordRate / readsPerSecond;
		int bufferCount = frameCount * channelCount;
		recordBuffer = new float[bufferCount];
		shortBuffer = audioFormat == AudioFormat.ENCODING_PCM_FLOAT ? null : new short[bufferCount];
		try {
			audioRecord = new AudioRecord(audioSource, recordRate, channelConfig, audioFormat, bufferSize);
			if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
				audioRecord.setRecordPositionUpdateListener(recordListener);
				audioRecord.setPositionNotificationPeriod(frameCount);
				if (rateChanged) {
					decoder = new Decoder(scopeBuffer, imageBuffer, getString(R.string.raw_mode), recordRate);
					decoder.setMode(currentMode);
					stft = new ShortTimeFourierTransform(recordRate / binWidthHz, 3);
				}
				startListening();
			} else {
				audioRecord.release();
				audioRecord = null;
				setStatus(R.string.audio_init_failed);
			}
		} catch (IllegalArgumentException e) {
			setStatus(R.string.audio_setup_failed);
		} catch (SecurityException e) {
			setStatus(R.string.audio_permission_denied);
		}
	}

	private void startListening() {
		if (audioRecord != null) {
			audioRecord.startRecording();
			if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
				if (shortBuffer == null)
					audioRecord.read(recordBuffer, 0, recordBuffer.length, AudioRecord.READ_BLOCKING);
				else
					audioRecord.read(shortBuffer, 0, recordBuffer.length, AudioRecord.READ_BLOCKING);
				setStatus(R.string.listening);
			} else {
				setStatus(R.string.audio_recording_error);
			}
		}
	}

	private void stopListening() {
		if (audioRecord != null)
			audioRecord.stop();
	}

	private void setRecordRate(int newSampleRate) {
		if (recordRate == newSampleRate)
			return;
		recordRate = newSampleRate;
		updateRecordRateMenu();
		initAudioRecord();
	}

	private void setRecordChannel(int newChannelSelect) {
		if (recordChannel == newChannelSelect)
			return;
		recordChannel = newChannelSelect;
		updateRecordChannelMenu();
		initAudioRecord();
	}

	private void setAudioSource(int newAudioSource) {
		if (audioSource == newAudioSource)
			return;
		audioSource = newAudioSource;
		updateAudioSourceMenu();
		initAudioRecord();
	}

	private void setAudioFormat(int newAudioFormat) {
		if (audioFormat == newAudioFormat)
			return;
		audioFormat = newAudioFormat;
		updateAudioFormatMenu();
		initAudioRecord();
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
		if (requestCode != permissionID)
			return;
		for (int i = 0; i < permissions.length; ++i)
			if (permissions[i].equals(Manifest.permission.RECORD_AUDIO) && grantResults[i] == PackageManager.PERMISSION_GRANTED)
				initAudioRecord();
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
		List<String> permissions = new ArrayList<>();
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			permissions.add(Manifest.permission.RECORD_AUDIO);
			setStatus(R.string.audio_permission_denied);
		} else {
			initAudioRecord();
		}
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
			showTextPage(getString(R.string.about_text, BuildConfig.VERSION_NAME, getString(R.string.disclaimer)));
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
		if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
			screenWidthDp /= 2;
		else
			screenHeightDp -= waterfallPlotHeightDp;
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
		startListening();
		super.onResume();
	}

	@Override
	protected void onPause() {
		stopListening();
		storeSettings();
		super.onPause();
	}
}
