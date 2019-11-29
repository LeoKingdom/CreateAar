package com.ly.bluetoothhelper.utils;

import java.io.IOException;
import java.io.InputStream;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/11/29 15:38
 * version: 1.0
 * 蓝牙数据解帧和组帧
 */
public class FrameUtils {

    /**
     * 解帧,获取每一帧的字节数组
     *
     * @param inputStream     数据输入流
     * @param mtu 最大传输单元
     * @param eachFrameLength 每帧大小
     * @param currentFrame    当前帧
     */
    public static byte[] deFrame(InputStream inputStream,int mtu, int eachFrameLength, int currentFrame) throws IOException {
        byte[] dataBytes = TransformUtils.streamToByte(inputStream);
        int totalFrame = dataBytes.length % eachFrameLength != 0 ? ((dataBytes.length / eachFrameLength) + 1) : (dataBytes.length / eachFrameLength);
        byte[] currentFrameBytes = getCurrentFrameBytes(dataBytes, eachFrameLength, totalFrame, currentFrame);
        byte[] sortEachFrame=splitEachFrameBytes(currentFrameBytes,mtu,eachFrameLength,false);
        return sortEachFrame;
    }

    /**
     * 解帧,返回每一帧处理后的字节数组,即添加序号并进行crc校验最后拼接
     *
     * @param inputStream     数据输入流
     * @param mtu 最大传输单元
     * @param eachFrameLength 每帧大小
     * @param currentFrame    当前帧
     */
    public static byte[] deFrameWithCrc(InputStream inputStream,int mtu, int eachFrameLength, int currentFrame) throws IOException {
        byte[] dataBytes = TransformUtils.streamToByte(inputStream);
        int totalFrame = dataBytes.length % eachFrameLength != 0 ? ((dataBytes.length / eachFrameLength) + 1) : (dataBytes.length / eachFrameLength);
        byte[] currentFrameBytes = getCurrentFrameBytes(dataBytes, eachFrameLength, totalFrame, currentFrame);
        byte[] sortEachFrame=splitEachFrameBytes(currentFrameBytes,mtu,eachFrameLength,true);
        return sortEachFrame;
    }

    /**
     * 组帧
     *
     */
    public static void framing(byte[] data) {

    }

    /**
     * 获取每一帧的字节数组
     *
     * @param dataBytes       字节流
     * @param eachFrameLength 每帧大小
     * @param totalFrame      总帧数
     * @param currentFrame    当前帧
     */
    private static byte[] getCurrentFrameBytes(byte[] dataBytes, int eachFrameLength, int totalFrame, int currentFrame) {
        byte[] eachFrameBytes = null;
        if (dataBytes.length > eachFrameLength) {
            if (currentFrame == totalFrame) {
                //最后一帧,不一定是4kb
                int lastPacketLenght = dataBytes.length - (totalFrame - 1) * eachFrameLength;
                eachFrameBytes = TransformUtils.subBytes(dataBytes, (currentFrame - 1) * eachFrameLength, lastPacketLenght);
            } else {
                //每一帧,长度为4kb
                eachFrameBytes = TransformUtils.subBytes(dataBytes, (currentFrame - 1) * eachFrameLength, eachFrameLength);
            }
        } else {
            eachFrameBytes = dataBytes;
        }
        return eachFrameBytes;
    }

    /**
     * 为每一帧的每一包加上序号和最后的crc字节
     *
     * @param eachFrameBytes  一帧数据
     * @param mtu             最大传输单元,蓝牙默认20
     * @param withCrc         是否进行crc校验并拼接在每一帧结尾
     * @param eachFrameLength 每帧大小
     * @return 加上序号后一帧数据
     */
    private static byte[] splitEachFrameBytes(byte[] eachFrameBytes, int mtu, int eachFrameLength, boolean withCrc) {
        int mtuMinus = mtu - 1;
        int totalPacket = eachFrameLength % mtuMinus == 0 ? eachFrameLength / mtuMinus : eachFrameLength / mtuMinus + 1;
        int length = (eachFrameBytes.length % mtuMinus == 0) ? (eachFrameBytes.length / mtuMinus) : (eachFrameBytes.length / mtuMinus + 1);
        int num = 0;
        int num1 = 0;
        byte[] lastBytes = null;
        boolean isTrue = eachFrameBytes.length % mtuMinus == 0;
        int tem = mtuMinus;
        for (int i = 1; i <= length; i++) {
            num++;
            num1++;
            byte[] eachHeadBytes = new byte[]{(byte) (Integer.parseInt(Integer.toHexString(num), 16))};
            if (num == totalPacket) {
                num = 0;
            }

            if (!isTrue && i == length) {
                tem = eachFrameBytes.length - mtuMinus * (length - 1);
            }
            byte[] eachBytes = TransformUtils.subBytes(eachFrameBytes, (i - 1) * mtuMinus, tem);
            byte[] handleBytes = TransformUtils.combineArrays(eachHeadBytes, eachBytes);
            if (i != 1) {
                lastBytes = TransformUtils.combineArrays(lastBytes, handleBytes);
            } else {
                lastBytes = handleBytes;
            }
        }
        byte crcByte = CRCCheckUtils.calcCrc8(eachFrameBytes);
        if (isTrue) {
            if (withCrc) {
                lastBytes = TransformUtils.combineArrays(lastBytes, new byte[]{(byte) num1, crcByte});
            }
        } else {
            if (withCrc) {
                lastBytes = TransformUtils.combineArrays(lastBytes, new byte[]{crcByte});
            }
        }
        return lastBytes;
    }
}
