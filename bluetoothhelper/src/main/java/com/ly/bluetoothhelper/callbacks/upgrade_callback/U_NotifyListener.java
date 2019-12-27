package com.ly.bluetoothhelper.callbacks.upgrade_callback;

import fastble.data.BleDevice;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/12/13 13:09
 * version: 1.0
 */
public interface U_NotifyListener {
    void notifySuccess(BleDevice device);
    void notifyFail(String des);
    void characteristicChange(String mac,byte[] data);
}
