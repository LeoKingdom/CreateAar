package com.ly.bluetoothhelper.callbacks.upgrade_callback;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/12/13 13:19
 * version: 1.0
 */
public interface U_WriteListener {
    void writeSuccess(int total,int current,byte[] currWrite);
    void writeFail(String des);
}
