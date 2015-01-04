
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
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;

public class ImageView extends SurfaceView implements SurfaceHolder.Callback {
    private int canvasWidth = -1, canvasHeight = -1;
    private boolean takeABreak = true, cantTouchThis = true, quitThread = false;
    private int imageWidth = 320;
    private int imageHeight = 240;
    private boolean intScale = false;
    MainActivity activity;
    private final SurfaceHolder holder;
    private final Bitmap bitmap;
    private final Paint paint;
    private final AudioRecord audio;
    private final int audioSource = MediaRecorder.AudioSource.MIC;
    private final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private final int sampleRate = 44100;
    private final short[] audioBuffer;
    private final int[] pixelBuffer;
    private final int[] currentMode;
    private final int[] savedBuffer;
    private final int[] savedWidth;
    private final int[] savedHeight;

    private final RenderScript rs;
    private final Allocation rsDecoderAudioBuffer;
    private final Allocation rsDecoderPixelBuffer;
    private final Allocation rsDecoderValueBuffer;
    private final Allocation rsDecoderCurrentMode;
    private final Allocation rsDecoderSavedBuffer;
    private final Allocation rsDecoderSavedWidth;
    private final Allocation rsDecoderSavedHeight;
    private final ScriptC_decoder rsDecoder;

    private final int mode_raw = 0;
    private final int mode_robot36 = 1;
    private final int mode_robot72 = 2;
    private final int mode_martin1 = 3;
    private final int mode_martin2 = 4;
    private final int mode_scottie1 = 5;
    private final int mode_scottie2 = 6;
    private final int mode_scottieDX = 7;
    private final int mode_wrasseSC2_180 = 8;

    private final Thread thread = new Thread() {
        @Override
        public void run() {
            while (true) {
                synchronized (this) {
                    if (quitThread)
                        return;
                    drawCanvas();
                }
                decode();
            }
        }
    };

    public ImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        holder = getHolder();
        holder.addCallback(this);

        paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        bitmap = Bitmap.createBitmap(320, 384, Bitmap.Config.ARGB_8888);
        pixelBuffer = new int[bitmap.getWidth() * bitmap.getHeight()];

