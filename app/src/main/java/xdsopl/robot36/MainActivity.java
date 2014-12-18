
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

import android.app.Activity;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.view.Menu;
import android.view.MenuItem;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private ImageView view;
    private Bitmap bitmap;
    private NotificationManager manager;
    private int notifyID = 1;

    private void showNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.decoder_running))
            .setContentIntent(pending);
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
                String name = new SimpleDateFormat("yyyyMMdd_HHmmss_").format(new Date());
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                if (!dir.exists())
                    dir.mkdirs();
                File file;
                FileOutputStream stream;
                try {
                    file = File.createTempFile(name, ".png", dir);
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
                Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                intent.setData(Uri.fromFile(file));
                sendBroadcast(intent);
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        view = (ImageView)findViewById(R.id.image);
        view.activity = this;
        manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    protected void onDestroy () {
        view.destroy();
        manager.cancel(notifyID);
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        view.pause();
        showNotification();
        super.onPause();
    }

    @Override
    protected void onResume() {
        view.resume();
        manager.cancel(notifyID);
        super.onResume();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        switch (id) {
            case R.id.action_softer_image:
                view.softer_image();
                return true;
            case R.id.action_sharper_image:
                view.sharper_image();
                return true;
            case R.id.action_robot36_mode:
                view.robot36_mode();
                return true;
            case R.id.action_robot72_mode:
                view.robot72_mode();
                return true;
            case R.id.action_martin1_mode:
                view.martin1_mode();
                return true;
            case R.id.action_martin2_mode:
                view.martin2_mode();
                return true;
            case R.id.action_scottie1_mode:
                view.scottie1_mode();
                return true;
            case R.id.action_scottie2_mode:
                view.scottie2_mode();
                return true;
            case R.id.action_scottieDX_mode:
                view.scottieDX_mode();
                return true;
            case R.id.action_wrasseSC2_180_mode:
                view.wrasseSC2_180_mode();
                return true;
            case R.id.action_debug_sync:
                view.debug_sync();
                return true;
            case R.id.action_debug_image:
                view.debug_image();
                return true;
            case R.id.action_debug_both:
                view.debug_both();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
