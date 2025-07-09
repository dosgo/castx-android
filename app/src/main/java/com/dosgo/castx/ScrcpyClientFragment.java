package com.dosgo.castx;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.fragment.app.Fragment;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.SecureRandom;
import java.util.Enumeration;


public class ScrcpyClientFragment extends Fragment {

    private Button btnControl;

    private TextView addrView;

    String addrTxt="";
    private EditText passwordInput;

    @Override
    public View onCreateView(LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_scrcpy, container, false);
    }

    @Override
    public void onViewCreated( View view,  Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        btnControl = view.findViewById(R.id.btn_control);
        Context context=getContext();
        //
        view.findViewById(R.id.btn_open).setOnClickListener(v -> {
            if (! Status.scrcpyIsRunning) {
                Toast.makeText(context, R.string.scrcpyNotStarted, Toast.LENGTH_SHORT).show();
                return;
            }
            openView();
        });
        view.findViewById(R.id.btn_open_browser).setOnClickListener(v -> {
            if (! Status.scrcpyIsRunning) {
                Toast.makeText(context, R.string.scrcpyNotStarted, Toast.LENGTH_SHORT).show();
                return;
            }
            openEdgeCustomTab();
        });




        btnControl.setOnClickListener(v -> {
            if ( Status.scrcpyIsRunning) {
                context.stopService(new Intent(context, ScrcpyClientService.class));
                btnControl.setText(R.string.startScrcpyClient);
                Status.scrcpyIsRunning=false;
            } else {
                btnControl.setText(R.string.stopScrcpyClient);
                Status.scrcpyIsRunning=true;
                context.startService(new Intent(context, ScrcpyClientService.class));
            }
        });
        addrView = view.findViewById(R.id.addrView);
        startMonitoring(context);


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
    }


    // 保存密码到SharedPreferences
    private void savePassword(String password) {
        SharedPreferences prefs =  getContext().getSharedPreferences("scrcpyConfig", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("password", password);
        editor.apply();
    }

    // 从SharedPreferences加载已保存的密码
    private void loadSavedPassword() {
        SharedPreferences prefs =  getContext().getSharedPreferences("scrcpyConfig", Context.MODE_PRIVATE);
        String savedPassword = prefs.getString("password", "");
        if(savedPassword.length()>0){
            passwordInput.setText(savedPassword);
        }else{
            passwordInput.setText(generateSimplePassword(6));
        }
    }
    public static String generateSimplePassword(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
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

    private void openView() {
        Intent intent = new Intent(getActivity(), WebrtcPlayerActivity.class);
        intent.putExtra("url", "http://127.0.0.1:8082/");
        intent.putExtra("isScrcpy", true);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT );
        startActivity(intent);
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
                            addrTxt=addrTxt+"\r\n"+"http://"+inetAddress.getHostAddress()+":8082/";
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

    public static boolean isChromeInstalled(Context context) {
        return isPackageInstalled(context, "com.android.chrome");
    }

    // 检查Edge是否安装
    public static boolean isEdgeInstalled(Context context) {
        return isPackageInstalled(context, "com.microsoft.emmx");
    }
    public static boolean isFirefoxInstalled(Context context) {
        return isPackageInstalled(context, "org.mozilla.firefox");
    }

    // 检查指定包名的应用是否安装
    private static boolean isPackageInstalled(Context context, String packageName) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info != null;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    // 在 Activity 中使用自定义标签
    private void openEdgeCustomTab() {
        String url = "http://127.0.0.1:8082/scrcpy.html";
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        Context context=getContext();
        // 自定义UI设置

       // builder.setShowTitle(true);
        // 设置Edge浏览器（需要用户安装）
        CustomTabsIntent customTabsIntent = builder.build();
        // 检查Edge是否安装
        if(isEdgeInstalled(context)) {
            customTabsIntent.intent.setPackage("com.microsoft.emmx");//edge
            customTabsIntent.launchUrl(context, Uri.parse(url));
        }else if(isChromeInstalled(context)) {
            customTabsIntent.intent.setPackage("com.android.chrome");//chrmoe
            customTabsIntent.launchUrl(context, Uri.parse(url));
        }else if(isFirefoxInstalled(context)){
            customTabsIntent.intent.setPackage("org.mozilla.firefox");//Firefox
            customTabsIntent.launchUrl(context, Uri.parse(url));
        } else{
            Toast.makeText(context, R.string.stopScreenMirroringMsg, Toast.LENGTH_LONG).show();
        }
    }


    private void openWithSpecificBrowser() {
        String url = "http://127.0.0.1:8082/scrcpy.html";
        try {
            Uri uri = Uri.parse(url);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            Context context=getContext();
            if(isEdgeInstalled(context)) {
                intent.setPackage("com.microsoft.emmx");
                startActivity(intent);
            }else if(isChromeInstalled(context)) {
                intent.setPackage("com.android.chrome");//chrmoe
                startActivity(intent);
            }else if(isFirefoxInstalled(context)){
                intent.setPackage("org.mozilla.firefox");//Firefox
                startActivity(intent);
            } else{
                Toast.makeText(context, R.string.stopScreenMirroringMsg, Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(getActivity(), R.string.stopScreenMirroringMsg, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        if (btnControl!=null){
            btnControl.setText(Status.scrcpyIsRunning? R.string.stopScrcpyClient:R.string.startScrcpyClient);
        }
        getAllIpv4Addresses();
        super.onResume();
    }


}