        int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        int bufferSizeInSamples = bufferSizeInBytes / 2;
        int framesPerSecond = Math.max(1, sampleRate / bufferSizeInSamples);
        audioBuffer = new short[framesPerSecond * bufferSizeInSamples];
        audio = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, audioBuffer.length * 2);
        audio.startRecording();

        int minValueBufferLength = 2 * sampleRate;
        int valueBufferLength = Integer.highestOneBit(minValueBufferLength);
        if (minValueBufferLength > valueBufferLength)
            valueBufferLength <<= 1;

        currentMode = new int[1];
        savedWidth = new int[1];
        savedHeight = new int[1];
        savedBuffer = new int[pixelBuffer.length];

        rs = RenderScript.create(context);
        rsDecoderAudioBuffer = Allocation.createSized(rs, Element.I16(rs), audioBuffer.length, Allocation.USAGE_SHARED | Allocation.USAGE_SCRIPT);
        rsDecoderValueBuffer = Allocation.createSized(rs, Element.U8(rs), valueBufferLength, Allocation.USAGE_SCRIPT);
        rsDecoderPixelBuffer = Allocation.createSized(rs, Element.I32(rs), pixelBuffer.length, Allocation.USAGE_SHARED | Allocation.USAGE_SCRIPT);
        rsDecoderCurrentMode = Allocation.createSized(rs, Element.I32(rs), 1, Allocation.USAGE_SHARED | Allocation.USAGE_SCRIPT);
        rsDecoderSavedWidth = Allocation.createSized(rs, Element.I32(rs), 1, Allocation.USAGE_SHARED | Allocation.USAGE_SCRIPT);
        rsDecoderSavedHeight = Allocation.createSized(rs, Element.I32(rs), 1, Allocation.USAGE_SHARED | Allocation.USAGE_SCRIPT);
        rsDecoderSavedBuffer = Allocation.createSized(rs, Element.I32(rs), savedBuffer.length, Allocation.USAGE_SHARED | Allocation.USAGE_SCRIPT);
        rsDecoder = new ScriptC_decoder(rs);
        rsDecoder.bind_audio_buffer(rsDecoderAudioBuffer);
        rsDecoder.bind_value_buffer(rsDecoderValueBuffer);
        rsDecoder.bind_pixel_buffer(rsDecoderPixelBuffer);
        rsDecoder.bind_current_mode(rsDecoderCurrentMode);
        rsDecoder.bind_saved_width(rsDecoderSavedWidth);
        rsDecoder.bind_saved_height(rsDecoderSavedHeight);
        rsDecoder.bind_saved_buffer(rsDecoderSavedBuffer);
        rsDecoder.invoke_initialize(sampleRate, valueBufferLength, bitmap.getWidth(), bitmap.getHeight());

        thread.start();
    }

    void toggle_scaling() { intScale ^= true; }
    void softer_image() { rsDecoder.invoke_incr_blur(); }
    void sharper_image() { rsDecoder.invoke_decr_blur(); }
    void toggle_debug() { rsDecoder.invoke_toggle_debug(); }
    void toggle_auto() { rsDecoder.invoke_toggle_auto(); }
    void raw_mode() { rsDecoder.invoke_raw_mode(); }
    void robot36_mode() { rsDecoder.invoke_robot36_mode(); }
    void robot72_mode() { rsDecoder.invoke_robot72_mode(); }
    void martin1_mode() { rsDecoder.invoke_martin1_mode(); }
    void martin2_mode() { rsDecoder.invoke_martin2_mode(); }
    void scottie1_mode() { rsDecoder.invoke_scottie1_mode(); }
    void scottie2_mode() { rsDecoder.invoke_scottie2_mode(); }
    void scottieDX_mode() { rsDecoder.invoke_scottieDX_mode(); }
    void wrasseSC2_180_mode() { rsDecoder.invoke_wrasseSC2_180_mode(); }

    void updateTitle(int id) { activity.updateTitle(activity.getString(id)); }
    void switch_mode(int mode)
    {
        switch (mode) {
            case mode_raw:
                imageWidth = bitmap.getWidth();
                imageHeight = bitmap.getHeight();
                updateTitle(R.string.action_raw_mode);
                break;
            case mode_robot36:
                imageWidth = 320;
                imageHeight = 240;
                updateTitle(R.string.action_robot36_mode);
                break;
            case mode_robot72:
                imageWidth = 320;
                imageHeight = 240;
                updateTitle(R.string.action_robot72_mode);
                break;
            case mode_martin1:
                imageWidth = 320;
                imageHeight = 256;
                updateTitle(R.string.action_martin1_mode);
                break;
            case mode_martin2:
                imageWidth = 320;
                imageHeight = 256;
                updateTitle(R.string.action_martin2_mode);
                break;
            case mode_scottie1:
                imageWidth = 320;
                imageHeight = 256;
                updateTitle(R.string.action_scottie1_mode);
                break;
            case mode_scottie2:
                imageWidth = 320;
                imageHeight = 256;
                updateTitle(R.string.action_scottie2_mode);
                break;
            case mode_scottieDX:
                imageWidth = 320;
                imageHeight = 256;
                updateTitle(R.string.action_scottieDX_mode);
                break;
            case mode_wrasseSC2_180:
                imageWidth = 320;
                imageHeight = 256;
                updateTitle(R.string.action_wrasseSC2_180_mode);
                break;
            default:
                break;
        }
    }
    void pause() {
        synchronized (thread) {
            takeABreak = true;
        }
    }
    void resume() {
        synchronized (thread) {
            takeABreak = false;
        }
    }
    void destroy() {
        synchronized (thread) {
            quitThread = true;
        }
        try {
            thread.join();
        } catch (InterruptedException ignore) {
        }
        audio.stop();
        audio.release();
    }
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        synchronized (thread) {
            canvasWidth = width;
            canvasHeight = height;
            drawCanvas();
        }
    }
    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (thread) {
            cantTouchThis = false;
        }
    }
    public void surfaceDestroyed(SurfaceHolder holder) {
        synchronized (thread) {
            cantTouchThis = true;
        }
    }

    void drawCanvas() {
        if (cantTouchThis || takeABreak)
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
            sx = (float)Math.floor(sx);
            sy = (float)Math.floor(sy);
        }
        float px = (canvasWidth - sx * bitmap.getWidth()) / 2.0f;
        float py = (canvasHeight - sy * bitmap.getHeight()) / 2.0f;
        canvas.drawColor(Color.BLACK);
        canvas.save();
        canvas.scale(sx, sy, px, py);
        canvas.drawBitmap(bitmap, px, py, paint);
        canvas.restore();
    }
    void decode() {
        int samples = audio.read(audioBuffer, 0, audioBuffer.length);
        if (samples <= 0)
            return;

        rsDecoderAudioBuffer.copyFrom(audioBuffer);
        rsDecoder.invoke_decode(samples);
        rsDecoderPixelBuffer.copyTo(pixelBuffer);
        bitmap.setPixels(pixelBuffer, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        rsDecoderCurrentMode.copyTo(currentMode);
        switch_mode(currentMode[0]);

        rsDecoderSavedHeight.copyTo(savedHeight);
        if (savedHeight[0] > 0) {
            rsDecoderSavedWidth.copyTo(savedWidth);
            rsDecoderSavedBuffer.copyTo(savedBuffer);
            activity.storeBitmap(Bitmap.createBitmap(savedBuffer, savedWidth[0], savedHeight[0], Bitmap.Config.ARGB_8888));
        }
    }
}
