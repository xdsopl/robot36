
/*
Copyright 2014 Ahmet Inan <xdsopl@googlemail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package xdsopl.robot36;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ShareActionProvider;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private Decoder decoder;
    private ImageView image;
    private Bitmap bitmap;
    private NotificationManager manager;
    private ShareActionProvider share;
    private int notifyID = 1;
    private int permissionsID = 2;
    private static final String channelID = "Robot36";
    private boolean enableAnalyzer = true;
    private Menu menu;

    private void showNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        int flags = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            flags = PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent, flags);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = manager.getNotificationChannel(channelID);
            if (channel == null) {
                channel = new NotificationChannel(channelID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW);
                channel.setDescription(getString(R.string.decoder_running));
                manager.createNotificationChannel(channel);
            }
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.decoder_running))
            .setContentIntent(pending)
            .setOngoing(true);
        manager.notify(notifyID, builder.build());
    }

    void updateTitle(final String newTitle)
    {
        if (getTitle() != newTitle) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setTitle(newTitle);
                }
            });
        }
    }

    void storeBitmap(Bitmap image) {
        bitmap = image;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Date date = new Date();
                String name = new SimpleDateFormat("yyyyMMdd_HHmmss").format(date);
                String title = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
                ContentValues values = new ContentValues();
                File dir;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                    if (!dir.exists())
                        dir.mkdirs();
                    File file;
                    try {
                        file = File.createTempFile(name + "_", ".png", dir);
                        name = file.getName();
                    } catch (IOException ignore) {
                        return;
                    }
                    FileOutputStream stream;
                    try {
                        stream = new FileOutputStream(file);
                    } catch (IOException ignore) {
                        return;
                    }
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    try {
                        stream.close();
                    } catch (IOException ignore) {
                        return;
                    }
                    values.put(MediaStore.Images.ImageColumns.DATA, file.toString());
                } else {
                    name += ".png";
                    values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                    values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/");
                    values.put(MediaStore.Images.Media.IS_PENDING, 1);
                }
                values.put(MediaStore.Images.ImageColumns.TITLE, title);
                values.put(MediaStore.Images.ImageColumns.MIME_TYPE, "image/png");
                ContentResolver resolver = getContentResolver();
                Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    FileOutputStream stream;
                    try {
                        ParcelFileDescriptor descriptor = getContentResolver().openFileDescriptor(uri,"w");
                        stream = new FileOutputStream(descriptor.getFileDescriptor());
                    } catch (IOException ignore) {
                        return;
                    }
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    try {
                        stream.close();
                    } catch (IOException ignore) {
                        return;
                    }
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    resolver.update(uri, values, null, null);
                }
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.setType("image/png");
                share.setShareIntent(intent);
                Toast toast = Toast.makeText(getApplicationContext(), name, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL, 0, 0);
                toast.show();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        changeLayoutOrientation(getResources().getConfiguration());
        image = (ImageView)findViewById(R.id.image);
        manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        startDecoder();
    }

    private void updateMenuButtons() {
        if (menu != null) {
            if (decoder != null) {
                menu.findItem(R.id.action_toggle_decoder).setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_media_pause));
                menu.setGroupEnabled(R.id.group_decoder, true);
            } else {
                menu.findItem(R.id.action_toggle_decoder).setIcon(ContextCompat.getDrawable(this, android.R.drawable.ic_media_play));
                menu.setGroupEnabled(R.id.group_decoder, false);
            }
        }
    }

    protected void stopDecoder() {
        if (decoder == null)
            return;
        decoder.destroy();
        decoder = null;
        manager.cancel(notifyID);
        updateMenuButtons();
    }

    private Intent createEmailIntent(final String subject, final String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/email");
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{ getString(R.string.email_address) });
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, text);
        return intent;
    }

    private void showErrorMessage(final String title, final String shortText, final String longText) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(shortText);
        builder.setNeutralButton(getString(R.string.btn_ok), null);
        builder.setPositiveButton(getString(R.string.btn_send_email), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String version = " " + BuildConfig.VERSION_NAME;
                String device = " (" + Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL + " " + Build.VERSION.RELEASE + ")";
                Intent intent = createEmailIntent(getString(R.string.email_subject) + version + device, longText);
                startActivity(Intent.createChooser(intent, getString(R.string.chooser_title)));
            }
        });
        builder.show();
    }

    private void showPrivacyPolicy() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.privacy_policy));
        builder.setMessage(getString(R.string.privacy_policy_text));
        builder.setNeutralButton(getString(R.string.btn_ok), null);
        builder.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode != permissionsID)
            return;
        for (int result : grantResults)
            if (result != PackageManager.PERMISSION_GRANTED)
                return;
        startDecoder();
    }

    private boolean permissionsGranted() {
        List<String> permissions = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.RECORD_AUDIO);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (permissions.isEmpty())
            return true;
        ActivityCompat.requestPermissions(this, permissions.toArray(new String[permissions.size()]), permissionsID);
        return false;
    }

    protected void startDecoder() {
        if (decoder != null)
            return;
        if (!permissionsGranted())
            return;
        try {
            decoder = new Decoder(this,
                    (SpectrumView) findViewById(R.id.spectrum),
                    (SpectrumView) findViewById(R.id.spectrogram),
                    (ImageView) findViewById(R.id.image),
                    (VUMeterView) findViewById(R.id.meter)
            );
            decoder.enable_analyzer(enableAnalyzer);
            showNotification();
            updateMenuButtons();
        } catch (Exception e) {
            showErrorMessage(getString(R.string.decoder_error), e.getMessage(), Log.getStackTraceString(e));
        }
    }

    protected void toggleDecoder() {
        if (decoder == null)
            startDecoder();
        else
            stopDecoder();
    }

    @Override
    protected void onDestroy () {
        stopDecoder();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        if (decoder != null)
            decoder.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (decoder != null)
            decoder.resume();
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        this.menu = menu;
        updateMenuButtons();
        MenuItem item = menu.findItem(R.id.menu_item_share);
        share = (ShareActionProvider)MenuItemCompat.getActionProvider(item);
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration config) {
        super.onConfigurationChanged(config);
        changeLayoutOrientation(config);
    }

    private void changeLayoutOrientation(Configuration config) {
        boolean horizontal = config.orientation == Configuration.ORIENTATION_LANDSCAPE;
        View analysis = findViewById(R.id.analysis);
        analysis.setVisibility(enableAnalyzer ? View.VISIBLE : View.GONE);
        analysis.setLayoutParams(
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT, horizontal ? 1.0f : 10.0f));
        int content_orientation = horizontal ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;
        ((LinearLayout)findViewById(R.id.content)).setOrientation(content_orientation);
        int analysis_orientation = horizontal ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL;
        ((LinearLayout)findViewById(R.id.analysis)).setOrientation(analysis_orientation);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_toggle_decoder) {
            toggleDecoder();
            return true;
        }
        if (id == R.id.action_save_image) {
            storeBitmap(image.bitmap);
            return true;
        }
        if (id == R.id.action_clear_image) {
            decoder.clear_image();
            return true;
        }
        if (id == R.id.action_sharpest_image) {
            decoder.adjust_blur(-3);
            return true;
        }
        if (id == R.id.action_sharper_image) {
            decoder.adjust_blur(-2);
            return true;
        }
        if (id == R.id.action_sharp_image) {
            decoder.adjust_blur(-1);
            return true;
        }
        if (id == R.id.action_neutral_image) {
            decoder.adjust_blur(0);
            return true;
        }
        if (id == R.id.action_soft_image) {
            decoder.adjust_blur(1);
            return true;
        }
        if (id == R.id.action_softer_image) {
            decoder.adjust_blur(2);
            return true;
        }
        if (id == R.id.action_softest_image) {
            decoder.adjust_blur(3);
            return true;
        }
        if (id == R.id.action_toggle_scaling) {
            decoder.toggle_scaling();
            return true;
        }
        if (id == R.id.action_toggle_debug) {
            decoder.toggle_debug();
            return true;
        }
        if (id == R.id.action_auto_mode) {
            decoder.auto_mode();
            return true;
        }
        if (id == R.id.action_toggle_analyzer) {
            decoder.enable_analyzer(enableAnalyzer ^= true);
            changeLayoutOrientation(getResources().getConfiguration());
            return true;
        }
        if (id == R.id.action_slow_update_rate) {
            decoder.setUpdateRate(0);
            return true;
        }
        if (id == R.id.action_normal_update_rate) {
            decoder.setUpdateRate(1);
            return true;
        }
        if (id == R.id.action_fast_update_rate) {
            decoder.setUpdateRate(2);
            return true;
        }
        if (id == R.id.action_faster_update_rate) {
            decoder.setUpdateRate(3);
            return true;
        }
        if (id == R.id.action_fastest_update_rate) {
            decoder.setUpdateRate(4);
            return true;
        }
        if (id == R.id.action_raw_mode) {
            decoder.raw_mode();
            return true;
        }
        if (id == R.id.action_robot36_mode) {
            decoder.robot36_mode();
            return true;
        }
        if (id == R.id.action_robot72_mode) {
            decoder.robot72_mode();
            return true;
        }
        if (id == R.id.action_martin1_mode) {
            decoder.martin1_mode();
            return true;
        }
        if (id == R.id.action_martin2_mode) {
            decoder.martin2_mode();
            return true;
        }
        if (id == R.id.action_scottie1_mode) {
            decoder.scottie1_mode();
            return true;
        }
        if (id == R.id.action_scottie2_mode) {
            decoder.scottie2_mode();
            return true;
        }
        if (id == R.id.action_scottieDX_mode) {
            decoder.scottieDX_mode();
            return true;
        }
        if (id == R.id.action_wraaseSC2_180_mode) {
            decoder.wraaseSC2_180_mode();
            return true;
        }
        if (id == R.id.action_pd50_mode) {
            decoder.pd50_mode();
            return true;
        }
        if (id == R.id.action_pd90_mode) {
            decoder.pd90_mode();
            return true;
        }
        if (id == R.id.action_pd120_mode) {
            decoder.pd120_mode();
            return true;
        }
        if (id == R.id.action_pd160_mode) {
            decoder.pd160_mode();
            return true;
        }
        if (id == R.id.action_pd180_mode) {
            decoder.pd180_mode();
            return true;
        }
        if (id == R.id.action_pd240_mode) {
            decoder.pd240_mode();
            return true;
        }
        if (id == R.id.action_pd290_mode) {
            decoder.pd290_mode();
            return true;
        }
        if (id == R.id.action_privacy_policy) {
            showPrivacyPolicy();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
