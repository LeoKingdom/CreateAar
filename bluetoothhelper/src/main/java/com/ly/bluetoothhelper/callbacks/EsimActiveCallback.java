package com.ly.bluetoothhelper.callbacks;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/22 15:46
 * version: 1.0
 * <p>
 * 激活esim卡回调
 */
public abstract class EsimActiveCallback extends BaseBleCallback{
    /**
     * 蓝牙地址非法
     */
    public  void macInvalidate(){}

    /**
     * 未找到设备
     */
   public void deviceNotFound(){}

    /**
     * 文件错误
     */
   public void fileError(){}

}
