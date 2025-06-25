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
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.json.JSONObject;

import castX.CastX;


import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class H264PlayerActivity extends Activity {
    private MediaCodec mediaCodec;

    private Thread decoderThread,decoderOutThread, receiveTrread;
    private boolean isRunning = false;
    private boolean isFullscreen = false;

    private MediaFormat format;

    private SurfaceView miniSurface, fullscreenSurface;
    private View miniContainer, fullscreenContainer;

    private EditText urlEt,et_password;

    private  LinearLayout   passwordve;

    private Button play;

    private  boolean isScrcpy;

    private  boolean fullSurfaceCreated;
    BlockingQueue<H264Frame> frameQueue = new LinkedBlockingQueue<>(500);
    byte[] sps ={}; // SPS 数据
    byte[] pps = {}; // PPS 数据

    boolean upSps=true;

    private ScrcpyReceive scrcpyReceive;

    private  boolean initMediaCodec=false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // 初始化视图
        miniSurface = findViewById(R.id.surface_view);
        fullscreenSurface = findViewById(R.id.fullscreen_surface);
        miniContainer = findViewById(R.id.mini_player_container);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        fullscreenSurface.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                // Surface创建时初始化摄像头
                resetMediaCodecSurface( holder.getSurface());
                fullSurfaceCreated=true;
                System.out.println("enterFullscreen resetMediaCodecSurface");
            }
            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }
            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

        // 设置全屏/小窗切换按钮
        findViewById(R.id.btn_expand).setOnClickListener(v -> enterFullscreen());
        findViewById(R.id.btn_shrink).setOnClickListener(v -> exitFullscreen());

        play=findViewById(R.id.play);
        play.setOnClickListener(v -> {
                    if (!isRunning) {
                        play();
                        play.setText("停止");
                    } else {
                        CastX.shutdownCastXClient();
                        stopDecoding();
                        play.setText("接收");
                    }
                }
        );

        urlEt=findViewById(R.id.url);
        et_password=findViewById(R.id.et_password);
        passwordve=findViewById(R.id.passwordve);


            isScrcpy = getIntent().getBooleanExtra("isScrcpy",false);
            if (isScrcpy) {
                String wsUrl = getIntent().getStringExtra("wsUrl");
                String password = getIntent().getStringExtra("password");
                urlEt.setText(wsUrl);
                urlEt.setEnabled(false);
                et_password.setText(password);
                passwordve.setVisibility(View.GONE);
            } else {
                urlEt.setEnabled(true);
                passwordve.setVisibility(View.VISIBLE);
            }

    }


    private void updateStartUI(){
        if (play!=null){
            play.setText(isRunning? "停止":"接收");
        }
    }


    // 初始化 MediaCodec 解码器
    private void initMediaCodec(Surface surface) throws IOException {
        if(sps==null){
            return ;
        }
        try {
            String json = CastX.parseH264SPS(sps,1);

            JSONObject jsonObject = new JSONObject(json);
            System.out.println("json:" + json);
            int width = jsonObject.getInt("Width");
            int height = jsonObject.getInt("Height");
            int profile = jsonObject.getInt("Profile");
            int constraintSetFlags = jsonObject.getInt("ConstraintSetFlags");
            System.out.println("profile:" + profile);
            // 1. 创建 H264 解码器
            mediaCodec = MediaCodec.createDecoderByType("video/avc");
            // 2. 配置 MediaFormat (需要 SPS 和 PPS)
            format = MediaFormat.createVideoFormat("video/avc", width, height); // 分辨率需与实际流匹配
            // 3. 设置关键参数（从流的头部提取或硬编码）
            if(profile==66) {
                if ( (constraintSetFlags & 0x40) != 0) {
                    format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline);
                }else{
                    format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline);

                }
            }
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel4);

            format.setByteBuffer("csd-0", ByteBuffer.wrap(sps));
            format.setByteBuffer("csd-1", ByteBuffer.wrap(pps));

            // 4. 绑定 Surface 并启动解码器
            mediaCodec.configure(format, surface, null, 0);
            mediaCodec.start();
            startDecoding();
            initMediaCodec=true;
        }catch (Exception e){
            e.printStackTrace();
        }
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
                while (isRunning) {
                    dataInputStream.readFully(headerArray);
                    long ptsAndFlags= ByteBuffer.wrap(headerArray, 0, 8)
                            .order(ByteOrder.BIG_ENDIAN)
                            .getLong();



                    long pts= ptsAndFlags &  0x3FFFFFFFFFFFFFFFL;
                    boolean isKeyFrame_= (ptsAndFlags & 0x4000000000000000L) != 0;
                    boolean isConfig=(ptsAndFlags & 0x8000000000000000L) != 0;
                    int len= ByteBuffer.wrap(headerArray, 8, 4)
                            .order(ByteOrder.BIG_ENDIAN)
                            .getInt();


                    dataInputStream.readFully(dataArray,0,  len);

                    int nalType = dataArray[0] & 0x1F;
                    if(nalType==7){
                        if(upSps==false) {
                            upSps = !compareFirstNBytes(sps, dataArray, sps.length);

                            if(upSps){
                                System.out.println("upSps:"+upSps);
                            }
                        }
                        sps=Arrays.copyOfRange(dataArray, 0, len);
                        System.out.println("sps:"+sps);
                       
                    }else if(nalType==8){
                        pps=Arrays.copyOfRange(dataArray, 0, len);
                        if(upSps) {
                            initMediaCodec(miniSurface.getHolder().getSurface());
                            upSps=false;
                        }
                    }else {
                        boolean isKeyFrame =nalType == 5?true:false;
                        if(initMediaCodec) {
                            frameQueue.put(new H264Frame(dataArray, len, pts, isKeyFrame));
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        receiveTrread.start();
    }
    public static boolean compareFirstNBytes(byte[] a, byte[] b, int n) {
        if (a == b) return true; // 同一个数组
        if (a == null || b == null) return false; // 有一个为null

        // 如果n大于数组长度，则取数组长度
        int len = Math.min(n, Math.min(a.length, b.length));
        for (int i = 0; i < len; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
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
                       // System.out.println("inputBufferIndex-1");
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
                    int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 10000);

                    if (outputBufferIndex >= 0) {
                        // 5. 渲染到 Surface（自动完成）
                        mediaCodec.releaseOutputBuffer(outputBufferIndex, true);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                     System.out.println("outputBufferIndex error");
                    break;
                }
            }
        });
        decoderOutThread.start();
    }

    // 停止解码
    private void stopDecoding() {
        isRunning = false;
        if(decoderThread!=null) {
            try {
                decoderThread.join();
                decoderOutThread.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if(mediaCodec!=null) {
            mediaCodec.stop();
            mediaCodec.release();
            mediaCodec=null;
        }
    }


    private void play() {
        String url= String.valueOf(urlEt.getText());
        String password =String.valueOf(et_password.getText());
        System.out.println("play ps:"+ password+"url:"+url);
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(Activity.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        display.getRealMetrics(metrics);
        int maxSize=metrics.widthPixels>metrics.heightPixels?metrics.widthPixels:metrics.heightPixels;
        System.out.println("play maxSize:"+maxSize);
        long port=CastX.startCastXClient(url,password,maxSize);
        if(port<1){
            Toast.makeText(this, "connect err", Toast.LENGTH_SHORT).show();
            return ;
        }
        System.out.println("play port:"+port);
        startReceive((int) port);
    }



    private void enterFullscreen() {
        isFullscreen = true;

        // 1. 隐藏小窗
        miniContainer.setVisibility(View.GONE);

        // 2. 显示全屏容器
        fullscreenContainer.setVisibility(View.VISIBLE);


        if(fullSurfaceCreated){
            resetMediaCodecSurface(     fullscreenSurface.getHolder().getSurface());
        }

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
        mediaCodec.setOutputSurface(surface);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStartUI();
        if(isRunning) {
            try {
                initMediaCodec(miniSurface.getHolder().getSurface());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
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