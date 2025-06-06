package com.dosgo.castx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.util.Random;

import castX.CastX;

// ScreenCastService.java
public class ScreenCastService extends Service {

    private static final int NOTIFICATION_ID = 10086;
    private static final String CHANNEL_ID = "screen_cast_channel";
    // 编码参数
    private static final int FRAME_RATE = 40;
    private VideoEncoder videoEncoder;
    private AudioEncoder audioEncoder;
    private MediaProjection mediaProjection;
    private static ScreenCastService instance;
    private    String mimeType="";
    private    String webRtcMimeType="";
    public static final String ACTION_UPDATE = "ScreenCastService.UPDATE_STATUS";
    public static ScreenCastService getInstance(){
        return instance;
    }
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this; // 在服务创建时赋值
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());

        if(isDecoderSupported(MediaFormat.MIMETYPE_VIDEO_VP9)){
         //  mimeType=MediaFormat.MIMETYPE_VIDEO_VP9;
          // webRtcMimeType="video/VP9";
        }

        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        display.getRealMetrics(metrics);

        if (intent != null && ! Status.isRunning) {
            int resultCode = intent.getIntExtra("EXTRA_RESULT_CODE", -1);
            Intent resultData = intent.getParcelableExtra("EXTRA_RESULT_INTENT");
            if (resultCode == -1 && resultData != null) {
                CastX.shutdown();
                SharedPreferences prefs = getSharedPreferences("config", Context.MODE_PRIVATE);
                String savedPassword = prefs.getString("password", "");
                CastX.start(8081,metrics.widthPixels,metrics.heightPixels,webRtcMimeType,savedPassword);
                System.out.println("start ok1111111111");
                startRecording(resultCode, resultData);
                Status.isRunning = true;
                sendStatusUpdate();
            }
        }
        return START_STICKY;
    }

    private void startRecording(int resultCode, Intent resultData) {
        try {

            MediaProjectionManager manager =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);


            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            display.getRealMetrics(metrics);

            mediaProjection = manager.getMediaProjection(resultCode, resultData);

            System.out.println("widthPixels:"+metrics.widthPixels);

            // 初始化视频编码器
            videoEncoder = new VideoEncoder(mediaProjection,metrics.widthPixels, metrics.heightPixels, FRAME_RATE,mimeType);

            // 初始化音频编码器
            audioEncoder = new AudioEncoder(ScreenCastService.this,mediaProjection,64000);


            Control.setContext(this);
        } catch (Exception e) {
            e.printStackTrace();
            stopSelf();
        }
    }

    public void setMinimumBrightness() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.System.canWrite(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                startActivity(intent);
            }
        }
        System.out.println("setMinimumBrightness");

        Settings.System.putInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);


        // 亮度设为最小值（通常为0）
        Settings.System.putInt(
                getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                1
        );
    }

    public void restoreBrightness() {
        Settings.System.putInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
    }

    public void startDecoding() {
        // 启动录制
        videoEncoder.start();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            audioEncoder.start();
        }
    }
    public void stopDecoding() {
        // 启动录制
        videoEncoder.stop();
        audioEncoder.stop();
        if (Settings.System.canWrite(this)) {
            restoreBrightness();
        }
    }

    public void setMaxSize(int maxSize){
        if(videoEncoder!=null){
            videoEncoder.setMaxSize(maxSize);
        }
    }
    private void releaseRecording() {
        CastX.shutdown();
        if (videoEncoder != null) {
            videoEncoder.release();
            videoEncoder = null;
        }
        if (audioEncoder != null) {
            audioEncoder.stop();
            audioEncoder = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        Status.isRunning = false;
        stopSelf();
    }

    @Override
    public void onDestroy() {
        releaseRecording();
        if (Settings.System.canWrite(this)) {
            restoreBrightness();
        }
        Control.unSetContext();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("屏幕录制中")
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "录屏服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
    }



    public static boolean isDecoderSupported(String mimetype) {
        // 遍历所有编解码器
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (MediaCodecInfo codecInfo : codecList.getCodecInfos()) {
            // 只检查编码器
            if (codecInfo.isEncoder()) {
                for (String mimeType : codecInfo.getSupportedTypes()) {
                    if (mimeType.equalsIgnoreCase(mimetype)) {
                        // 进一步检查颜色格式支持
                        MediaCodecInfo.CodecCapabilities caps = codecInfo.getCapabilitiesForType(mimeType);
                        for (int colorFormat : caps.colorFormats) {
                            if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return  false;
    }


    private void sendStatusUpdate() {
        Intent intent = new Intent(ACTION_UPDATE);
        // 设置Intent的包名，这样广播只会发送到我们自己的应用
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }
}