package com.ly.bluetoothhelper;

import android.util.Log;

import com.ly.bluetoothhelper.utils.DataPacketUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;

import java.util.Arrays;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/25 9:30
 * version: 1.0
 */
public class Test {
    public static void main(String[] args){
        int num=0;
        if (num<16){

        }
        byte[] testBytes=TransformUtils.int2TwoBytes(500);
        System.out.println("code---"+TransformUtils.byte2Int((byte) 0x84));
        System.out.println("code---1="+TransformUtils.bytes2String(testBytes));
//        System.out.println(Arrays.toString(DataPacketUtils.eachFrameFirstPacket(4096,1,1)));
    }
}
