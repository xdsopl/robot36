
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
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.view.SurfaceHolder;

public class DecoderThread extends Thread {
    int canvasWidth = -1, canvasHeight = -1;
    boolean takeABreak = true, cantTouchThis = true;
    int imageWidth = 320;
    int imageHeight = 240;
    final SurfaceHolder holder;
    final Bitmap bitmap;
    final Paint paint;
    AudioRecord audio;
    final int audioSource = MediaRecorder.AudioSource.MIC;
    final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    final int sampleRate = 44100;
    final short[] audioBuffer;
    final int[] pixelBuffer;

    RenderScript rs;
    Allocation rsDecoderAudioBuffer, rsDecoderPixelBuffer, rsDecoderValueBuffer;
    ScriptC_decoder rsDecoder;

    public DecoderThread(Context context, SurfaceHolder holder) {
        this.holder = holder;
        paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        bitmap = Bitmap.createBitmap(2048, 512, Bitmap.Config.ARGB_8888);
        pixelBuffer = new int[bitmap.getWidth() * bitmap.getHeight()];

        int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        int bufferSizeInSamples = bufferSizeInBytes / 2;
        int framesPerSecond = Math.max(1, sampleRate / bufferSizeInSamples);
        audioBuffer = new short[framesPerSecond * bufferSizeInSamples];

        int maxHorizontalLength = 2 * sampleRate;

        rs = RenderScript.create(context);
        rsDecoderAudioBuffer = Allocation.createSized(rs, Element.I16(rs), audioBuffer.length, Allocation.USAGE_SHARED | Allocation.USAGE_SCRIPT);
        rsDecoderValueBuffer = Allocation.createSized(rs, Element.U8(rs), maxHorizontalLength, Allocation.USAGE_SCRIPT);
        rsDecoderPixelBuffer = Allocation.createSized(rs, Element.I32(rs), pixelBuffer.length, Allocation.USAGE_SHARED | Allocation.USAGE_SCRIPT);
        rsDecoder = new ScriptC_decoder(rs);
        rsDecoder.bind_audio_buffer(rsDecoderAudioBuffer);
        rsDecoder.bind_value_buffer(rsDecoderValueBuffer);
        rsDecoder.bind_pixel_buffer(rsDecoderPixelBuffer);
        rsDecoder.invoke_initialize(sampleRate, maxHorizontalLength, bitmap.getWidth(), bitmap.getHeight());
    }
    void debug_sync() {
        synchronized (holder) {
            imageWidth = 320;
            imageHeight = bitmap.getHeight();
            rsDecoder.invoke_debug_sync();
        }
    }
    void debug_image() {
        synchronized (holder) {
            imageWidth = 320;
            imageHeight = bitmap.getHeight();
            rsDecoder.invoke_debug_image();
        }
    }
    void debug_both() {
        synchronized (holder) {
            imageWidth = 320;
            imageHeight = bitmap.getHeight();
            rsDecoder.invoke_debug_both();
        }
    }
    void robot36_mode() {
        synchronized (holder) {
            imageWidth = 320;
            imageHeight = 240;
            rsDecoder.invoke_robot36_mode();
        }
    }
    void robot72_mode() {
        synchronized (holder) {
            imageWidth = 320;
            imageHeight = 240;
            rsDecoder.invoke_robot72_mode();
        }
    }
    void martin1_mode() {
        synchronized (holder) {
            imageWidth = 320;
            imageHeight = 256;
            rsDecoder.invoke_martin1_mode();
        }
    }
    void martin2_mode() {
        synchronized (holder) {
            imageWidth = 320;
            imageHeight = 256;
            rsDecoder.invoke_martin2_mode();
        }
    }
    void scottie1_mode() {
        synchronized (holder) {
            imageWidth = 320;
            imageHeight = 256;
            rsDecoder.invoke_scottie1_mode();
        }
    }
    void scottie2_mode() {
        synchronized (holder) {
            imageWidth = 320;
            imageHeight = 256;
            rsDecoder.invoke_scottie2_mode();
        }
    }
    void scottieDX_mode() {
        synchronized (holder) {
            imageWidth = 320;
            imageHeight = 256;
            rsDecoder.invoke_scottieDX_mode();
        }
    }
    void resize(int width, int height){
        synchronized (holder) {
            canvasWidth = width;
            canvasHeight = height;
        }
    }
    void pause() {
        synchronized (holder) {
            takeABreak = true;
            if (audio != null) {
                audio.stop();
                audio.release();
                audio = null;
            }
        }
    }
    void play() {
        synchronized (holder) {
            takeABreak = false;
            if (audio == null) {
                audio = new AudioRecord(audioSource, sampleRate, channelConfig, audioFormat, audioBuffer.length * 2);
                audio.startRecording();
            }
            holder.notify();
        }
    }
    void surfaceCreated() {
        synchronized (holder) {
            cantTouchThis = false;
        }
        play();
    }
    void surfaceDestroyed() {
        synchronized (holder) {
            cantTouchThis = true;
        }
        pause();
    }
    void draw(Canvas canvas) {
        float sx, sy, px, py;
        if (imageWidth * canvasHeight < canvasWidth * bitmap.getHeight()) {
            sx = (float)canvasHeight * imageWidth / (bitmap.getWidth() * bitmap.getHeight());
            sy = (float)canvasHeight / bitmap.getHeight();
            px = (canvasWidth - sx * bitmap.getWidth()) / 2.0f;
            py = 0.0f;
        } else {
            sx = (float)canvasWidth / bitmap.getWidth();
            sy = (float)canvasWidth * imageHeight / (bitmap.getHeight() * imageWidth);
            px = 0.0f;
            py = (canvasHeight - sy * bitmap.getHeight()) / 2.0f;
        }
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
    }
    @Override
    public void run() {
        while (true) {
            synchronized (holder) {
                while (cantTouchThis || takeABreak) {
                    try {
                        holder.wait();
                    } catch (InterruptedException e) {
                    }
                }
                decode();
                Canvas canvas = null;
                try {
                    canvas = holder.lockCanvas(null);
                    draw(canvas);
                } finally {
                    if (canvas != null)
                        holder.unlockCanvasAndPost(canvas);
                }
            }
        }
    }
}
