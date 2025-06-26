package com.dosgo.castx;

import android.app.Activity;

import android.os.Bundle;

import android.view.Surface;
import android.view.SurfaceHolder;

import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;

import org.webrtc.*;
import castX.CastX;

public class WebrtcPlayerActivity extends Activity {


    private boolean isRunning = false;
    private boolean isFullscreen = false;


    public SurfaceViewRenderer remoteVideoView ;
    private View miniContainer, fullscreenContainer;

    private EditText urlEt,et_password;

    private  LinearLayout   passwordve;

    private Button play;

    private  boolean isScrcpy;

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private SurfaceViewRenderer videoRenderer;
    private EglBase eglBase;




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webrtcPlayer);

        miniContainer = findViewById(R.id.mini_player_container);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        remoteVideoView= findViewById(R.id.remote_video_view);
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



    private void initializeWebRTC() {
        // 创建EGL上下文
        eglBase = EglBase.create();
        videoRenderer.init(eglBase.getEglBaseContext(), null);

        // 初始化PeerConnectionFactory
        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);

        factory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        // 3. 创建PeerConnection
        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(
                new ArrayList<>() // 不需要ICE服务器
        );
        configuration.bundlePolicy = PeerConnection.BundlePolicy.MAX_BUNDLE;
        configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;

        peerConnection = factory.createPeerConnection(configuration, new PeerConnection.Observer() {
            @Override
            public void onAddStream(MediaStream stream) {
                // 4. 当有音视频流到达时自动播放
                runOnUiThread(() -> {
                    // 播放视频
                    if (!stream.videoTracks.isEmpty()) {
                        VideoTrack videoTrack = stream.videoTracks.get(0);
                        videoTrack.addSink(videoRenderer);
                        Log.d("WebRTCPlayer", "开始播放视频");
                    }

                    // 播放音频
                    if (!stream.audioTracks.isEmpty()) {
                        AudioTrack audioTrack = stream.audioTracks.get(0);
                        audioTrack.setEnabled(true);
                        Log.d("WebRTCPlayer", "开始播放音频");
                    }
                });
            }

            // 其他必要的回调（可以为空）
            @Override public void onIceCandidate(IceCandidate iceCandidate) {}
            @Override public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}
            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {}
            @Override public void onRemoveStream(MediaStream mediaStream) {}
            @Override public void onDataChannel(DataChannel dataChannel) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}
        });

        // 5. 设置远程媒体流描述
        setRemoteDescription();
    }
    /**
     * 在实际应用中，您需要通过网络从对等端接收ICE候选信息
     */
    public void onRemoteIceCandidateReceived(String candidate, int sdpMLineIndex, String sdpMid) {
        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
        peerConnection.addIceCandidate(iceCandidate);
    }

    private void setRemoteDescription() {
        // 6. 接收到的远程SDP字符串
        String remoteSdp = getRemoteSdp();

        // 7. 创建远程会话描述
        SessionDescription remoteDesc = new SessionDescription(
                SessionDescription.Type.ANSWER, // 或者OFFER，取决于角色
                remoteSdp
        );

        // 8. 设置远程描述
        peerConnection.setRemoteDescription(new SimpleSdpObserver() {
            @Override
            public void onSetSuccess() {
                Log.d("WebRTCPlayer", "远程描述设置成功，准备接收流");
            }

            @Override
            public void onSetFailure(String error) {
                Log.e("WebRTCPlayer", "设置远程描述失败: " + error);
            }
        }, remoteDesc);
    }
    private void play() {

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

    private void resetMediaCodecSurface(Surface surface) {
        mediaCodec.setOutputSurface(surface);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStartUI();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 释放资源
        if (peerConnection != null) {
            peerConnection.dispose();
        }
        if (videoRenderer != null) {
            videoRenderer.release();
        }
        if (eglBase != null) {
            eglBase.release();
        }
        if (factory != null) {
            factory.dispose();
        }
    }

}