/*
 Copyright 2015 Ahmet Inan <xdsopl@googlemail.com>

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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class SpectrumView extends SurfaceView implements SurfaceHolder.Callback {
    private int canvasWidth = -1, canvasHeight = -1;
    private boolean cantTouchThis = true;
    private final SurfaceHolder holder;
    public final Bitmap bitmap;
    private final Paint paint;

    public SpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        holder = getHolder();
        holder.addCallback(this);
        paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        bitmap = Bitmap.createBitmap(256, 64, Bitmap.Config.ARGB_8888);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        synchronized (holder) {
            canvasWidth = width;
            canvasHeight = height;
        }
        drawCanvas();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (holder) {
            cantTouchThis = false;
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        synchronized (holder) {
            cantTouchThis = true;
        }
    }

    void drawCanvas() {
        synchronized (holder) {
            if (cantTouchThis)
                return;
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas(null);
                drawBitmap(canvas);
            } finally {
                if (canvas != null)
                    holder.unlockCanvasAndPost(canvas);
            }
        }
    }

    void drawBitmap(Canvas canvas) {
        canvas.drawColor(Color.BLACK);
        canvas.save();
        float sx = (float)canvasWidth / bitmap.getWidth();
        float sy = (float)canvasHeight / bitmap.getHeight();
        canvas.scale(sx, sy, 0, 0);
        canvas.drawBitmap(bitmap, 0, 0, paint);
        canvas.restore();
    }
}