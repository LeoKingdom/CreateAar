package com.ly.bluetoothhelper.beans;

import android.bluetooth.BluetoothGatt;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/24 14:56
 * version: 1.0
 */
public class MsgBean {
    private String action1;
    private String action2;
    private String macAddress;
    private String tName;
    private String msg;
    private Object object;
    private BluetoothGatt gatt;
    public String gettName() {
        return tName;
    }

    public void settName(String tName) {
        this.tName = tName;
    }
    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public MsgBean() {
    }

    public MsgBean(String msg, Object object) {
        this.msg = msg;
        this.object = object;
    }

    public MsgBean(String msg, BluetoothGatt gatt, Object object) {
        this.msg = msg;
        this.object = object;
        this.gatt = gatt;
    }

    public MsgBean(String action1, String action2, String macAddress, String tName, String msg, Object object, BluetoothGatt gatt) {
        this.action1 = action1;
        this.action2 = action2;
        this.macAddress = macAddress;
        this.tName = tName;
        this.msg = msg;
        this.object = object;
        this.gatt = gatt;
    }

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

    public String getAction2() {
        return action2;
    }
    public void setAction2(String action2) {
        this.action2 = action2;
    }
    public String getAction1() {
        return action1;
    }
    public void setAction1(String action) {
        this.action1 = action;
    }
    public BluetoothGatt getGatt() {
        return gatt;
    }
    public void setGatt(BluetoothGatt gatt) {
        this.gatt = gatt;
    }

}
