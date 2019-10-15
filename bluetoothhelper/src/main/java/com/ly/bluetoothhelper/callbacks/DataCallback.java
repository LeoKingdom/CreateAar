package com.ly.bluetoothhelper.callbacks;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/15 14:59
 * version: 1.0
 */
public interface DataCallback {
    void nextFrame(int currentFrame,int totalFrame);
    void reSend();
    void done();
    void checkOutTime();
    void binChecking();
    void binCheckDone(boolean isBin);
}
