package com.dosgo.castx;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import androidx.core.content.ContextCompat;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int REQUEST_CODE_SCREEN_CAPTURE = 1001;
    private Button btnControl,btn_receive;
    private TextView addrView;
    String addrTxt="";
    private EditText passwordInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        passwordInput = findViewById(R.id.et_password);

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

        btnControl = findViewById(R.id.btn_control);
        btnControl.setOnClickListener(v -> {
            if (Status.isRunning) {
                stopService(new Intent(this, ScreenCastService.class));
                btnControl.setText(R.string.startScreenMirroring);

                Toast.makeText(this, R.string.stopScreenMirroringMsg, Toast.LENGTH_SHORT).show();
            } else {
                startScreenCapture();
            }
        });
        btn_receive=findViewById(R.id.btn_receive);
        btn_receive.setOnClickListener(v -> {
            Intent intent = new Intent(this, ScrcpyClientActivity.class);
            startActivity(intent);
        });
        addrView = findViewById(R.id.addrView);
        getAllIpv4Addresses();
        startMonitoring(this);
    }

    // 保存密码到SharedPreferences
    private void savePassword(String password) {
        SharedPreferences prefs = getSharedPreferences("config", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("password", password);
        editor.apply();
    }

    // 从SharedPreferences加载已保存的密码
    private void loadSavedPassword() {
        SharedPreferences prefs = getSharedPreferences("config", Context.MODE_PRIVATE);
        String savedPassword = prefs.getString("password", "");
        passwordInput.setText(savedPassword);
    }

    @Override
    protected void onResume() {
        if (btnControl!=null){
            btnControl.setText(Status.isRunning? R.string.stopScreenMirroring:R.string.startScreenMirroring);
        }
        super.onResume();
        getAllIpv4Addresses();
    }
    // 更新显示数值


    private void startScreenCapture() {

        checkPermission();
        MyAccessibilityService service = MyAccessibilityService.getInstance();
        if (!isAccessibilityEnabled(this)||service==null){
            requestAccessibilityPermission();
        }
        MediaProjectionManager manager =
                (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
                manager.createScreenCaptureIntent(),
                REQUEST_CODE_SCREEN_CAPTURE
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE && resultCode == RESULT_OK) {
            Intent serviceIntent = new Intent(this, ScreenCastService.class)
                    .putExtra("EXTRA_RESULT_CODE", resultCode)
                    .putExtra("EXTRA_RESULT_INTENT", data);
            System.out.println("onActivityResultresultCode:"+resultCode);
            System.out.println("onActivityResult:"+data);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
                System.out.println("startForegroundService");
            } else {
                startService(serviceIntent);
            }
            btnControl.setText(R.string.stopScreenMirroring);
        }
    }

    public Boolean checkPermission()  {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
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
            runOnUiThread(() -> {
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
}
