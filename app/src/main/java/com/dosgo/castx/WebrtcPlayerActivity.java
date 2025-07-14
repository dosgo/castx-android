package com.dosgo.castx;

import android.app.Activity;

import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Rational;
import android.view.Display;
import android.view.MotionEvent;


import android.view.View;

import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;


import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.*;

public class WebrtcPlayerActivity extends AppCompatActivity {


    public boolean isRunning = false;
    private boolean isFullscreen = false;

    private View miniContainer, fullscreenContainer;

    private EditText urlEt,et_password;



    private  LinearLayout   passwordve;

    private ImageButton play,volume,tv,ptp;


    private  boolean isScrcpy;

    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;
    private SurfaceViewRenderer videoRenderer,fullRenderer;
    private EglBase eglBase;
    private  VideoTrack videoTrack;

    private  String GOOS="android";

    private  boolean sound=true;
    private  boolean displayPower=true;

    private boolean isConnect=false;

    private  boolean adbConnect;
    AudioTrack audioTrack;

    private AdbConnectFragment adbConnectFragment;

    private  int videoHeight = 0;
    private int videoWidth  =0;
    private RadioGroup tabTop;
    private WsClient wsClient;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webrtc_player);

        miniContainer = findViewById(R.id.mini_player_container);
        fullscreenContainer = findViewById(R.id.fullscreen_container);

        videoRenderer= findViewById(R.id.remote_video_view);
        videoRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        videoRenderer.setEnableHardwareScaler(true);
        videoRenderer.setOnTouchListener(touchHandler);


        fullRenderer=findViewById(R.id.fullscreen_surface);
        fullRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        fullRenderer.setEnableHardwareScaler(true);
        fullRenderer.setOnTouchListener(touchHandler);
        // 设置全屏/小窗切换按钮
        findViewById(R.id.btn_expand).setOnClickListener(v -> enterFullscreen());
        findViewById(R.id.btn_shrink).setOnClickListener(v -> exitFullscreen());
        adbConnectFragment = (AdbConnectFragment) getSupportFragmentManager().findFragmentById(R.id.adb_connect);

        findViewById(R.id.home).setOnClickListener(v -> {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "keyboard");
                json.put("code", "home");
                sendControl(json.toString());
            }catch (Exception e){
                e.printStackTrace();
            }
        });
        findViewById(R.id.back).setOnClickListener(v -> {
            try {
                JSONObject json = new JSONObject();
                json.put("type", "keyboard");
                json.put("code", "back");
                sendControl(json.toString());
            }catch (Exception e){
                e.printStackTrace();
            }
        });


        volume= findViewById(R.id.volume);
        volume.setOnClickListener(v -> {
            try {
                if(audioTrack!=null){
                    if(  audioTrack.enabled()){
                        audioTrack.setEnabled(false);
                        sound=false;
                    }else {
                        audioTrack.setEnabled(true);
                        sound=true;
                    }
                    updateStartUI();
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        });

       tv= findViewById(R.id.tv);
       tv.setOnClickListener(v->{
            displayPower=!displayPower;
            try {
                JSONObject json = new JSONObject();
                json.put("type", "displayPower");
                json.put("action", displayPower ? 1 : 0);
                sendControl(json.toString());
            }catch (Exception e){
                e.printStackTrace();
            }
            updateStartUI();
        });
        ptp=findViewById(R.id.ptp);
        ptp.setOnClickListener(v->{
            enterPipMode();
            updateStartUI();
        });


        play=findViewById(R.id.play);
        play.setOnClickListener(v -> {
                    if(isScrcpy&&!Status.scrcpyIsRunning){
                        Toast.makeText(this, R.string.scrcpyStartMsg, Toast.LENGTH_SHORT).show();
                        return ;
                    }
                    if (!isRunning) {
                        new Thread(() -> play()).start();
                    } else {
                        if(wsClient!=null) {
                            wsClient.disconnect();
                        }
                        releaseWebRTCResources();
                        isRunning=false;
                        updateStartUI();
                    }
                }
        );

        urlEt=findViewById(R.id.url);

        urlEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 当文本改变后自动保存密码
                saveConf("url",s.toString());
            }
        });
        et_password=findViewById(R.id.et_password);

        et_password.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 当文本改变后自动保存密码
                saveConf("password",s.toString());
            }
        });
        passwordve=findViewById(R.id.passwordve);
        Control.setActivity(this);
        tabTop=findViewById(R.id.tab_top);
        tabTop.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                // checkedId 是被选中的 RadioButton 的 ID
                Intent intent = new Intent(WebrtcPlayerActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT );
                if (checkedId == R.id.castx_client) {
                   isScrcpy=false;
                } else if (checkedId == R.id.scrcpy_client) {
                    isScrcpy=true;
                }
                updateStartUI();
            }
        });

        findViewById(R.id.backMain).setOnClickListener(v -> {
            Intent intent = new Intent(this, MainActivity.class);
            intent.putExtra("selectTab",isScrcpy?2:1);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT );
            startActivity(intent);
        });

        processIntent();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void enterPipMode() {
        if (isInPictureInPictureMode()) return;

        PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .build();

        enterPictureInPictureMode(params);
    }
    private void updateStartUI(){
        loadConf();
        play.setImageResource(isRunning?R.drawable.stop_circle_24px:R.drawable.play_circle_24px);
        if(GOOS.equals("android")){
            findViewById(R.id.androidMenu).setVisibility(View.VISIBLE);
        }else{
            findViewById(R.id.androidMenu).setVisibility(View.GONE);
        }
        volume.setImageResource(sound?R.drawable.volume_up_24px:R.drawable.volume_off_24px);
        tv.setImageResource(displayPower?R.drawable.tv_off_24px:R.drawable.tv_24px);

        findViewById(R.id.menu).setVisibility(isInPictureInPictureMode()?View.GONE:View.VISIBLE);


        if (isScrcpy) {
            urlEt.setEnabled(false);
            et_password.setEnabled(false);
            isConnect=adbConnect?true:false;
            showAdbConnectFragment(!isConnect);
            if(urlEt.getText().length()<1) {
                urlEt.setText("http://127.0.0.1:8082/");
            }
        } else {
            urlEt.setEnabled(true);
            et_password.setEnabled(true);
            showAdbConnectFragment(false);
            isConnect=true;
        }
    }



    private void initializeWebRTC() {
        // 创建EGL上下文
        if(eglBase==null) {
            eglBase = EglBase.create();
            runOnUiThread(() -> {
                try {
                    videoRenderer.init(eglBase.getEglBaseContext(), null);
                    fullRenderer.init(eglBase.getEglBaseContext(), null);
                    Log.d("WebRTC", "SurfaceViewRenderer 初始化成功");
                } catch (Exception e) {
                    Log.e("WebRTC", "初始化失败", e);
                }
            });
        }

        // 初始化PeerConnectionFactory

        PeerConnectionFactory.InitializationOptions options =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .setEnableInternalTracer(true)
                        .setFieldTrials("WebRTC-Bwe-AlrLimitedBackoff/Enabled/") // 自适应带宽算法
                        .setFieldTrials("WebRTC-ZeroPlayoutDelay/Enabled/") // 关键：减少缓冲延迟
                        .setFieldTrials("WebRTC-LowLatencyRenderer/Enabled/")
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(options);


        DefaultVideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        PeerConnectionFactory.Options options1 = new PeerConnectionFactory.Options();
        options1.disableNetworkMonitor = true;


        factory = PeerConnectionFactory.builder().setOptions(options1).setVideoDecoderFactory(decoderFactory).createPeerConnectionFactory();

        // 3. 创建PeerConnection
        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(
                new ArrayList<>() // 不需要ICE服务器
        );
       // configuration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
      //  configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        configuration.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;


        peerConnection = factory.createPeerConnection(configuration, new PeerConnection.Observer() {
            @Override
            public void onAddStream(MediaStream stream) {
                // 4. 当有音视频流到达时自动播放
                runOnUiThread(() -> {
                    // 播放视频
                    if (!stream.videoTracks.isEmpty()) {
                        videoTrack = stream.videoTracks.get(0);
                        videoTrack.addSink(videoRenderer);
                        videoTrack.addSink(fullRenderer);


                        // 使用异步渲染（API 23+）
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            videoRenderer.setEnableHardwareScaler(true); // 异步缩放
                            fullRenderer.setEnableHardwareScaler(true); // 异步缩放
                            videoRenderer.setZOrderMediaOverlay(true); // 提升渲染优先级
                            fullRenderer.setZOrderMediaOverlay(true); // 提升渲染优先级
                        }

                    }

                    // 播放音频
                    if (!stream.audioTracks.isEmpty()) {
                        audioTrack = stream.audioTracks.get(0);
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

                        if(wsClient!=null) {
                            wsClient.sendOffer(json.toString());
                        }
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
        videoTransceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);

        // 添加音频接收器
        RtpTransceiver audioTransceiver = peerConnection.addTransceiver(
                MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO
        );
        audioTransceiver.setDirection(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY);


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


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // 更新Intent
        processIntent();
    }

    private void processIntent() {
        isScrcpy = getIntent().getBooleanExtra("isScrcpy",false);
        if (isScrcpy) {
            String url = getIntent().getStringExtra("url");
            urlEt.setText(url);
            isConnect=false;
            tabTop.check(R.id.scrcpy_client);
        }else{
            isConnect=true;
            tabTop.check(R.id.castx_client);
        }
    }


    public void loginCall(String data){
        System.out.println("loginCall data:"+data);
        try {
            JSONObject json = new JSONObject(data);
            boolean auth = json.getBoolean("auth");
            if(auth){
                initializeWebRTC();
            }else{
                runOnUiThread(()-> {
                    Toast.makeText(WebrtcPlayerActivity.this, "login err", Toast.LENGTH_SHORT).show();
                });
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public void offerRespCall(String data){
        System.out.println("offerRespCall data:"+data);
        // 5. 设置远程媒体流描述
        try {
            setRemoteDescription(data);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    public void infoNotifyCall(String data){
        System.out.println("infoNotifyCall data:"+data);
        try {
            JSONObject json = new JSONObject(data);
            boolean useAdb = json.getBoolean("useAdb");
             adbConnect = json.getBoolean("adbConnect");
            videoHeight=json.getInt("videoHeight");
            videoWidth=json.getInt("videoWidth");

            if(useAdb){
                isConnect=adbConnect?true:false;
                showAdbConnectFragment(!isConnect);
            }
        } catch (Exception e) {

           e.printStackTrace();
        }
    }



    private void showAdbConnectFragment(boolean show){
        runOnUiThread(()-> {
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            if (show) {
                transaction.show(adbConnectFragment);
            } else {
                transaction.hide(adbConnectFragment);
            }
            transaction.commit();
        });
    }


    private void setRemoteDescription(String remoteSdp ) throws JSONException {

        JSONObject json = new JSONObject(remoteSdp);
        String sdpStr = json.getString("sdp");
        GOOS=json.getString("GOOS");
        JSONObject sdpJson = new JSONObject(sdpStr);

        String sdp=sdpJson.getString("sdp");
        // 7. 创建远程会话描述
        SessionDescription remoteDesc = new SessionDescription(
                SessionDescription.Type.ANSWER, // 或者OFFER，取决于角色
                sdp
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
        try {
            String url = String.valueOf(urlEt.getText());
            URI uri = new URI(url.trim());
            String wsUrl = (uri.getScheme().equals("http")?"ws":"wss")+ "://" + uri.getHost() +  (uri.getPort()!=-1?(":"+uri.getPort()):"") + "/ws";
            String password = String.valueOf(et_password.getText());
            DisplayMetrics metrics = new DisplayMetrics();
            WindowManager windowManager = (WindowManager) getSystemService(Activity.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            display.getRealMetrics(metrics);
            int maxSize = metrics.widthPixels > metrics.heightPixels ? metrics.widthPixels : metrics.heightPixels;
            System.out.println("play maxSize:" + maxSize);
            System.out.println("wsUrl:" + wsUrl+"uri.getScheme():"+uri.getScheme());

            wsClient = new WsClient(wsUrl, password, maxSize);
            wsClient.setCallback(new WsClient.WsCallback() {
                @Override
                public void onLogin(JSONObject data) {
                   loginCall(data.toString());
                }

                @Override
                public void onOfferResponse(JSONObject data) {

                    offerRespCall(data.toString());

                }

                @Override
                public void onInfoNotify(JSONObject data) {
                    infoNotifyCall(data.toString());
                }

                @Override
                public void onStatusUpdate(String message) {

                }

                @Override
                public void onError(String error) {

                }
            });
            wsClient.connect();
            isRunning = true;
        }catch (URISyntaxException e){
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.stopScreenMirroringMsg, Toast.LENGTH_LONG).show();
            });
        }
        runOnUiThread(() -> {
            updateStartUI();
        });
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
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        updateStartUI();
    }

    private void exitFullscreen() {
        isFullscreen = false;
        // 1. 显示小窗
        miniContainer.setVisibility(View.VISIBLE);
        // 2. 隐藏全屏容器
        fullscreenContainer.setVisibility(View.GONE);
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        // 4. 恢复系统UI
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }


    // 保存密码到SharedPreferences
    private void saveConf(String key,String value) {
        SharedPreferences prefs =getSharedPreferences(isScrcpy?"scrcpyConfig":"castxConfig", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.apply();
    }

    // 从SharedPreferences加载已保存的密码
    private void loadConf() {
        SharedPreferences prefs = getSharedPreferences(isScrcpy?"scrcpyConfig":"castxConfig", Context.MODE_PRIVATE);
        String savedPassword = prefs.getString("password", "");
        String wsUrl = prefs.getString("url", "");
        urlEt.setText(wsUrl);
        et_password.setText(savedPassword);
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


    View.OnTouchListener touchHandler =  new View.OnTouchListener() {
        private long downTime;
        private float downX, downY;
        private float startX,startY;
        long duration=0;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            String type="";
             downX = 0;
             downY=0;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                        downTime = System.currentTimeMillis();
                        downX = event.getX();
                        downY = event.getY();
                        type="panstart";
                        System.out.println("panstart");
                        startX=downX;
                        startY=downY;
                        break;
                case MotionEvent.ACTION_MOVE:

                        downX = event.getX();
                        downY = event.getY();
                        type="pan";
                    System.out.println("pan");
                        break;

                case MotionEvent.ACTION_UP:
                        downX = event.getX();
                        downY = event.getY();

                        float touchNum=10;//小于10像素是点击
                         type="panend";
                        System.out.println("panend");
                        if(Math.abs(downX-startX)  < touchNum&&  Math.abs(downY  -startY ) < touchNum  ){

                            startX=0;
                            startY=0;
                            duration =System.currentTimeMillis() - downTime;
                            if(duration<15){
                                duration=15;
                            }
                            downTime=0;
                            type="click";
                            if(duration>400&&!GOOS.equals("android")){
                                type="rightClick";
                            }
                        }

                    break;
            }
            if(type.length()>0){
                try {
                    JSONObject json = new JSONObject();
                    json.put("type", type);


                    int width = videoRenderer.getWidth();
                    int height = videoRenderer.getHeight();
                    if (isFullscreen) {
                        width = fullRenderer.getWidth();
                        height = fullRenderer.getHeight();
                    }

                    System.out.println("src downX:"+downX);
                    if (width != videoWidth) {
                        downX = downX * ((float)videoWidth / (float)width);
                        downY = downY * ((float)videoHeight /(float)height);
                    }
                    json.put("x", downX);
                    json.put("y", downY);
                    json.put("videoWidth", videoWidth);
                    json.put("videoHeight", videoHeight);
                    json.put("duration", duration);
                    System.out.println("srcvideoWidth:"+videoWidth);
                    System.out.println("width:"+width);
                    System.out.println("downX:"+downX);
                    sendControl(json.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return true;
        }
    };

    private void  sendControl( String args) {
        if (wsClient != null) {
            wsClient.sendCmd("control",args);
        }
    }

    public void  ConnectAdb( String args) {
        if (wsClient != null) {
            wsClient.sendCmd("connectAdb",args);
        }
    }


    private void releaseWebRTCResources() {
        if (peerConnection != null) {
            peerConnection.dispose();
            peerConnection = null;
        }
        if (videoTrack != null) {
            videoTrack = null;
        }
        if (audioTrack != null) {
            audioTrack = null;
        }
        if (factory != null) {
            factory.dispose();
            factory = null;
        }
        if (eglBase != null) {
            eglBase.release();
            eglBase = null;
        }
        // 清理渲染器
        runOnUiThread(() -> {
            if (videoRenderer != null) {
                videoRenderer.release();
                // 注意：这里不能置为null，因为视图还在，我们只是释放资源，在下次初始化时会重新init
                videoRenderer.clearImage();
            }
            if (fullRenderer != null) {
                fullRenderer.release();
                fullRenderer.clearImage();
            }
        });
    }

}