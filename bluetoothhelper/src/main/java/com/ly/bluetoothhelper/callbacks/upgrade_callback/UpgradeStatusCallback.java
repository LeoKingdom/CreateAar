package com.ly.bluetoothhelper.callbacks.upgrade_callback;

import com.ly.bluetoothhelper.callbacks.BaseBleCallback;

import fastble.data.BleDevice;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/12/31 17:12
 * version: 1.0
 */
public abstract class UpgradeStatusCallback extends BaseBleCallback {
    public void deviceDisconn(BleDevice device,boolean isActive){}
    public void fileIsEmpty(){}
    public void connSuccess(BleDevice device){}
    public void reConnSuccess(BleDevice device){}
}
