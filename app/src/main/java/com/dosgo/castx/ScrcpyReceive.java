package com.dosgo.castx;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ScrcpyReceive {
    private static final String TAG = "H264Receive";
    private OutputStream outputStream;
    private  InputStream inputStream;


    /*
    type 1 video 2 audio
    * */
    public  ScrcpyReceive(int port, int type ) {
        try {
            Socket socket = new Socket("127.0.0.1", port);
            outputStream = socket.getOutputStream();
            writeHeader(type);
            Log.i(TAG, "Connected to Go server");
            inputStream= socket.getInputStream();
        } catch (Exception e) {
            Log.e(TAG, "Connection failed", e);
        }
    }



    public void writeHeader( int type) throws IOException {
         String typeStr= type==1?"video":"audio";
        outputStream.write(typeStr.getBytes());
    }

    public InputStream getInputStream(  )  {
       return inputStream;
    }


}