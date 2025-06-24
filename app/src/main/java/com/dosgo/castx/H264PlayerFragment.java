package com.dosgo.castx;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import castX.CastX;


import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class H264PlayerFragment extends Fragment {
    private MediaCodec mediaCodec;

    private Thread decoderThread,decoderOutThread, receiveTrread;
    private boolean isRunning = false;
    private boolean isFullscreen = false;

    private MediaFormat format;

    private SurfaceView miniSurface, fullscreenSurface;
    private View miniContainer, fullscreenContainer;

    private EditText urlEt,et_password;

    private  LinearLayout   passwordve;

    private  boolean isScrcpy;
    BlockingQueue<H264Frame> frameQueue = new LinkedBlockingQueue<>(500);
    byte[] sps ={}; // SPS 数据
     byte[] pps = {}; // PPS 数据

    private ScrcpyReceive scrcpyReceive;


    @Override
    public View onCreateView( LayoutInflater inflater,
                              ViewGroup container,
                              Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_player, container, false);
    }

    @Override
    public void onViewCreated( View view,  Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        // 初始化视图
        miniSurface = view.findViewById(R.id.surface_view);
        fullscreenSurface = view.findViewById(R.id.fullscreen_surface);
        miniContainer = view.findViewById(R.id.mini_player_container);
        fullscreenContainer = view.findViewById(R.id.fullscreen_container);

        // 设置全屏/小窗切换按钮
        view.findViewById(R.id.btn_expand).setOnClickListener(v -> enterFullscreen());
        view.findViewById(R.id.btn_shrink).setOnClickListener(v -> exitFullscreen());
        view.findViewById(R.id.play).setOnClickListener(v->play());

        urlEt=view.findViewById(R.id.url);
        et_password=view.findViewById(R.id.et_password);
        passwordve=view.findViewById(R.id.passwordve);

        if(getArguments()!=null) {
            isScrcpy = getArguments().getBoolean("isScrcpy");
            if (isScrcpy) {
                String wsUrl = getArguments().getString("wsUrl");
                String password = getArguments().getString("password");
                urlEt.setText(wsUrl);
                urlEt.setEnabled(false);
                et_password.setText(password);
                passwordve.setVisibility(View.GONE);
            } else {
                urlEt.setEnabled(true);
                passwordve.setVisibility(View.VISIBLE);
            }
        }
    }



    private void openView() {
        SharedPreferences prefs = getContext().getSharedPreferences("config", Context.MODE_PRIVATE);
        String password = prefs.getString("password", "");

        Intent intent = new Intent(getContext(), H264PlayerFragment.class);
        intent.putExtra("wsUrl", "ws://127.0.0.1:8082/ws");
        intent.putExtra("isScrcpy", true);
        intent.putExtra("password", password);
        startActivity(intent);
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

        startDecoding();
    }

    private void startReceive(int port ){
        isRunning=true;
        receiveTrread = new Thread(() -> {
            try {
                scrcpyReceive=new ScrcpyReceive( port,1);
                InputStream inputStream=scrcpyReceive.getInputStream();
                DataInputStream dataInputStream=new DataInputStream(inputStream);

                var headerArray = new byte[12]; // 8字节(long) + 4字节(int)
                var dataArray = new byte[1024*1024*5]; // 5M
                boolean one=true;
                while (isRunning) {
                    dataInputStream.readFully(headerArray);
                    long pts= ByteBuffer.wrap(headerArray, 0, 8)
                            .order(ByteOrder.BIG_ENDIAN)
                            .getLong();

                    int len= ByteBuffer.wrap(headerArray, 8, 4)
                            .order(ByteOrder.BIG_ENDIAN)
                            .getInt();

                    dataInputStream.readFully(dataArray,0,  len);

                    int nalType = dataArray[4] & 0x1F;
                    if(nalType==7){
                        System.out.println("revice sps len:"+len);
                        sps=Arrays.copyOfRange(dataArray, 0, len);
                        System.out.println("revice sps len111:"+sps.length);
                    }else if(nalType==8){

                        pps=Arrays.copyOfRange(dataArray, 0, len);
                        System.out.println("revice pps len:"+len);
                        if(one) {
                            initMediaCodec(miniSurface.getHolder().getSurface());
                            one=false;
                        }
                    }else {
                        boolean isKeyFrame = nalType == 5?true:false;
                        if(isKeyFrame ){
                            System.out.println("revice isKeyFrame\r\n");
                        }
                        frameQueue.put(new H264Frame(dataArray, len, pts, isKeyFrame));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        receiveTrread.start();
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
                    break;
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
                    break;
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
        String url= String.valueOf(urlEt.getText());
        String password =String.valueOf(et_password.getText());
        System.out.println("play:"+ password);
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getActivity().getSystemService(Activity.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        display.getRealMetrics(metrics);
        long port=CastX.startCastXClient(url,password, metrics.widthPixels>metrics.heightPixels?metrics.widthPixels:metrics.heightPixels);
        if(port<1){
            Toast.makeText(getContext(), "connect err", Toast.LENGTH_SHORT).show();

            return ;
        }
        startReceive((int) port);
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
        getActivity().getWindow().getDecorView().setSystemUiVisibility(
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
        getActivity().getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private void resetMediaCodecSurface(Surface surface) {
        // 暂停解码
        mediaCodec.stop();

        // 重新配置到新Surface
        mediaCodec.configure(format, surface, null, 0);
        mediaCodec.start();

    }



    // 处理返回键（全屏时先退出全屏）
   // @Override
    public void onBackPressed() {
        if (isFullscreen) {
            exitFullscreen();
        } else {
           // super.onBackPressed();
        }
    }
}