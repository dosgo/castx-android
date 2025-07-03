package com.dosgo.castx;

import android.app.Activity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;


import castX.CastX;

public class RtspPlayerActivity extends Activity {


    private boolean isRunning = false;
    private boolean isFullscreen = false;



    private View miniContainer, fullscreenContainer;

    private EditText urlEt,et_password;

    private  LinearLayout   passwordve;

    private Button play;

    private  boolean isScrcpy;


    private PlayerView  playerView;





    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_rtsp_player);

        miniContainer = findViewById(R.id.mini_player_container);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        playerView= findViewById(R.id.remote_video_view);
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







    private void play() {
        String url= String.valueOf(urlEt.getText());
        String password =String.valueOf(et_password.getText());
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) getSystemService(Activity.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        display.getRealMetrics(metrics);
        int maxSize=metrics.widthPixels>metrics.heightPixels?metrics.widthPixels:metrics.heightPixels;
        System.out.println("play maxSize:"+maxSize);
        CastX.startCastXClient(url,password,maxSize,true);
        ExoPlayer player = new ExoPlayer.Builder(this).build();


        playerView.setPlayer(player);

        String rtspUrl = "rtsp://127.0.0.1:8554/";
        player.setMediaItem(MediaItem.fromUri(rtspUrl));
        player.prepare();
        player.play();

    }


    private void enterFullscreen() {
        isFullscreen = true;

        // 1. 隐藏小窗
        miniContainer.setVisibility(View.GONE);

        // 2. 显示全屏容器
        fullscreenContainer.setVisibility(View.VISIBLE);



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
        // 4. 恢复系统UI
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }


    @Override
    protected void onResume() {
        super.onResume();
        updateStartUI();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}