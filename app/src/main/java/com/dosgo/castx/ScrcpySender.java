package com.dosgo.castx;

import android.net.LocalSocketAddress;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

public class ScrcpySender {
    private static final String TAG = "H264Sender";
    private OutputStream outputStream;

    private static final long PACKET_FLAG_CONFIG = 1L << 63;
    private static final long PACKET_FLAG_KEY_FRAME = 1L << 62;
    private final ByteBuffer headerBuffer = ByteBuffer.allocate(12);


    public ScrcpySender(int port, int winth, int height) {
        try {
            Socket socket = new Socket("127.0.0.1", port);

            outputStream = socket.getOutputStream();

            writeVideoHeader(winth,height);

            Log.i(TAG, "Connected to Go server");
        } catch (Exception e) {
            Log.e(TAG, "Connection failed", e);
        }
    }

    public ScrcpySender(int port) {
        try {
            Socket socket = new Socket("127.0.0.1", port);

            outputStream = socket.getOutputStream();

            writeAudioHeader();

            Log.i(TAG, "Connected to Go server");
        } catch (Exception e) {
            Log.e(TAG, "Connection failed", e);
        }
    }



    public void writeAudioHeader() throws IOException {

        ByteBuffer buffer = ByteBuffer.allocate(4);
        int id=0x6f707573;//opus
        buffer.putInt(id);
        outputStream.write(buffer.array());

    }

    public void writeVideoHeader( int width,int height) throws IOException {

            ByteBuffer buffer = ByteBuffer.allocate(12);
            int id=0x68323634;//h264
            buffer.putInt(id);
            buffer.putInt(width);
            buffer.putInt(height);

          outputStream.write(buffer.array());

    }

     public void writeFrame( ByteBuffer buffer, long pts, boolean config, boolean keyFrame) throws IOException {
        if (outputStream==null){
            return;
        }
        headerBuffer.clear();
        long ptsAndFlags;
        if (config) {
            ptsAndFlags = PACKET_FLAG_CONFIG; // non-media data packet
        } else {
            ptsAndFlags = pts;
            if (keyFrame) {
                ptsAndFlags |= PACKET_FLAG_KEY_FRAME;
            }
        }
        headerBuffer.putLong(ptsAndFlags);
        headerBuffer.putInt(buffer.limit());
        outputStream.write(headerBuffer.array());

         if (buffer.hasArray()) {
             outputStream.write(buffer.array());
         } else {
             byte[] frameArray;
             // 如果是直接缓冲区
             frameArray = new byte[buffer.remaining()];
             buffer.get(frameArray); // 复制数据到字节数组
             outputStream.write(frameArray);
         }
    }
}