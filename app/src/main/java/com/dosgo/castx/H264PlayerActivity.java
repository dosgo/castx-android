package com.dosgo.castx;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import castX.CastX;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class H264PlayerActivity extends Activity   {
    private MediaCodec mediaCodec;

    private Thread decoderThread,decoderOutThread;
    private boolean isRunning = false;
    private boolean isFullscreen = false;

    private MediaFormat format;

    private SurfaceView miniSurface, fullscreenSurface;
    private View miniContainer, fullscreenContainer;

    private EditText urlEt;
    BlockingQueue<H264Frame> frameQueue = new LinkedBlockingQueue<>(500);
    byte[] sps ={}; // SPS 数据
     byte[] pps = {}; // PPS 数据

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // 初始化视图
        miniSurface = findViewById(R.id.surface_view);
        fullscreenSurface = findViewById(R.id.fullscreen_surface);
        miniContainer = findViewById(R.id.mini_player_container);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        // 设置全屏/小窗切换按钮
        findViewById(R.id.btn_expand).setOnClickListener(v -> enterFullscreen());
        findViewById(R.id.btn_shrink).setOnClickListener(v -> exitFullscreen());
        findViewById(R.id.play).setOnClickListener(v->play());

        urlEt=findViewById(R.id.url);

    }




    // 初始化 MediaCodec 解码器
    private void initMediaCodec(Surface surface) throws IOException {
        // 1. 创建 H264 解码器
        mediaCodec = MediaCodec.createDecoderByType("video/avc");

        // 2. 配置 MediaFormat (需要 SPS 和 PPS)
        format = MediaFormat.createVideoFormat("video/avc", 1280, 720); // 分辨率需与实际流匹配

        // 3. 设置关键参数（从流的头部提取或硬编码）
        format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline);
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps));

        // 4. 绑定 Surface 并启动解码器
        mediaCodec.configure(format, surface, null, 0);
        mediaCodec.start();
    }

    // 启动解码线程
    private void startDecoding() {
        System.out.println("startDecoding\r\n");
        isRunning = true;
        decoderThread = new Thread(() -> {

            while (isRunning) {
                try {



                    // 1. 获取输入缓冲区
                    int inputBufferIndex = mediaCodec.dequeueInputBuffer(10000);
                    if (inputBufferIndex >= 0) {
                        H264Frame frame = frameQueue.take();
                        ByteBuffer codecBuffer = mediaCodec.getInputBuffer(inputBufferIndex);

                        codecBuffer.put(frame.data);

                        // 3. 提交给解码器
                        mediaCodec.queueInputBuffer(
                            inputBufferIndex, 
                            0,
                                frame.data.length,
                                frame.timestamp, // 时间戳（微秒）
                                frame.isKeyFrame ? MediaCodec.BUFFER_FLAG_KEY_FRAME : 0
                        );


                    }else{
                        System.out.println("inputBufferIndex-1");
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        decoderThread.start();

        decoderOutThread = new Thread(() -> {

            while (isRunning) {
                try {
                    // 4. 处理解码后的输出
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                    int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);

                    if (outputBufferIndex >= 0) {
                        // 5. 渲染到 Surface（自动完成）
                        mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        decoderOutThread.start();
    }

    // 停止解码
    private void stopDecoding() {
        isRunning = false;
        try {
            decoderThread.join();
            decoderOutThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mediaCodec.stop();
        mediaCodec.release();
    }


    private void play() {
        Control.setActivity(this);
        String url= String.valueOf(urlEt.getText());
        System.out.println("play:"+url);
        CastX.startWebRtcReceive(url+"/sendOffer");
    }



    private void enterFullscreen() {
        isFullscreen = true;

        // 1. 隐藏小窗
        miniContainer.setVisibility(View.GONE);

        // 2. 显示全屏容器
        fullscreenContainer.setVisibility(View.VISIBLE);

        // 3. 重新绑定MediaCodec到全屏Surface
        resetMediaCodecSurface(fullscreenSurface.getHolder().getSurface());

        // 4. 隐藏状态栏和导航栏
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void exitFullscreen() {
        isFullscreen = false;

        // 1. 显示小窗
        miniContainer.setVisibility(View.VISIBLE);

        // 2. 隐藏全屏容器
        fullscreenContainer.setVisibility(View.GONE);

        // 3. 重新绑定MediaCodec到小窗Surface
        resetMediaCodecSurface(miniSurface.getHolder().getSurface());

        // 4. 恢复系统UI
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void resetMediaCodecSurface(Surface surface) {
        // 暂停解码
        mediaCodec.stop();

        // 重新配置到新Surface
        mediaCodec.configure(format, surface, null, 0);
        mediaCodec.start();

    }
    public static boolean isKeyFrame(byte[] nalu) {
        if (nalu == null || nalu.length == 0) {
            return false;
        }
        // 获取 NAL 单元类型（首字节低5位）
        int nalType = nalu[0] & 0x1F;
        return nalType == 5; // 5=IDR帧
    }
    public void callBytes(long cmd, byte[] data,long timestamp)  {
        switch ((int) cmd){
            case 1://data
                try {
                    boolean isKeyFrame=isKeyFrame(data);
                    frameQueue.put(new H264Frame(data,  timestamp, isKeyFrame));
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                break;
            case 2://SPS
                sps=data;
                break;
            case 3://PPS
                pps=data;
                break;
        }
        if(pps.length>0&&sps.length>0&&!isRunning){
            try {
                initMediaCodec(miniSurface.getHolder().getSurface());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            startDecoding();
        }
    }

    // 处理返回键（全屏时先退出全屏）
    @Override
    public void onBackPressed() {
        if (isFullscreen) {
            exitFullscreen();
        } else {
            super.onBackPressed();
        }
    }
}