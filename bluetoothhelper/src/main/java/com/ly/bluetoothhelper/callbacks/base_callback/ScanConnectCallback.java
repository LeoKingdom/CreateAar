package com.ly.bluetoothhelper.callbacks.base_callback;

import android.bluetooth.BluetoothGatt;

import com.ly.bluetoothhelper.callbacks.BaseBleCallback;

import fastble.data.BleDevice;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/11/26 17:53
 * version: 1.0
 */
public abstract class ScanConnectCallback extends BaseBleCallback {
    public void onScanStarted(boolean success) {
    }

    public abstract void onScanFinished(BleDevice bleDevice);

    public abstract void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status);

    public abstract void onConnectFailed(BleDevice bleDevice, String description);

    public abstract void onDisconnect(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt);

    public void onBleDisable() {
    }
}
