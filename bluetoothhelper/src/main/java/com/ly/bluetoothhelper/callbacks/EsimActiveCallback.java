package com.ly.bluetoothhelper.callbacks;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/22 15:46
 * version: 1.0
 * <p>
 * 激活esim卡回调
 */
public interface EsimActiveCallback {
    void noDevice();

    void activeSuccess(Object object);

    void activeFail(String error);
}
