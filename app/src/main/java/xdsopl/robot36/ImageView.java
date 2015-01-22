
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class ImageView extends SurfaceView implements SurfaceHolder.Callback {
    private int canvasWidth = -1, canvasHeight = -1;
    private boolean cantTouchThis = true;
    public int imageWidth = 320;
    public int imageHeight = 240;
    public boolean intScale = false;
    private final SurfaceHolder holder;
    public final Bitmap bitmap;
    private final Paint paint;

    public ImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        holder = getHolder();
        holder.addCallback(this);

        paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        bitmap = Bitmap.createBitmap(320, 384, Bitmap.Config.ARGB_8888);
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        synchronized (this.holder) {
            canvasWidth = width;
            canvasHeight = height;
        }
        drawCanvas();
    }

    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (this.holder) {
            cantTouchThis = false;
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        synchronized (this.holder) {
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
        float sx, sy;
        if (imageWidth * canvasHeight < canvasWidth * bitmap.getHeight()) {
            sy = (float)canvasHeight / bitmap.getHeight();
            sx = sy * imageWidth / bitmap.getWidth();
        } else {
            sx = (float)canvasWidth / bitmap.getWidth();
            sy = (float)canvasWidth / imageWidth;
        }
        if (intScale) {
            sx = (float)Math.max(1, Math.floor(sx));
            sy = (float)Math.max(1, Math.floor(sy));
        }
        float px = (canvasWidth - sx * bitmap.getWidth()) / 2.0f;
        float py = (canvasHeight - sy * bitmap.getHeight()) / 2.0f;
        canvas.drawColor(Color.BLACK);
        canvas.save();
        canvas.scale(sx, sy, px, py);
        canvas.drawBitmap(bitmap, px, py, paint);
        canvas.restore();
    }
}
