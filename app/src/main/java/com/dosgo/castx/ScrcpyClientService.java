package com.dosgo.castx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import java.io.File;

import castX.CastX;

// ScreenCastService.java
public class ScrcpyClientService extends Service {

    private static final int NOTIFICATION_ID = 10087;
    private static final String CHANNEL_ID = "scrcpy_channel";
    //


    @Override
    public void onCreate() {
        super.onCreate();
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        }else {
            startForeground(
                    NOTIFICATION_ID,
                    buildNotification());
        }
        String androidId = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ANDROID_ID
        );
        CastX.shutdownScrcpyClient();
        String dirPath = getFilesDir().getAbsolutePath();
        SharedPreferences prefs = getSharedPreferences("config", Context.MODE_PRIVATE);
        String savedPassword = prefs.getString("password", "");
        CastX.startScrcpyClient(8082,"castX-"+androidId, dirPath + File.separator,savedPassword);
        Status.scrcpyIsRunning=true;
        return START_STICKY;
    }








    @Override
    public void onDestroy() {
        CastX.shutdownScrcpyClient();
        super.onDestroy();
        Status.scrcpyIsRunning=false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("scrcpyClient run...")
                .setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "scrcpy",
                    NotificationManager.IMPORTANCE_LOW
            );
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE))
                    .createNotificationChannel(channel);
        }
    }




}