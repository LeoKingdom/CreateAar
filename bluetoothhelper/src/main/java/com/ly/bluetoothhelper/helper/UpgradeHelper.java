package com.ly.bluetoothhelper.helper;

import android.app.Application;
import android.bluetooth.BluetoothGatt;

import com.ly.bluetoothhelper.callbacks.base_callback.ConnectCallback;
import com.ly.bluetoothhelper.callbacks.base_callback.NotifyOpenCallback;
import com.ly.bluetoothhelper.callbacks.base_callback.ScanConnectCallback;
import com.ly.bluetoothhelper.callbacks.base_callback.WriteCallback;
import com.ly.bluetoothhelper.callbacks.upgrade_callback.U_NotifyListener;
import com.ly.bluetoothhelper.callbacks.upgrade_callback.U_ScanConnListener;
import com.ly.bluetoothhelper.callbacks.upgrade_callback.U_WriteListener;

import fastble.data.BleDevice;
import fastble.exception.BleException;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/12/12 17:45
 * version: 1.0
 */
public class UpgradeHelper extends BleBaseHelper{
    protected final int CONNCTED=0;//设备已连接,virtual leash逻辑实现
    protected final int SCAN_AND_CONN=1; //扫描并连接
    protected final int CONN_FROM_BOND=2; //从绑定设备列表中连接
    protected BleDevice mDevice;

    public boolean isNextPacketSuccess() {
        return nextPacketSuccess;
    }

    public void setNextPacketSuccess(boolean nextPacketSuccess) {
        this.nextPacketSuccess = nextPacketSuccess;
    }

    private boolean nextPacketSuccess=true;
    public int getIntervalTime() {
        return intervalTime;
    }

    public void setIntervalTime(int intervalTime) {
        this.intervalTime = intervalTime;
    }

    private int intervalTime=20;
    public UpgradeHelper(Application application) {
        super(application);
    }
    @Override
    public void init() {
        String[] uuids = {"00005500-d102-11e1-9b23-00025b00a5a5", "00005501-d102-11e1-9b23-00025b00a5a5"};
        setService_UUID(uuids[0]).setRead_UUID(uuids[1]).setWrite_UUID(uuids[1]).setNotify_UUID(uuids[1]);
    }

    public boolean deviceIsBond(String mac){
        return isBonded(mac);
    }

    public void scanAndConnDevice(String mac, U_ScanConnListener uScanConnListener){
        if (isConnected(mac)){
            BleDevice device=getConnectDevice(mac);
            this.mDevice=device;
            uScanConnListener.connSuccess(CONNCTED,device);
        }else {
            scanAndConnect(true, mac, "", new ScanConnectCallback() {


                @Override
                public void onScanFinished(BleDevice bleDevice) {
                    uScanConnListener.scanFinish(bleDevice);
                }

                @Override
                public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                    uScanConnListener.connSuccess(SCAN_AND_CONN,bleDevice);
                    mDevice=bleDevice;
                }

                @Override
                public void onConnectFailed(BleDevice bleDevice, String description) {
                    uScanConnListener.connFail(bleDevice,description);
                }

                @Override
                public void onDisconnect(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt) {
                    uScanConnListener.disconnDevice(isActiveDisConnected,device);
                }

            });
        }
    }

    public void connDevice(String mac,U_ScanConnListener uScanConnListener){
        connect(mac, new ConnectCallback() {
            @Override
            public void onConnectSuccess(BleDevice device, BluetoothGatt gatt) {
                uScanConnListener.connSuccess(CONN_FROM_BOND,device);
            }

            @Override
            public void onConnectFail(BleDevice device, BleException e) {
                uScanConnListener.connFail(device,e.getDescription());
            }

            @Override
            public void onDisconnect(boolean isActiveDisConnected, BleDevice device) {
                uScanConnListener.disconnDevice(isActiveDisConnected,device);
            }
        });
    }

    public void openNotify( U_NotifyListener uNotifyListener){
        setNotify(mDevice, new NotifyOpenCallback() {
            @Override
            public void onNotifySuccess(BleDevice device) {
                uNotifyListener.notifySuccess(device);
            }

            @Override
            public void onNotifyFailed(BleException e) {
                uNotifyListener.notifyFail(e.getDescription());
            }

            @Override
            public void onCharacteristicChanged(String mac, byte[] data) {
                uNotifyListener.characteristicChange(mac,data);
            }
        });
    }

    public void writeData(byte[] data, U_WriteListener uWriteListener){
        writeCharacteristic(mDevice, data, isNextPacketSuccess(), getIntervalTime(), new WriteCallback() {
            @Override
            public void writeSuccess(int actionType, int current, int total, byte[] justWrite) {
                uWriteListener.writeSuccess(total,current,justWrite);
            }

            @Override
            public void error(String err) {
                super.error(err);
                uWriteListener.writeFail(err);
            }
        });
    }

}
