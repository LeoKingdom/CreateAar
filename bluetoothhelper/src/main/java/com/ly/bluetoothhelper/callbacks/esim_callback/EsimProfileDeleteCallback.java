package com.ly.bluetoothhelper.callbacks.esim_callback;

import com.ly.bluetoothhelper.callbacks.BaseBleCallback;

public abstract class EsimProfileDeleteCallback extends BaseBleCallback {
    /**
     * 删除结果,非0为失败
     * @param isActivated 是否成功
     */
    public abstract void deleteResult(boolean isActivated);
}
