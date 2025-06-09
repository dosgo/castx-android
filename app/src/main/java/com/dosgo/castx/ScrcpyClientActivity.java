package com.dosgo.castx;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;


public class ScrcpyClientActivity extends Activity   {

    private Button btnControl;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrcpy);

        btnControl = findViewById(R.id.btn_control);

        //
        findViewById(R.id.btn_open).setOnClickListener(v -> {
            if (! Status.scrcpyIsRunning) {
                Toast.makeText(this, R.string.scrcpyNotStarted, Toast.LENGTH_SHORT).show();
                return;
            }
            openEdgeCustomTab();
        });


        btnControl.setOnClickListener(v -> {
            if ( Status.scrcpyIsRunning) {
                stopService(new Intent(this, ScrcpyClientService.class));
                btnControl.setText(R.string.startScrcpyClient);
                Status.scrcpyIsRunning=false;
            } else {
                btnControl.setText(R.string.stopScrcpyClient);
                Status.scrcpyIsRunning=true;
                startService(new Intent(this, ScrcpyClientService.class));
            }
        });

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

        // 自定义UI设置

       // builder.setShowTitle(true);
        // 设置Edge浏览器（需要用户安装）
        CustomTabsIntent customTabsIntent = builder.build();
        // 检查Edge是否安装
        if(isEdgeInstalled(this)) {
            customTabsIntent.intent.setPackage("com.microsoft.emmx");//edge
            customTabsIntent.launchUrl(this, Uri.parse(url));
        }else if(isChromeInstalled(this)) {
            customTabsIntent.intent.setPackage("com.android.chrome");//chrmoe
            customTabsIntent.launchUrl(this, Uri.parse(url));
        }else if(isFirefoxInstalled(this)){
            customTabsIntent.intent.setPackage("org.mozilla.firefox");//Firefox
            customTabsIntent.launchUrl(this, Uri.parse(url));
        } else{
            Toast.makeText(this, R.string.stopScreenMirroringMsg, Toast.LENGTH_LONG).show();
        }
    }
    @Override
    protected void onResume() {
        if (btnControl!=null){
            btnControl.setText(Status.scrcpyIsRunning? R.string.stopScrcpyClient:R.string.startScrcpyClient);
        }
        super.onResume();
    }


}