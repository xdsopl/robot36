
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
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

public class MainActivity extends Activity {
    ImageView view;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        view = (ImageView)findViewById(R.id.image);
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

    @Override
    protected void onPause() {
        view.pause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        view.resume();
        super.onResume();
    }
}
