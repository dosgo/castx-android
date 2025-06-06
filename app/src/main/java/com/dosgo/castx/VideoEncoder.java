package com.dosgo.castx;

import static android.content.ContentValues.TAG;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import castX.CastX;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

// VideoEncoder.java
public class VideoEncoder {
    private   String MIME_TYPE =    MediaFormat.MIMETYPE_VIDEO_AVC;;
    private int srcWidth;
    private int srcHeight;

    private  int width;
    private  int height;
    private final int frameRate;
    private MediaCodec mediaCodec;
    private Surface inputSurface;
    private VirtualDisplay virtualDisplay;
    private HandlerThread encoderThread;
    private volatile boolean isRunning;
    private  int dpi=180;
    private final VideoEncoder mythis;
    private int maxSize=1920;


    public  void limit( ) {
        assert maxSize >= 0 : "Max size may not be negative";
        assert maxSize % 8 == 0 : "Max size must be a multiple of 8";

        if (maxSize == 0) {
            // No limit
            this.width =this.srcWidth;
            this.height=this.srcHeight;
            return;
        }

        boolean portrait = srcHeight > srcWidth;
        int major = portrait ? srcHeight : srcWidth;
        if (major <= maxSize) {

        }

        int minor = portrait ? srcWidth : srcHeight;

        int newMajor = maxSize;
        int newMinor = maxSize * minor / major;

        this.width = portrait ? newMinor : newMajor;
        this.height= portrait ? newMajor : newMinor;
        System.out.println("  this.width :"+  this.width );
        System.out.println("  this.height :"+  this.height );
        System.out.println("  maxSize:"+  maxSize );
    }

    public VideoEncoder(MediaProjection _mediaProjection, int width, int height, int frameRate,String mimeType) {
        this.srcWidth = width;
        this.srcHeight = height;
        this.limit();
        this.frameRate = frameRate;
        if (!mimeType.isEmpty()) {
            this.MIME_TYPE = mimeType;
        }
        mythis=this;
        _mediaProjection.registerCallback(mMediaProjectionCallback, new Handler());
        createVirtualDisplay(_mediaProjection);
    }

    public void setMaxSize(int _maxSize) {
        int srcMaxSize=this.srcWidth>this.srcHeight?this.srcWidth:this.srcHeight;
        if(_maxSize>srcMaxSize){
            this.maxSize=0;
        }else{
            this.maxSize=_maxSize;
        }
    }

