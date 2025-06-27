package com.dosgo.castx;

import org.json.JSONException;

import castX.JavaCallbackInterface;
public class CallInterface implements JavaCallbackInterface {

    private static H264PlayerActivity play;
    public  void controlCall(String param) {
        try {
            System.out.println("callString param:"+param);
            Control.cmd(param);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        return ;
    }

    @Override
    public void loginCall(String s) {
        Control.loginCall(s);
    }

    @Override
    public void offerRespCall(String s) {
        Control.offerRespCall(s);
    }


    public  void webRtcConnectionStateChange(long count) {
        if(count<1){
            if (ScreenCastService.getInstance()!=null) {
                ScreenCastService.getInstance().stopDecoding();
                System.out.println("stopDecoding\r\n");
            }
        }else{
            if (ScreenCastService.getInstance()!=null) {
                ScreenCastService.getInstance().startDecoding();
                System.out.println("startDecoding\r\n");
            }
        }
        return ;
    }
    public  void setMaxSize(long count) {
        if (ScreenCastService.getInstance()!=null) {
            System.out.println("setMaxSize:"+count+"\r\n");
            ScreenCastService.getInstance().setMaxSize((int) count);
        }
    }

}