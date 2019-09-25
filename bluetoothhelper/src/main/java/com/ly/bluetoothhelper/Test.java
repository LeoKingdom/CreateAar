package com.ly.bluetoothhelper;

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
        System.out.println(Arrays.toString(DataPacketUtils.eachFrameFirstPacket(4096,1,1)));
    }
}
