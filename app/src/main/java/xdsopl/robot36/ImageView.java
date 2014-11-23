
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

import android.content.Context;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class ImageView extends SurfaceView implements SurfaceHolder.Callback {
    final DecoderThread thread;
    public ImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
        thread = new DecoderThread(context, holder);
        thread.start();
    }

    public void pause() { thread.pause(); }
    public void resume() { thread.play(); }
    public void debug_sync() { thread.debug_sync(); }
    public void debug_image() { thread.debug_image(); }
    public void debug_both() { thread.debug_both(); }
    public void robot36_mode() { thread.robot36_mode(); }
    public void robot72_mode() { thread.robot72_mode(); }
    public void martin1_mode() { thread.martin1_mode(); }
    public void martin2_mode() { thread.martin2_mode(); }
    public void scottie1_mode() { thread.scottie1_mode(); }
    public void scottie2_mode() { thread.scottie2_mode(); }
    public void scottieDX_mode() { thread.scottieDX_mode(); }

    public void surfaceCreated(SurfaceHolder holder) {
        thread.surfaceCreated();
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        thread.resize(width, height);
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        thread.surfaceDestroyed();
    }
}
