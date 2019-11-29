package com.ly.bluetoothhelper.callbacks;

import android.bluetooth.BluetoothGatt;

import fastble.data.BleDevice;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/11/27 18:09
 * version: 1.0
 */
public class VirtualLeashCallback {

    public interface OnScanStartListener {
        void scanStart();
    }

    public interface OnScanFinishListener {
        void scanFinish(BleDevice bleDevice);
    }

    public interface OnConnectSuccessListener {
        void connectSuccess(BleDevice bleDevice, BluetoothGatt gatt);
    }

    public interface OnConnectFailListener {
        void connectFail(BleDevice bleDevice, String description);
    }

    public interface OnDisconnectListener {
        void disconnect(boolean isActiveDisConnect, BleDevice bleDevice, BluetoothGatt gatt);
    }

    public interface OnReconnectSuccessListener {
        void reconnectSuccess(BleDevice bleDevice);
    }

    public interface OnReconnectFailListener {
        void reconnectFail(BleDevice bleDevice);
    }

    public interface OnDeviceSelfDisableListener {
        void deviceSelfDisable(String mac);
    }

    public interface OnDeviceAwayListener {
        void deviceAway(String address, String name);
    }

    public interface NotifySuccessListener {
        void notifySuccess(BleDevice device);
    }

    public interface NotifyFailListener {
        void notifyFail(BleDevice device, String err);
    }

    public interface NotifyCharacteristicChangeListener {
        void characteristicChange(String mac, byte[] data);
    }

    public interface WriteSuccessListener {
        void writeSuccess(int current, int total, byte[] packet);
    }

    public interface WriteFailListener {
        void writeFail(String err);
    }
}
