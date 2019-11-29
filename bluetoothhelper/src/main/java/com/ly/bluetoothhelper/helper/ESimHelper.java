package com.ly.bluetoothhelper.helper;


import android.app.Application;
import android.bluetooth.BluetoothGatt;
import android.text.TextUtils;

import com.ly.bluetoothhelper.callbacks.EsimActiveCallback;
import com.ly.bluetoothhelper.callbacks.NotifyOpenCallback;
import com.ly.bluetoothhelper.callbacks.ScanConnectCallback;
import com.ly.bluetoothhelper.callbacks.WriteCallback;
import com.ly.bluetoothhelper.utils.OrderSetUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;

import java.io.File;
import java.io.IOException;

import fastble.data.BleDevice;
import fastble.exception.BleException;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/15 11:11
 * version: 1.0
 * <p>
 * esim卡工具类,主要为外围设备和中心设备围绕esim展开的交互(蓝牙)
 */
public class ESimHelper extends BleBaseHelper {
    private String[] uuids = {"00005500-d102-11e1-9b23-00025b00a5a5", "00005501-d102-11e1-9b23-00025b00a5a5"};
    private Application mApp;
    private EsimActiveCallback esimActiveCallback;
    private String mMac;
    private File mFile;
    private BleDevice bleDevice;
    private WriteCallback writeListener = new WriteCallback() {
        @Override
        public void writeSuccess(int actionType, int current, int total, byte[] justWrite) {

        }

        @Override
        public void error(String err) {

        }
    };
    private NotifyOpenCallback notifyListener = new NotifyOpenCallback() {
        @Override
        public void onNotifySuccess(BleDevice device) {
            if (device.getMac().equalsIgnoreCase(mMac)) {
                writeCharacteristic(device, OrderSetUtils.ESIM_PROFILE_DOWNLOAD, writeListener);
            }
        }

        @Override
        public void onNotifyFailed(BleException e) {

        }

        @Override
        public void onCharacteristicChanged(String mac, byte[] data) {
            if (mac.equalsIgnoreCase(mMac)) {
                if (data.length > 7) {
                    byte moduleId = data[5];
                    byte eventId = data[6];
                }
            }
        }
    };
    private ScanConnectCallback handleListener = new ScanConnectCallback() {
        @Override
        public void onScanStarted(boolean success) {

        }

        @Override
        public void onScanFinished(BleDevice bleDevice) {
            if (bleDevice == null) {
                if (esimActiveCallback != null) {
                    esimActiveCallback.deviceNotFound();
                }
            }
        }

        @Override
        public void onConnectSuccess(BleDevice device, BluetoothGatt gatt, int status) {
            if (device != null) {
                bleDevice = device;
                setNotify(device, notifyListener);
            }
        }

        @Override
        public void onConnectFailed(BleDevice bleDevice, String description) {

        }

        @Override
        public void onDisconnect(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt) {

        }

        @Override
        public void onBleDisable() {

        }
    };

    public ESimHelper(Application application) {
        super(application);
    }


    /**
     * 激活esim的方法
     *
     * @param mac 设备的蓝牙地址
     */
    public void esimActive(String mac) {
        this.mMac = mac;
        if (TextUtils.isEmpty(mac)) {
            if (esimActiveCallback != null) {
                esimActiveCallback.macInvalidate();
            }
        } else {
            BleDevice device = getConnectDevice(mac);
            if (device == null) {
                if (esimActiveCallback != null) {
                    esimActiveCallback.deviceNotConnect();
                }
                scanAndConnect(false, mac, "", handleListener);
            } else {
                this.bleDevice = device;
                setNotify(device, notifyListener);
            }
        }
    }

    private void esimActive() {
        if (bleDevice == null) {
            if (esimActiveCallback != null) {
                esimActiveCallback.deviceNotFound();
            }
            return;
        }
        try {
            byte[] datas = TransformUtils.fileToByte(mFile);
            writeCharacteristic(bleDevice, datas, writeListener);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void init() {
//        uuids();
        //默认初始化的uuid
        setService_UUID(uuids[0]).setRead_UUID(uuids[1]).setWrite_UUID(uuids[1]).setNotify_UUID(uuids[1]);
    }

    public void setErrorCallListener(EsimActiveCallback esimActiveCallback) {
        this.esimActiveCallback = esimActiveCallback;
    }

}
