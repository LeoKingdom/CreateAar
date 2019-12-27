package com.ly.bluetoothhelper.callbacks.base_callback;

import com.ly.bluetoothhelper.callbacks.BaseBleCallback;

import fastble.exception.BleException;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/11/26 18:55
 * version: 1.0
 */
public abstract class ReadCallback extends BaseBleCallback {
    public abstract void onReadSuccess(byte[] data);

    public abstract void onReadFailure(BleException exception);
}
