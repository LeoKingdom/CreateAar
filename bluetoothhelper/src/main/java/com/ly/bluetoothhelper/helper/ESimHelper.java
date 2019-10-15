package com.ly.bluetoothhelper.helper;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/15 11:11
 * version: 1.0
 *
 * esim卡工具类,主要为外围设备和中心设备围绕esim展开的交互(蓝牙)
 */
public class ESimHelper {


    public void esimActive(){

    }

    public interface EsimActiveCallback{
        void activeSuccess(String msg);
        void activeFail(String error);
    }
}
