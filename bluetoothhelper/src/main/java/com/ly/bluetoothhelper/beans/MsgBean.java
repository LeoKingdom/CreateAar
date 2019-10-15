package com.ly.bluetoothhelper.beans;

import android.bluetooth.BluetoothGatt;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/24 14:56
 * version: 1.0
 */
public class MsgBean {
    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    String msg;
    Object object;
    BluetoothGatt gatt;

    public BluetoothGatt getGatt() {
        return gatt;
    }

    public void setGatt(BluetoothGatt gatt) {
        this.gatt = gatt;
    }

    public MsgBean(){}
    public MsgBean(String msg, Object object) {
        this.msg = msg;
        this.object = object;
    }
    public MsgBean(String msg, BluetoothGatt gatt, Object object) {
        this.msg = msg;
        this.object = object;
        this.gatt=gatt;
    }
}
