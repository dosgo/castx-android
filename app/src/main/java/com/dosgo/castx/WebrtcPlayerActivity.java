package com.dosgo.castx;

import android.app.Activity;

import android.os.Bundle;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;

import android.view.View;

import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.ArrayList;

import org.json.JSONObject;
import org.webrtc.*;
import castX.CastX;

public class WebrtcPlayerActivity extends Activity {


    private boolean isRunning = false;
    private boolean isFullscreen = false;



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
        setContentView(R.layout.activity_webrtc_player);

        miniContainer = findViewById(R.id.mini_player_container);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        videoRenderer= findViewById(R.id.remote_video_view);
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
        Control.setActivity(this);
    }


    private void updateStartUI(){
        if (play!=null){
            play.setText(isRunning? "停止":"接收");
        }
    }



    private void initializeWebRTC() {
        // 创建EGL上下文
        eglBase = EglBase.create();
        runOnUiThread(() -> {
            try {
                videoRenderer.init(eglBase.getEglBaseContext(), null);
                Log.d("WebRTC", "SurfaceViewRenderer 初始化成功");
            } catch (Exception e) {
                Log.e("WebRTC", "初始化失败", e);
            }
        });

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



        configuration.bundlePolicy = PeerConnection.BundlePolicy.MAXCOMPAT; // 关键！
     //   configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
      //  configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

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

            @Override
            public void onIceCandidateError(IceCandidateErrorEvent event) {
                PeerConnection.Observer.super.onIceCandidateError(event);
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

            }

            @Override
            public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
                PeerConnection.Observer.super.onSelectedCandidatePairChanged(event);
            }

            @Override public void onSignalingChange(PeerConnection.SignalingState signalingState) {}
            @Override public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {}

            @Override
            public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {
                PeerConnection.Observer.super.onStandardizedIceConnectionChange(newState);
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
                PeerConnection.Observer.super.onConnectionChange(newState);
            }

            @Override public void onIceConnectionReceivingChange(boolean b) {}
            @Override public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

                if (iceGatheringState == PeerConnection.IceGatheringState.GATHERING) {
                    Log.d("webrtc", "开始收集 ICE 候选");
                } else if (iceGatheringState == PeerConnection.IceGatheringState.COMPLETE) {

                    try {
                      SessionDescription  offer= peerConnection.getLocalDescription();
                        JSONObject json = new JSONObject();
                        json.put("type", offer.type.canonicalForm());
                        json.put("sdp", offer.description);

                        CastX.castXClientSendOffer(json.toString());
                    } catch (Exception e){
                        e.printStackTrace();

                    }
                }
            }
            @Override public void onRemoveStream(MediaStream mediaStream) {}
            @Override public void onDataChannel(DataChannel dataChannel) {}
            @Override public void onRenegotiationNeeded() {}
            @Override public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {}

            @Override
            public void onRemoveTrack(RtpReceiver receiver) {
                PeerConnection.Observer.super.onRemoveTrack(receiver);
            }

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                PeerConnection.Observer.super.onTrack(transceiver);
            }
        });

        RtpTransceiver videoTransceiver = peerConnection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO
        );
      //  videoTransceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);

        // 添加音频接收器
        RtpTransceiver audioTransceiver = peerConnection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO
        );
        //audioTransceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);


        // 1. 开始收集 ICE 候选 (自动触发)
        peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription offer) {
                // 2. 创建 Offer 成功

                // 3. 设置本地描述
                peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onSetSuccess() {

                    }
                    @Override
                    public void onSetFailure(String error) {
                        Log.e("WebRTC", "设置本地描述失败: " + error);
                    }

                    // 其他回调
                    @Override public void onCreateSuccess(SessionDescription sessionDescription) {}
                    @Override public void onCreateFailure(String s) {}
                }, offer);
            }

            @Override
            public void onCreateFailure(String error) {
                Log.e("WebRTC", "创建 Offer 失败: " + error);
            }

            // 其他回调
            @Override public void onSetSuccess() {}
            @Override public void onSetFailure(String s) {}
        }, new MediaConstraints());
    }
    /**
     * 在实际应用中，您需要通过网络从对等端接收ICE候选信息
     */
    public void onRemoteIceCandidateReceived(String candidate, int sdpMLineIndex, String sdpMid) {
        IceCandidate iceCandidate = new IceCandidate(sdpMid, sdpMLineIndex, candidate);
        peerConnection.addIceCandidate(iceCandidate);
    }


    public void loginCall(String data){
        System.out.println("loginCall data:"+data);
        try {
            JSONObject json = new JSONObject(data);
            boolean auth = json.getBoolean("auth");
            if(auth){
                initializeWebRTC();
            }else{
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(WebrtcPlayerActivity.this, "login err", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void offerRespCall(String data){
        System.out.println("offerRespCall data:"+data);
        // 5. 设置远程媒体流描述
        setRemoteDescription(data);
    }

    private void setRemoteDescription(String remoteSdp ) {

        // 7. 创建远程会话描述
        SessionDescription remoteDesc = new SessionDescription(
                SessionDescription.Type.ANSWER, // 或者OFFER，取决于角色
                remoteSdp
        );

        // 8. 设置远程描述
        peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onSetSuccess() {
                Log.d("WebRTCPlayer", "远程描述设置成功，准备接收流");
                // 这里可以添加后续逻辑，例如：创建Answer等
            }

            @Override
            public void onSetFailure(String error) {
                Log.e("WebRTCPlayer", "设置远程描述失败: " + error);
            }

            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                // 创建SDP成功时调用（如createOffer/createAnswer），本例中不需要实现
            }

            @Override
            public void onCreateFailure(String error) {
                // 创建SDP失败时调用，本例中不需要实现
            }
        }, remoteDesc);
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
        CastX.startCastXClient(url,password,maxSize,false);

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