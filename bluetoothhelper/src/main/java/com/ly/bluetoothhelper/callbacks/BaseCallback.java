package com.ly.bluetoothhelper.callbacks;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/15 14:13
 * version: 1.0
 */
public interface BaseCallback {
    void success();
    void fail(Object o);
    void noDevice();
}
