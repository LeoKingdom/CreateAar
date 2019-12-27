package com.ly.bluetoothhelper.callbacks.upgrade_callback;

import fastble.data.BleDevice;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/12/13 10:21
 * version: 1.0
 */
public interface U_ScanConnListener {
    void scanFinish(BleDevice device);
    void connSuccess(int connType,BleDevice device);
    void connFail(BleDevice device,String des);
    void disconnDevice(boolean isActive,BleDevice device);
}
