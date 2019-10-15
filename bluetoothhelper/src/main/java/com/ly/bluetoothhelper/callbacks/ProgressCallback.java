package com.ly.bluetoothhelper.callbacks;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/15 13:26
 * version: 1.0
 */
public interface ProgressCallback extends BaseCallback{
    void setMax(int max);
    void setProgress(float progress,int currentPacket,int currentFrame);
}
