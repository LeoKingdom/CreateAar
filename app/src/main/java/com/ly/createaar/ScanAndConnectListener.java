package com.ly.createaar;

import com.clj.fastble.data.BleDevice;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/24 13:50
 * version: 1.0
 */
public interface ScanAndConnectListener {
    void scanFail();
    void scanSuccess(BleDevice bleDevice);
    void connetFail(BleDevice b);
    void connectSuccess(BleDevice bleDevice);
}
