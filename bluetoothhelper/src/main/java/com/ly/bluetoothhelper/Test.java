package com.ly.bluetoothhelper;

import android.util.Log;

import com.ly.bluetoothhelper.utils.DataPacketUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/25 9:30
 * version: 1.0
 */
public class Test {
    public static final byte[] ORDER_OAD_DATA_SEND={-85,0,0,0,0,32,3};
    public static void main(String[] args){
        String byteString=Arrays.toString(ORDER_OAD_DATA_SEND);
        byte[] a = TransformUtils.string2ByteArray(byteString);
        for (Byte b:a){
            System.out.println(b);
        }
        System.out.println("code---"+byteString);
        System.out.println("code---1="+TransformUtils.string2ByteArray(byteString));
//        System.out.println(Arrays.toString(DataPacketUtils.eachFrameFirstPacket(4096,1,1)));
    }
}
