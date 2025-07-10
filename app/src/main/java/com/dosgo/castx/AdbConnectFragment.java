package com.dosgo.castx;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;


import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import castX.CastX;


public class AdbConnectFragment extends Fragment {

    private UsbToWebSocket usbToWebSocket;

    private  View view;
    private EditText ipEt,authPort,etAuthCode,etConectPort;

    @Override
    public void onCreate( Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 创建USB转WebSocket实例
        usbToWebSocket = new UsbToWebSocket(getActivity());
        // 设置 USB 变动回调
        usbToWebSocket.setUsbChangeCallback(() -> {
            // 这个匿名函数会在 USB 设备变动时被调用
            findAndClaimTargetDevice(getActivity());
        });
    }

    @Override
    public View onCreateView( LayoutInflater inflater,
                              ViewGroup container,
                              Bundle savedInstanceState) {
        return inflater.inflate(R.layout.adb_connect, container, false);
    }


    @Override
    public void onViewCreated( View _view,  Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view=_view;
        _view.findViewById(R.id.tab_wifi).setOnClickListener(tab);
        _view.findViewById(R.id.tab_usb).setOnClickListener(tab);
        ipEt= _view.findViewById(R.id.ipEt);
        ipEt.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 当文本改变后自动保存密码
                saveConf("adbAddress",s.toString());
            }
        });

        authPort=_view.findViewById(R.id.authPort);
        etAuthCode=_view.findViewById(R.id.etAuthCode);
        etConectPort=_view.findViewById(R.id.etConectPort);
        etConectPort.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                // 当文本改变后自动保存密码
                saveConf("conectPort",s.toString());
            }
        });
        view.findViewById(R.id.btnPair).setOnClickListener(v -> {
                System.out.println("btnPair\r\n");
                 WebrtcPlayerActivity webrtcPlayerActivity = (WebrtcPlayerActivity) getActivity();
                if(!webrtcPlayerActivity.isRunning){
                    Toast.makeText(getActivity(),  R.string.pairMsg, Toast.LENGTH_SHORT).show();

                    return;
                }

                try {


                    JSONObject json = new JSONObject();
                    json.put("adbType", "pair");
                    json.put("selectedType", "wifi");
                    json.put("address",  ipEt.getText());
                    json.put("authPort", Long.parseLong(authPort.getText().toString().trim()));
                    json.put("authCode",  Long.parseLong(etAuthCode.getText().toString().trim()));



                    webrtcPlayerActivity.ConnectAdb(json.toString());
                    System.out.println("btnPair json:"+json+"\r\n");
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        );
        view.findViewById(R.id.btnConnect).setOnClickListener(v -> {
            WebrtcPlayerActivity webrtcPlayerActivity = (WebrtcPlayerActivity) getActivity();
            if(!webrtcPlayerActivity.isRunning){
                Toast.makeText(getActivity(), R.string.connectAdbMsg, Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                JSONObject json = new JSONObject();
                json.put("adbType", "connect");
                json.put("selectedType", "wifi");
                json.put("address",  ipEt.getText());
                json.put("connectPort", Long.parseLong(etConectPort.getText().toString().trim()));

                webrtcPlayerActivity.ConnectAdb(json.toString());
            }catch (Exception e){
                e.printStackTrace();
            }

        });
        loadConf();
    }


    // 保存密码到SharedPreferences
    private void saveConf(String key,String value) {
        SharedPreferences prefs =getActivity().getSharedPreferences("adbConf", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(key, value);
        editor.apply();
    }

    // 从SharedPreferences加载已保存的密码
    private void loadConf() {
        SharedPreferences prefs = getActivity().getSharedPreferences("adbConf", Context.MODE_PRIVATE);
        String adbAddress = prefs.getString("adbAddress", "");
        String conectPort = prefs.getString("conectPort", "");

        ipEt.setText(adbAddress);
        etConectPort.setText(conectPort);
    }



    @Override
    public  void onPause() {
        super.onPause();

    }

    @Override
    public  void onDestroy() {
        super.onDestroy();
        usbToWebSocket.closeAll();
    }




    View.OnClickListener tab=new View.OnClickListener(){

        @Override
        public void onClick(View v) {
            if( v.getId()==R.id.tab_wifi&&  view!=null){
                v.setBackgroundColor( Color.parseColor("#2196F3"));
                view.findViewById(R.id.tab_usb).setBackgroundColor( Color.parseColor("#F5F5F5"));
                view.findViewById(R.id.wifiView).setVisibility(View.VISIBLE);
                view.findViewById(R.id.usbView).setVisibility(View.GONE);
            }
            if( v.getId()==R.id.tab_usb&&  view!=null){
                v.setBackgroundColor( Color.parseColor("#2196F3"));
                view.findViewById(R.id.tab_wifi).setBackgroundColor( Color.parseColor("#F5F5F5"));
                view.findViewById(R.id.wifiView).setVisibility(View.GONE);
                view.findViewById(R.id.usbView).setVisibility(View.VISIBLE);
                findAndClaimTargetDevice(getActivity());
            }
        }
    };

    private boolean hasInterfaceAdb(UsbDevice device) {
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            if (device.getInterface(i).getInterfaceClass() == 0xFF&&
                    device.getInterface(i).getInterfaceSubclass()==0x42&&
                    device.getInterface(i).getInterfaceProtocol()==0x01
                ) {
                return true;
            }
        }
        return false;
    }
    public  void findAndClaimTargetDevice(Context context) {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            return;
        }
        // 获取所有已连接的USB设备
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        List<UsbItemAdapter.UsbItem> items = new ArrayList<>();
        for (Map.Entry<String, UsbDevice> entry : deviceList.entrySet()) {
            UsbDevice device = entry.getValue();
            //如果是adb设备
            if (hasInterfaceAdb(device)) {
                items.add(new UsbItemAdapter.UsbItem(device.getDeviceName(), device));
            }
        }
        WebrtcPlayerActivity webrtcPlayerActivity = (WebrtcPlayerActivity) getActivity();

        // 设置适配器
        UsbItemAdapter adapter = new UsbItemAdapter(webrtcPlayerActivity, items,usbToWebSocket);
        ListView listView = view.findViewById(R.id.usbList);
        listView.setAdapter(adapter);
    }

}
