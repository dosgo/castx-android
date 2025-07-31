package com.dosgo.castx;

import android.content.Context;

import org.concentus.OpusDecoder;
import org.concentus.OpusEncoder;
import org.concentus.OpusException;
import org.concentus.OpusMode;
import org.concentus.OpusSignal;
import org.concentus.OpusApplication;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class test {


    public static void test(Context context)
    {
        try {

            InputStream inputStream = context.getResources().openRawResource(R.raw.hztereo);

            OpusEncoder encoder = new OpusEncoder(48000, 2, OpusApplication.OPUS_APPLICATION_AUDIO);
            encoder.setBitrate(96000);
            encoder.setForceMode(OpusMode.MODE_CELT_ONLY);
            encoder.setSignalType(OpusSignal.OPUS_SIGNAL_MUSIC);
            encoder.setComplexity(0);

            OpusDecoder decoder = new OpusDecoder(48000, 2);


            int packetSamples = 960;
            byte[] inBuf = new byte[packetSamples * 2 * 2];
            byte[] data_packet = new byte[1275];
            long start = System.currentTimeMillis();
            while (inputStream.available() >= inBuf.length) {
                int bytesRead = inputStream.read(inBuf, 0, inBuf.length);
                short[] pcm = BytesToShorts(inBuf, 0, inBuf.length);
                System.out.println("PCM len: " + pcm.length);
                int bytesEncoded = encoder.encode(pcm, 0, packetSamples, data_packet, 0, 1275);
                System.out.println("bytesEncoded:"+bytesEncoded + " bytes encoded");

                int samplesDecoded = decoder.decode(data_packet, 0, bytesEncoded, pcm, 0, packetSamples, false);
                System.out.println(samplesDecoded + " samples decoded");
              ///  byte[] bytesOut = BytesToShorts1(pcm);
                break;

            }

            long end = System.currentTimeMillis();
            System.out.println("Time was " + (end - start) + "ms");
            inputStream.close();

            System.out.println("Done!");
        } catch (IOException e) {
            System.out.println(e.getMessage());
        } catch (OpusException e) {
            System.out.println(e.getMessage());
        }
    }

    public static byte[] ShortsToBytes(short[] input, int offset, int length) {
        byte[] processedValues = new byte[length * 2];
        for (int c = 0; c < length; c++) {
            processedValues[c * 2] = (byte) (input[c + offset] & 0xFF);
            processedValues[c * 2 + 1] = (byte) ((input[c + offset] >> 8) & 0xFF);
        }

        return processedValues;
    }
    public static short[] BytesToShorts1(byte[] input) {
        return BytesToShorts(input, 0, input.length);
    }

    public static short[] BytesToShorts(byte[] input, int offset, int length) {
        short[] processedValues = new short[length / 2];
        for (int c = 0; c < processedValues.length; c++) {
            short a = (short) (((int) input[(c * 2) + offset]) & 0xFF);
            short b = (short) (((int) input[(c * 2) + 1 + offset]) << 8);
            processedValues[c] = (short) (a | b);
        }

        return processedValues;
    }

}
