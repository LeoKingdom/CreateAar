package com.ly.bluetoothhelper.callbacks.upgrade_callback;

import com.ly.bluetoothhelper.callbacks.BaseBleCallback;
import com.ly.bluetoothhelper.callbacks.BaseCallback;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/15 14:22
 * version: 1.0
 */
public abstract class NotifyCallback extends BaseBleCallback {
    public  void characteristicChange(int action,byte[] backBytes){}
    public void deviceReconn(){}
}
