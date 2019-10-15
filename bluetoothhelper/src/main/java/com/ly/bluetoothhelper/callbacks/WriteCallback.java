package com.ly.bluetoothhelper.callbacks;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/15 14:25
 * version: 1.0
 */
public interface WriteCallback extends BaseCallback{
    void writeSuccess(int actionType,int current, int total, byte[] justWrite);
}