    private void initCodec() {
        try {
            System.out.println("MIME_TYPE:"+this.MIME_TYPE);
            mediaCodec = MediaCodec.createEncoderByType(this.MIME_TYPE);
            Bundle params = new Bundle();
            params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            mediaCodec.setParameters(params);
            MediaFormat format = MediaFormat.createVideoFormat(this.MIME_TYPE, width, height);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, 
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, (int) (width*height*1));
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);

            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 10); // 关键帧间隔
            format.setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 100_000); //  repeat after 100ms
            //format.setInteger(MediaFormat.KEY_LATENCY, 1); // 低延迟模式（部分设备支持）
             if(this.MIME_TYPE.equals( MediaFormat.MIMETYPE_VIDEO_AVC)) {
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline);
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            }
            if(this.MIME_TYPE.equals( MediaFormat.MIMETYPE_VIDEO_VP9)) {
                format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.VP9Profile0);
                format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);
            }
            mediaCodec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = mediaCodec.createInputSurface();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private VirtualDisplay.Callback mVirtualDisplayCallback = new VirtualDisplay.Callback() {
        @Override
        public void onPaused() {
            Log.d(TAG, "VirtualDisplay paused");
            // 处理暂停事件（如暂停视频编码）
        }

        @Override
        public void onResumed() {
            Log.d(TAG, "VirtualDisplay resumed");
            // 处理恢复事件（如恢复视频编码）
        }

        @Override
        public void onStopped() {
            Log.d(TAG, "VirtualDisplay stopped");
            // 释放虚拟显示和相关资源

        }
    };
    private MediaProjection.Callback mMediaProjectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            System.out.println( "MediaProjection stopped");

        }
        @Override
        public void onCapturedContentResize(int width, int height) {
            if(mythis.srcWidth!=width&&mythis.srcWidth>0) {
                stop();
                System.out.println("onCapturedContentResize width:"+width+"height:"+height);
                mythis.srcWidth=width;
                mythis.srcHeight=height;
                mythis.limit();
                start();
                //通知修改了大小
                CastX.setSize(width,height,mythis.width,mythis.height,width>height?1:0);
            }
        }

    };

    private  void  updateVirtualDisplay(){
        virtualDisplay.resize( width,
                height,dpi);
        virtualDisplay.setSurface(inputSurface);

    }

    public void updateBitrate(){
        Bundle params = new Bundle();
        params.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, 2_000_000);
        params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
        mediaCodec.setParameters(params);
    }
    private void createVirtualDisplay( MediaProjection mediaProjection) {
        // 3. 创建虚拟显示
        if(virtualDisplay==null) {
            virtualDisplay =  mediaProjection.createVirtualDisplay(
                    "ScreenCastVideo",
                    width,
                    height,
                    dpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    null,
                    mVirtualDisplayCallback,
                    null
            );
            System.out.println("createVirtualDisplay");
        }
    }

    public void start() {
        if(!isRunning) {
            initCodec();
            updateVirtualDisplay();
            isRunning = true;
            encoderThread = new HandlerThread("VideoEncoder");
            encoderThread.start();
            mediaCodec.start();
            new Handler(encoderThread.getLooper()).post(this::encodeLoop);
        }
    }

    public void stop() {
        if(virtualDisplay!=null){
            virtualDisplay.setSurface(null);
        }
        isRunning = false;
        if (encoderThread != null) {
            encoderThread.quitSafely();
        }
        if (inputSurface != null) {
            inputSurface.release();
        }
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec=null;
        }
    }


    private void encodeLoop()  {
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        System.out.println("encodeLoop start\r\n");
        try {
            while (isRunning) {
                if(mediaCodec==null){
                    break;
                }
                int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);
                if (outIndex >= 0) {
                    ByteBuffer encodedData = mediaCodec.getOutputBuffer(outIndex);
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0) {
                        MediaFormat newFormat = mediaCodec.getOutputFormat();
                        ByteBuffer sps = newFormat.getByteBuffer("csd-0");
                        ByteBuffer pps = newFormat.getByteBuffer("csd-1");
                        if (sps != null) {
                            writeH264Head(sps, bufferInfo);
                            writeH264Head(pps, bufferInfo);
                           // System.out.println("encodeLoop sps pps\r\n");
                        }
                    }
                    writeH264Data(encodedData, bufferInfo);
                    mediaCodec.releaseOutputBuffer(outIndex, false);
                } else {
                    if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // 获取SPS和PPS信息
                        MediaFormat newFormat = mediaCodec.getOutputFormat();
                        ByteBuffer sps = newFormat.getByteBuffer("csd-0");
                        ByteBuffer pps = newFormat.getByteBuffer("csd-1");
                        writeH264Head(sps, bufferInfo);
                        writeH264Head(pps, bufferInfo);
                       // System.out.println("encodeLoop sps pps\r\n");
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("startEncoderException");
            e.printStackTrace();
        }
        System.out.println("encodeLoop end\r\n");
    }
    private void writeH264Data(ByteBuffer buffer, MediaCodec.BufferInfo info) throws IOException {
        if (info.size > 0) {
            byte[] data = new byte[info.size];
            buffer.position(info.offset);
            buffer.get(data, 0, info.size);
            CastX.sendVideo(data,info.presentationTimeUs);
        }
    }
    private void writeH264Head(ByteBuffer buffer, MediaCodec.BufferInfo info) throws IOException {
        int bufLen=buffer.limit();
        if (bufLen > 0) {
            byte[] data = new byte[bufLen];
            buffer.position(0);
            buffer.get(data, 0, bufLen);
            // 5. 发送数据包
            byte[] head = {0x00,0x00,0x00,0x01};
            byte[] merged = new byte[4 + bufLen];
            System.arraycopy(head, 0, merged, 0,4);
            System.arraycopy(data, 0, merged, 4, bufLen);
            CastX.sendVideo(merged,info.presentationTimeUs);
        }
    }


    public void release() {
        isRunning = false;
        if (encoderThread != null) {
            encoderThread.quitSafely();
        }
        if (mediaCodec != null) {
            mediaCodec.stop();
            mediaCodec.release();
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
    }
}

class H264Frame {
    final byte[] data;
    final long timestamp; // 单位微秒(μs)
    final boolean isKeyFrame;
    H264Frame(byte[] data, long timestamp,boolean isKeyFrame) {
        this.data = Arrays.copyOf(data, data.length);
        this.timestamp = timestamp;
        this.isKeyFrame=isKeyFrame;
    }
}
