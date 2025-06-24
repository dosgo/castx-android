package com.dosgo.castx;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class CastxFragment extends Fragment {
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1001;
    private Button btnControl,btn_receive,btn_scrcpy;
    private TextView addrView;
    String addrTxt="";
    private EditText passwordInput;


    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ScreenCastService.ACTION_UPDATE.equals(intent.getAction())) {
                // 更新UI
                updateStartUI();
            }
        }
    };



    @Override
    public View onCreateView( LayoutInflater inflater,
                              ViewGroup container,
                              Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_castx, container, false);
    }


    @Override
    public void onViewCreated( View view,  Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        passwordInput = view.findViewById(R.id.et_password);

        loadSavedPassword();
        passwordInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 当文本改变后自动保存密码
                savePassword(s.toString());
            }
        });
        Context context = getContext();
        btnControl = view.findViewById(R.id.btn_control);
        btnControl.setOnClickListener(v -> {
            if (Status.isRunning) {
                context.stopService(new Intent(context, ScreenCastService.class));
                btnControl.setText(R.string.startScreenMirroring);
                Toast.makeText(context, R.string.stopScreenMirroringMsg, Toast.LENGTH_SHORT).show();
            } else {
                startScreenCapture();

            }
        });
        btn_receive=view.findViewById(R.id.btn_receive);
        btn_receive.setOnClickListener(v -> {
            openView();
        });
        btn_scrcpy=view.findViewById(R.id.btn_scrcpy);
        btn_scrcpy.setOnClickListener(v -> {
            Intent intent = new Intent( getContext(), ScrcpyClientActivity.class);
            startActivity(intent);
        });
        addrView = view.findViewById(R.id.addrView);
        startMonitoring(context);
    }



    // 保存密码到SharedPreferences
    private void savePassword(String password) {
        SharedPreferences prefs =  getContext().getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("password", password);
        editor.apply();
    }

    // 从SharedPreferences加载已保存的密码
    private void loadSavedPassword() {
        SharedPreferences prefs =  getContext().getSharedPreferences("config", Context.MODE_PRIVATE);
        String savedPassword = prefs.getString("password", "");
        passwordInput.setText(savedPassword);
    }

    @Override
    public void onResume() {
         IntentFilter filter = new IntentFilter(ScreenCastService.ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // 本应用内部广播，可以安全导出（实际上不会被外部应用接收）
            getContext().registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 强制要求标志位
            getContext().registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            getContext().registerReceiver(receiver, filter);
        }
        updateStartUI();
        super.onResume();
        getAllIpv4Addresses();
    }

    @Override
    public  void onPause() {
        super.onPause();
        // 在Activity暂停时注销广播接收器，避免内存泄漏
        getContext().unregisterReceiver(receiver);
    }
    // 更新显示数值

    private void updateStartUI(){
        if (btnControl!=null){
            btnControl.setText(Status.isRunning? R.string.stopScreenMirroring:R.string.startScreenMirroring);
        }
    }


    private void startScreenCapture() {

        checkPermission();
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (!isAccessibilityEnabled( getContext())||service==null){
            requestAccessibilityPermission();
        }
        MediaProjectionManager manager =
                (MediaProjectionManager)  getContext().getSystemService( getContext().MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                manager.createScreenCaptureIntent(),
                REQUEST_CODE_SCREEN_CAPTURE
        );
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == Activity.RESULT_OK) {
            Intent serviceIntent = new Intent( getContext(), ScreenCastService.class)
                    .putExtra("EXTRA_RESULT_CODE", resultCode)
                    .putExtra("EXTRA_RESULT_INTENT", data);
            System.out.println("onActivityResultresultCode:"+resultCode);
            System.out.println("onActivityResult:"+data);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getContext().startForegroundService(serviceIntent);
                System.out.println("startForegroundService");
            } else {
                getContext().startService(serviceIntent);
            }
            btnControl.setText(R.string.stopScreenMirroring);
        }
    }

    public Boolean checkPermission()  {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&  getContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && getContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(   new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,  Manifest.permission.RECORD_AUDIO}, 1);
        }
        return false;
    }



    public void  getAllIpv4Addresses() {

         addrTxt="";

        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                if(intf.getName().indexOf("ap")==-1&&intf.getName().indexOf("wlan")==-1){
                    continue;
                }
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses();
                     enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if(inetAddress==null){
                        continue;
                    }
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {

                        if (inetAddress.getHostAddress()!=null){
                            addrTxt=addrTxt+"\r\n"+"http://"+inetAddress.getHostAddress()+":8081/";
                        }
                    }
                }
            }
            getActivity().runOnUiThread(() -> {
                addrView.setText(addrTxt);
            });
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    public  void startMonitoring(Context context) {
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build();

        cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                getAllIpv4Addresses();
            }

            @Override
            public void onLost(Network network) {
                getAllIpv4Addresses();
            }
        });
    }

    // 检查是否已启用无障碍服务
    private boolean isAccessibilityEnabled(Context context) {
        String serviceName = context.getPackageName() + "/.MyAccessibilityService";
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        if (accessibilityEnabled == 1) {
            String enabledServices = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            return enabledServices != null && enabledServices.contains(serviceName);
        }
        return false;
    }

    // 跳转到无障碍设置页面
    private void requestAccessibilityPermission() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }


    private void openView() {
        Intent intent = new Intent( getContext(), H264PlayerActivity.class);
        startActivity(intent);
    }


}
