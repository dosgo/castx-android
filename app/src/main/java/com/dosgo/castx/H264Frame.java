package com.dosgo.castx;

import java.util.Arrays;

class H264Frame {
    final byte[] data;
    final long timestamp; // 单位微秒(μs)
    final boolean isKeyFrame;
    H264Frame(byte[] data,int len, long timestamp,boolean isKeyFrame) {
        this.data = Arrays.copyOf(data, len);
        this.timestamp = timestamp;
        this.isKeyFrame=isKeyFrame;
    }
}
