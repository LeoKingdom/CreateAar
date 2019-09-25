package com.ly.bluetoothhelper.utils;

import android.content.Context;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/24 17:23
 * version: 1.0
 */
public class DataPacketUtils {
    /**
     * @param datas 字节流
     * @param curr  当前帧
     * @param total 总帧
     * @return 当前帧字节数组
     */
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

    /**
     * @param inputStream 文件输入流
     * @return 加上序号后的字节数组
     */
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

    public static byte[] dataLenght(int dataLenght) {
        byte[] lengthByte = new byte[3];
        int l = dataLenght / 256;
        int m = dataLenght % 256;
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

    /**
     * 数据帧帧头
     *
     * @param dataLength 字节长度
     * @param totalFrame 总帧数
     * @param currFrame  当前帧
     * @return
     */
    public static byte[] eachFrameFirstPacket(int dataLength, int totalFrame, int currFrame) {
        byte[] frameOrderByte = new byte[7];
        int l = dataLength / 256;
        int m = dataLength % 256;
        frameOrderByte[0] = TransformUtils.hexToByte("AB");
        frameOrderByte[1] = TransformUtils.int2byte(l);
        frameOrderByte[2] = TransformUtils.int2byte(m);
        frameOrderByte[3] = TransformUtils.int2byte(totalFrame);
        frameOrderByte[4] = TransformUtils.int2byte(currFrame);
        frameOrderByte[5] = TransformUtils.hexToByte("20");
        frameOrderByte[6] = TransformUtils.hexToByte("03");
        return frameOrderByte;
    }

    //0xAB 0x00 0x04 0x03  0x01  0x20 0x03  0x03  0x01 0x0A 0x64 收包回复
    //帧头 数据-长度 总帧 当前帧 MId  EId  丢包数 之后为丢包序号(上限五包,超过填0xFF)

    /**
     * @param data         字节流
     * @param responseByte 响应字节数组
     * @return
     */
    public static List<byte[]> losePacketList(byte[] data, byte[] responseByte) {
        List<byte[]> losePacketList = new ArrayList<>();
        int totalFrame = TransformUtils.byte2Int(responseByte[3]);
        int currentFrame = TransformUtils.byte2Int(responseByte[4]);
        byte[] currentFrameBytes = currentPacket(data, totalFrame, currentFrame);
        //总包数
        int length = data.length % 20 == 0 ? (data.length / 20) : (data.length / 20 + 1);
        if (responseByte[7] == (byte) 0x00) {
            //未丢包
            return null;
        } else if (responseByte[7] == (byte) 0x01) {
            //丢一包,responseByte长度为9
            int losePacketIndex = TransformUtils.byte2Int(responseByte[8]);
            byte[] onePacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex - 1, 20);
            losePacketList.add(onePacket);
        } else if (responseByte[7] == (byte) 0x02) {
            //丢两包,responseByte长度为10
            int losePacketIndex = TransformUtils.byte2Int(responseByte[8]);
            int losePacketIndex1 = TransformUtils.byte2Int(responseByte[9]);
            byte[] onePacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex - 1, 20);
            byte[] twoPacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex1 - 1, 20);
            losePacketList.add(onePacket);
            losePacketList.add(twoPacket);
        } else if (responseByte[7] == (byte) 0x03) {
            //丢三包,responseByte长度为11
            int losePacketIndex = TransformUtils.byte2Int(responseByte[8]);
            int losePacketIndex1 = TransformUtils.byte2Int(responseByte[9]);
            int losePacketIndex2 = TransformUtils.byte2Int(responseByte[10]);
            byte[] onePacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex - 1, 20);
            byte[] twoPacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex1 - 1, 20);
            byte[] threePacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex2 - 1, 20);
            losePacketList.add(onePacket);
            losePacketList.add(twoPacket);
            losePacketList.add(threePacket);
        } else if (responseByte[7] == (byte) 0x04) {
            //丢四包,responseByte长度为12
            int losePacketIndex = TransformUtils.byte2Int(responseByte[8]);
            int losePacketIndex1 = TransformUtils.byte2Int(responseByte[9]);
            int losePacketIndex2 = TransformUtils.byte2Int(responseByte[10]);
            int losePacketIndex3 = TransformUtils.byte2Int(responseByte[11]);
            byte[] onePacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex - 1, 20);
            byte[] twoPacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex1 - 1, 20);
            byte[] threePacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex2 - 1, 20);
            byte[] firthPacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex3 - 1, 20);
            losePacketList.add(onePacket);
            losePacketList.add(twoPacket);
            losePacketList.add(threePacket);
            losePacketList.add(firthPacket);
        } else if (responseByte[7] == (byte) 0x05) {
            //丢五包,responseByte长度为13
            int losePacketIndex = TransformUtils.byte2Int(responseByte[8]);
            int losePacketIndex1 = TransformUtils.byte2Int(responseByte[9]);
            int losePacketIndex2 = TransformUtils.byte2Int(responseByte[10]);
            int losePacketIndex3 = TransformUtils.byte2Int(responseByte[11]);
            int losePacketIndex4 = TransformUtils.byte2Int(responseByte[12]);
            byte[] onePacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex - 1, 20);
            byte[] twoPacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex1 - 1, 20);
            byte[] threePacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex2 - 1, 20);
            byte[] firthPacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex3 - 1, 20);
            byte[] fifthPacket = TransformUtils.subBytes(currentFrameBytes, losePacketIndex4 - 1, 20);
            losePacketList.add(onePacket);
            losePacketList.add(twoPacket);
            losePacketList.add(threePacket);
            losePacketList.add(firthPacket);
            losePacketList.add(fifthPacket);
        } else if (responseByte[7] == (byte) 0xFF) {
            //丢五包以上,responseByte长度为9
            return losePacketList;
        }
        return losePacketList;
    }
}
