package com.ly.bluetoothhelper.callbacks;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/15 14:22
 * version: 1.0
 */
public interface NotifyCallback extends BaseCallback{
    void charactoristicChange(int action,byte[] backBytes);
    void deviceReconn();
}
