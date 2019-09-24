package com.ly.bluetoothhelper.utils;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/24 17:23
 * version: 1.0
 */
public class DataPacketUtils {
    public static byte[] currentPacket(byte[] datas, int curr, int total) {
        byte[] eachFrameBytes = null;
        if (curr == total - 1) {
            //最后一帧,不一定是4kb
            int lastPacketLenght = datas.length - (total - 1) * 4 * 1024;
            eachFrameBytes = TransformUtils.subBytes(datas, curr * 1024, lastPacketLenght);
        } else {
            //每一帧,长度为4kb
            eachFrameBytes = TransformUtils.subBytes(datas, curr * 1024, 4 * 1024);
        }
        return eachFrameBytes;
    }

    public static byte[] getHeadBytes(Context context, InputStream inputStream) {
        byte[] bytes = null;
        try {
            bytes = TransformUtils.streamToByte(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] bytes1 = TransformUtils.subBytes(bytes, 0, 5);
        return bytes1;
    }

    public static byte[] combinePacket(InputStream inputStream) {
        //合并后的字节数组
        byte[] lastBytes = null;
        try {
            byte[] bytes = TransformUtils.streamToByte(inputStream);
            byte[] bytes1 = TransformUtils.subBytes(bytes, 0, 5);
            int length = bytes.length % 20 == 0 ? (bytes.length / 20) : (bytes.length / 20 + 1);
            byte[] newBytes = new byte[length + bytes.length];
            //由于需要带上序号,因此总的byte数组长度增大
            int totalPackets0 = (bytes.length % 19 == 0) ? (bytes.length / 19) : (bytes.length / 19 + 1);
            int num = 0;
            int currentPacket = 0;
            if (bytes.length > 4 * 1024) {
                for (int j = 0; j < totalPackets0; j++) {
                    num++;
                    if (num == 205) {
                        num = 1;
                    }
                    byte[] eachHeadBytes = new byte[]{(byte) (Integer.parseInt(Integer.toHexString(num), 16))};
                    int tem = 0;
                    if (j != totalPackets0 - 1) {
                        tem = 19;
                    } else {
                        tem = bytes.length - 19 * (totalPackets0 - 1);
                    }
                    byte[] eachBytes = TransformUtils.subBytes(bytes, j * 19, tem);
                    byte[] handleBytes = TransformUtils.combineArrays(eachHeadBytes, eachBytes);
                    if (j != 0) {
                        lastBytes = TransformUtils.combineArrays(lastBytes, handleBytes);
                    } else {
                        lastBytes = TransformUtils.combineArrays(handleBytes);
                    }
                }
            } else {
                for (int j = 0; j < totalPackets0; j++) {
                    byte[] eachHeadBytes = new byte[]{(byte) (Integer.parseInt(Integer.toHexString(j + 1), 16))};
                    int tem = 0;
                    if (j != totalPackets0 - 1) {
                        tem = 19;
                    } else {
                        tem = bytes.length - 19 * (totalPackets0 - 1);
                    }
                    byte[] eachBytes = TransformUtils.subBytes(bytes, j * 19, tem);
                    byte[] handleBytes = TransformUtils.combineArrays(eachHeadBytes, eachBytes);
                    if (j != 0) {
                        lastBytes = TransformUtils.combineArrays(lastBytes, handleBytes);
                    } else {
                        lastBytes = TransformUtils.combineArrays(handleBytes);
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return lastBytes;
    }

    public static byte[] dataLenght(byte[] data) {
        byte[] lengthByte = new byte[3];
        int l = data.length / 256;
        int m = data.length % 16;
        lengthByte[0] = TransformUtils.hexToByte("AB");
        lengthByte[1] = TransformUtils.int2byte(l);
        lengthByte[2] = TransformUtils.int2byte(m);
        return lengthByte;
    }

    public static byte[] frameBytes(int totalFrame, int currFrame) {
        byte[] frameByte = new byte[2];
        frameByte[0] = TransformUtils.int2byte(totalFrame);
        frameByte[1] = TransformUtils.int2byte(currFrame);
        return frameByte;
    }
}
