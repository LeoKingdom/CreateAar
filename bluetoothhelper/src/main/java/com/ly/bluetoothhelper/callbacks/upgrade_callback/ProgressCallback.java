package com.ly.bluetoothhelper.callbacks.upgrade_callback;

import com.ly.bluetoothhelper.callbacks.BaseBleCallback;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/15 13:26
 * version: 1.0
 */
public abstract class ProgressCallback extends BaseBleCallback {
    public  void setMax(int max){}
    public  void setProgress(float progress,int currentPacket,int totalFrame,int currentFrame,int currentBin,int totalBin){}
    public void setMaxFrame(int maxFrame){}
    public void setFrameProgress(float frameProgress,int totalFrame,int currentFrame,int currentBin,int totalBin){}
}
