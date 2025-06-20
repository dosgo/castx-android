package com.dosgo.castx;
// AudioEncoder.java

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import castX.CastX;

public class AudioEncoder {
    private static final String MIME_TYPE = "audio/opus";
    private final MediaProjection mediaProjection;
    private final int bitRate;

    private AudioRecord audioRecord;
    private MediaCodec audioCodec;
    private HandlerThread encoderThread;
    private volatile boolean isEncoding;
    private ScrcpySender scrcpySender;
    private int senderPort;
    private  Context context;

    public AudioEncoder(Context context,MediaProjection projection,int bitRate,int senderPort) {
        this.mediaProjection = projection;
        this.context=context;
        this.bitRate = bitRate;
        this.senderPort=senderPort;
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public void start() {
        if(isEncoding){
            return;
        }
        try {
            isEncoding=true;
            initAudioCapture();
            initCodec();
            startEncodingThread();
        } catch (Exception e) {
            Log.e("AudioEncoder", "Start failed", e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    private void initAudioCapture() {

        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(48000)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();

        AudioPlaybackCaptureConfiguration config =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build();

        if (ActivityCompat.checkSelfPermission(this.context, android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            System.out.println("Start Audio Error\r\n");
            return;
        }
        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(format)
                .setAudioPlaybackCaptureConfig(config)
                .build();
    }

    private void initCodec() throws IOException {

        MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, 48000, 2);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 4096);

        audioCodec = MediaCodec.createEncoderByType(MIME_TYPE);
        audioCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        audioCodec.start();
    }

    private void startEncodingThread() {
        if(audioRecord==null){
            return;
        }
        encoderThread = new HandlerThread("AudioEncoder");
        encoderThread.start();

        new Handler(encoderThread.getLooper()).post(() -> {
            scrcpySender=new ScrcpySender(senderPort);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            byte[] pcmBuffer = new byte[4096];
            audioRecord.startRecording();

            try{
                while (isEncoding) {

                    int read = audioRecord.read(pcmBuffer, 0, pcmBuffer.length);
                    if (read > 0) {
                        encodeAndSend(pcmBuffer, read, bufferInfo);
                    }
                }
            }catch (Exception e) {
                e.printStackTrace();
            }

        });
    }

    private void encodeAndSend(byte[] pcmData, int size,
                               MediaCodec.BufferInfo bufferInfo) {

        // 输入编码器
        int inIndex = audioCodec.dequeueInputBuffer(10000);
        if (inIndex >= 0) {
            ByteBuffer buffer = audioCodec.getInputBuffer(inIndex);
            buffer.clear();
            buffer.put(pcmData, 0, size);
            audioCodec.queueInputBuffer(inIndex, 0, size,
                    System.nanoTime() / 1000, 0);
        }

        // 获取并发送编码数据
        int outIndex;
        while ((outIndex = audioCodec.dequeueOutputBuffer(bufferInfo, 0)) >= 0) {
            ByteBuffer outBuffer = audioCodec.getOutputBuffer(outIndex);

            //boolean config = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
           try{
            scrcpySender.writeFrame(outBuffer,bufferInfo.presentationTimeUs,false,false);
           } catch (Exception e) {

           }
            audioCodec.releaseOutputBuffer(outIndex, false);
        }
    }


    public void stop() {
        isEncoding = false;
        if (encoderThread != null) encoderThread.quitSafely();
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord=null;
        }
        if (audioCodec != null) {
            audioCodec.stop();
            audioCodec.release();
            audioCodec=null;
        }

    }
}