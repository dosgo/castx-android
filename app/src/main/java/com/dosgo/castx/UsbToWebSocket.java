package com.dosgo.castx;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class UsbToWebSocket {
    private static final String TAG = "UsbToWebSocket";
    private static final String ACTION_USB_PERMISSION = "com.dosgo.castx.USB_PERMISSION";

    private Context context;
    private UsbManager usbManager;

    private UsbDeviceConnection usbConnection;
    private UsbEndpoint inEndpoint;
    private UsbEndpoint outEndpoint;
    private UsbDevice usbDevice;
    private WebSocketClient webSocketClient;
    private volatile boolean readingActive = false;
    private final int packetSize = 512*32;
    private final BroadcastReceiver usbPermissionReceiver;

    private UsbChangeCallback usbChangeCallback;
    // USB 设备变动回调接口
    public interface UsbChangeCallback {
        void onUsbDevicesChanged();
    }

    /**
     * 设置 USB 设备变动回调
     */
    public void setUsbChangeCallback( UsbChangeCallback callback) {
        this.usbChangeCallback = callback;
    }

    public UsbToWebSocket(Context context) {
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        // 创建USB权限广播接收器
        this.usbPermissionReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (ACTION_USB_PERMISSION.equals(action)) {
                    synchronized (this) {
                        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            if(device != null){
                                connectUsb(device);
                            }
                        }
                        else {
                            Log.d(TAG, "permission denied for device " + device);
                            Toast.makeText(context, R.string.connectUsbErr, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
                //设备插入
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    notifyUsbDevicesChanged();
                }else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    notifyUsbDevicesChanged();
                }
            }
        };

        // 注册广播接收器
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED); // 系统广播
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        // 使用 ContextCompat.registerReceiver 并指定标志位
        ContextCompat.registerReceiver(
                context,              // 上下文 (Activity, Service, Application)
                usbPermissionReceiver,            // 你的接收器实例
                filter,                // 意图过滤器
                ContextCompat.RECEIVER_NOT_EXPORTED //
        );
    }

    private void notifyUsbDevicesChanged() {
        if (usbChangeCallback != null) {
            usbChangeCallback.onUsbDevicesChanged();
        }
    }
    public void requestUsbPermission(UsbDevice _usbDevice ) {
        this.usbDevice=_usbDevice;
        PendingIntent permissionIntent = PendingIntent.getBroadcast(
                context, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE
        );
        usbManager.requestPermission(usbDevice, permissionIntent);
    }

    private void connectUsb(UsbDevice device) {
        usbConnection = usbManager.openDevice(device);
        if (usbConnection == null) {
            Toast.makeText(context, R.string.connectUsbErr1, Toast.LENGTH_SHORT).show();
            return;
        }

        // 使用第一个接口（通常为0）
        UsbInterface usbInterface = device.getInterface(0);
        if (!usbConnection.claimInterface(usbInterface, true)) {
            Toast.makeText(context, R.string.connectUsbErr1, Toast.LENGTH_SHORT).show();
            return;
        }

        // 查找端点
        for (int i = 0; i < usbInterface.getEndpointCount(); i++) {
            UsbEndpoint endpoint = usbInterface.getEndpoint(i);
            if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                inEndpoint = endpoint;
            } else if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                outEndpoint = endpoint;
            }
        }

        if (inEndpoint != null && outEndpoint != null) {
            connectWebSocket("ws://127.0.0.1:8082/usbWs");
        } else {
            Toast.makeText(context, R.string.connectUsbErr2, Toast.LENGTH_SHORT).show();
            Log.e(TAG, "未找到所需的USB端点");
        }
    }

    private void connectWebSocket(String serverUri) {
        webSocketClient = new WebSocketClient(URI.create(serverUri)) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                Log.i(TAG, "WebSocket连接已打开");
                startReadingUsbData();
            }

            @Override
            public void onMessage(String message) {

            }

            @Override
            public void onMessage(ByteBuffer bytes) {
                // 将WebSocket数据转发到USB设备
                byte[] data = new byte[bytes.remaining()];
                bytes.get(data);
                sendToUsb(data);
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.i(TAG, "WebSocket连接关闭: " + reason);
                closeAll();
            }

            @Override
            public void onError(Exception ex) {
                Log.e(TAG, "WebSocket错误: " + ex.getMessage());
                closeAll();
            }
        };
        webSocketClient.connect();
    }

    private void startReadingUsbData() {
        readingActive = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[packetSize];
                while (readingActive) {
                    int bytesRead = usbConnection.bulkTransfer(
                            inEndpoint,
                            buffer,
                            buffer.length,
                            100 // 超时时间(ms)
                    );

                    if (bytesRead > 0) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);
                        sendToWebSocket(data);
                    } else if (bytesRead < 0) {
                        // 错误或超时
                        Log.w(TAG, "USB读取错误: " + bytesRead);
                    }
                }
            }
        }).start();
    }

    private void sendToWebSocket(byte[] data) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.send(data);
        }
    }

    private void sendToUsb(byte[] data) {
        if (usbConnection != null && outEndpoint != null) {
            int bytesSent = usbConnection.bulkTransfer(outEndpoint, data, data.length, 100);
            if (bytesSent < 0) {
                Log.w(TAG, "USB写入错误: " + bytesSent);
            }
        }
    }

    public void closeAll() {
        readingActive = false;

        // 关闭WebSocket
        if (webSocketClient != null) {
            webSocketClient.close();
        }

        // 关闭USB连接
        if (usbConnection != null) {
            if (usbDevice != null) {
                UsbInterface usbInterface = usbDevice.getInterface(0);
                usbConnection.releaseInterface(usbInterface);
            }
            usbConnection.close();
            usbConnection = null;
        }

        // 注销广播接收器
        try {
            context.unregisterReceiver(usbPermissionReceiver);
        } catch (Exception e) {
            Log.w(TAG, "注销广播接收器失败", e);
        }
    }
}