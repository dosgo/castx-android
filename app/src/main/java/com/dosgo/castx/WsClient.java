package com.dosgo.castx;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public class WsClient {

    public interface WsCallback {
        void onLogin(JSONObject data);
        void onOfferResponse(JSONObject data);
        void onInfoNotify(JSONObject data);
        void onStatusUpdate(String message);
        void onError(String error);
    }

    private WebSocketClient webSocketClient;
    private String securityKey = "";
    private boolean isAuth = false;
    private boolean isConnected = false;
    private WsCallback callback;

    private String serverUri;
    private String password;
    private int maxSize;

    public WsClient(String serverUri, String password, int maxSize) {
        this.serverUri = serverUri;
        this.password = password;
        this.maxSize = maxSize;
    }

    public void setCallback(WsCallback callback) {
        this.callback = callback;
    }

    public void connect() {
        try {
            URI uri = new URI(serverUri);
            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    isConnected = true;

                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    handleMessage(new String(bytes.array()));
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    isConnected = false;
                    isAuth = false;
                    securityKey = "";

                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }
            };

            webSocketClient.connect();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isAuthenticated() {
        return isAuth;
    }

    public void sendOffer(String offerJSON) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject message = new JSONObject();
                message.put("type", "offer");
                message.put("data", offerJSON);
                webSocketClient.send(message.toString());

            } catch (JSONException e) {
               e.printStackTrace();
            }
        } else {

        }
    }

    public void sendCmd(String cmd, String args) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            try {
                JSONObject message = new JSONObject();
                message.put("type", cmd);
                message.put("data", args);
                webSocketClient.send(message.toString());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else {

        }
    }

    private void handleMessage(String message) {
        try {
            JSONObject json = new JSONObject(message);
            System.out.println("handleMessage:"+message);
            String type = json.getString("type");
            String dataString = json.optString("data", "{}");
            JSONObject data = new JSONObject(dataString);

            switch (type) {
                case "initConfig":
                    securityKey = data.getString("securityKey");
                    login();
                    break;
                case "loginAuthResp":
                    isAuth = data.optBoolean("success", false);
                    if (callback != null) callback.onLogin(data);
                    break;
                case "offerResponse":
                    if (callback != null) callback.onOfferResponse(data);
                    break;
                case "infoNotify":
                    if (callback != null) callback.onInfoNotify(data);
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void login() {
        long timestamp = System.currentTimeMillis();
        String srcData = securityKey + "|" + timestamp + "|" + password;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(srcData.getBytes());
            String token = bytesToHex(hash);

            Map<String, Object> args = new HashMap<>();
            args.put("maxSize", maxSize);
            args.put("token", token);
            args.put("timestamp", timestamp);

            JSONObject jsonArgs = new JSONObject(args);
            JSONObject message = new JSONObject();
            message.put("type", "loginAuth");
            message.put("data", jsonArgs.toString());

            webSocketClient.send(message.toString());

        } catch (NoSuchAlgorithmException | JSONException e) {

        }
    }

    private String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }




}