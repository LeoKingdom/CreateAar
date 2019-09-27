package com.ly.createaar;

import android.app.Application;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.CountDownTimer;
import android.os.Message;
import android.util.Log;

import com.clj.fastble.BleManager;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.BleLog;
import com.ly.bluetoothhelper.helper.BluetoothHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/6/5 10:25
 * version: 1.0
 */
public class VirtualLeashHelper {
    private static VirtualLeashHelper virtualLeashHelper;

    public BluetoothHelper getBluetoothHelper() {
        return bluetoothHelper;
    }

    private BluetoothHelper bluetoothHelper;
    private boolean reconnSate = false;

    public BleManager getBleManager() {
        return bleManager;
    }

    private BleManager bleManager;
    private Map<String, BleDevice> disConnDeviceMap = new HashMap<>();

    public Map<String, BleDevice> getConnDeviceMap() {
        return connDeviceMap;
    }

    private Map<String, BleDevice> connDeviceMap = new HashMap<>();
    private Map<String, Integer> connDeviceRssiMap = new HashMap<>();

    public Map<String, BleDevice> getDisConnDeviceMap() {
        return disConnDeviceMap;
    }

    private VirtualLeashHelper() {


    }

    public VirtualLeashHelper init(Application application) {
        bluetoothHelper = new BluetoothHelper(application);
        bleManager = bluetoothHelper.getBleManager();
        return this;
    }

    public static VirtualLeashHelper getInstance() {
        if (virtualLeashHelper == null) {
            synchronized (VirtualLeashHelper.class) {
                if (virtualLeashHelper == null) {
                    virtualLeashHelper = new VirtualLeashHelper();
                }
            }
        }
        return virtualLeashHelper;
    }

    /**
     *
     * @param isFuzzy 是否模糊搜索,基本都是true
     * @param address 蓝牙mac地址
     * @param name 蓝牙名称
     *             注意:
     *             当通过名称搜索和连接时,address可以为null
     *             当通过address搜索和连接时,name不能为null,否则会报空指针异常,可以是""
     */

    public void openVirtualLeash(boolean isFuzzy, String address, String name) {
        bluetoothHelper.setCharacteristicChangeListener(characteristicChangeListener);
        bluetoothHelper.scanAndConnect(isFuzzy, address, name,bleHandleListener);
    }

    private BluetoothHelper.CharacteristicChangeListener characteristicChangeListener= new BluetoothHelper.CharacteristicChangeListener() {
        @Override
        public void onCharacteristicChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (onCharacteristicChangeListener!=null){
                onCharacteristicChangeListener.onCharacteristicChange(gatt,characteristic);
            }
        }
    };

    BluetoothHelper.BleHandleListener bleHandleListener = new BluetoothHelper.BleHandleListener() {
        @Override
        public void onScanStarted(boolean success) {
            if (success && scanStartListener != null) {
                scanStartListener.scanStart();
            }
        }

        @Override
        public void onScanning() {

        }

        @Override
        public void onScanFinished(BleDevice bleDevice) {
            if (scanFinishListener != null) {
                scanFinishListener.scanFinish(bleDevice);
            }
            if (bleDevice == null) {
                BleLog.e("no device was found");
                if (reconnSate && reconnectFailListener != null) {
                    reconnectFailListener.reconnectFail(null);
                }
            } else {
                BleLog.e("scanDevice---: " + bleDevice.getName() + "/" + bleDevice.getMac());
            }
        }

        @Override
        public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
            connDeviceMap.put(bleDevice.getName(), bleDevice);
            connDeviceRssiMap.put(bleDevice.getName(), bleDevice.getRssi());
            readRssi();
            if (connectSuccessListener != null) {
                connectSuccessListener.connectSuccess(bleDevice, gatt);
            }
            if (reconnSate) {
                if (disConnDeviceMap.containsKey(bleDevice.getName())) {
                    reconnSate = false;
                    reconnectSuccessListener.reconnectSuccess(bleDevice);
                }
                for (Map.Entry<String, BleDevice> entry : disConnDeviceMap.entrySet()) {
                    //测试使用name,因为mac地址一直改变
//                if (entry.getKey().equals(bleDevice.getMac())){
//                    disConnDeviceMap.remove(entry.getKey());
//                }
                    if (entry.getKey().equals(bleDevice.getName())) {
                        disConnDeviceMap.remove(entry.getKey());
                    }
                }
                ReconnectHelper.ReconnHandler handler = ReconnectHelper.getInstance().getReConnHandler();
                Message message = handler.obtainMessage();
                message.what = 1;
                message.obj = bleDevice.getName();
                handler.sendMessage(message);
            }
        }

        @Override
        public void onConnectFailed(BleDevice bleDevice, String description) {
            if (connectFailListener != null) {
                connectFailListener.connectFail(bleDevice, description);
            }

            if (reconnSate && disConnDeviceMap.size() > 0) {
                if (disConnDeviceMap.containsKey(bleDevice.getName())) {
                    reconnectFailListener.reconnectFail(bleDevice);
                }
                double distance = getDistance(connDeviceRssiMap.get(bleDevice.getName()));
                if (bleDevice.getRssi() == 0 || distance > 10) {
                    deviceAwayListener.deviceAway(bleDevice);
                }
                Log.e("distance---", distance + "");
            }
        }

        @Override
        public void onDisconnect(BleDevice device, BluetoothGatt gatt) {
            Log.e("disConn----", device.getMac() + "/" + device.getName() + "/" + device.getRssi());
            reconnSate = true;
            //            disConnDeviceMap.put(device.getMac(), device);
            disConnDeviceMap.put(device.getName(), device); //测试
            bluetoothHelper.scanAndConnect(false, null, device.getName(),bleHandleListener);
            ReconnectHelper.ReconnHandler handler = ReconnectHelper.getInstance().getReConnHandler();
            handler.sendEmptyMessageDelayed(0, bleManager.getReConnectCount() * bleManager.getReConnectInterval());
            for (Map.Entry<String, BleDevice> entry : connDeviceMap.entrySet()) {
                //测试使用name,因为mac地址一直改变
//                if (entry.getKey().equals(bleDevice.getMac())){
//                    disConnDeviceMap.remove(entry.getKey());
//                }
                if (entry.getKey().equals(device.getName())) {
                    connDeviceMap.remove(entry.getKey());
                }
            }


            if (!bleManager.isBlueEnable()) {
                deviceSelfDisableListener.deviceSelfDisable();
            }

        }
    };

    private CountDownTimer rssiTimer;

    private void readRssi() {
        rssiTimer = new CountDownTimer(600000, 5000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (connDeviceMap.size() > 0) {
                    for (Map.Entry<String, BleDevice> device : connDeviceMap.entrySet()) {
                        bluetoothHelper.readRssi(device.getValue(), new BluetoothHelper.RemoteRssiListener() {
                            @Override
                            public void onRemoteRssi(int rssi) {
                                Log.e("rssi----", rssi + "/"+getDistance(rssi));
                                connDeviceRssiMap.put(device.getKey(), rssi);
                            }

                            @Override
                            public void onRssiFailure(BleException exception) {

                            }
                        });
                    }
                } else {
                    rssiTimer.cancel();
                }
            }

            @Override
            public void onFinish() {

            }
        }.start();

    }

    public double getDistance(int rssi) {
        int iRssi = Math.abs(rssi);
        double power = (iRssi - 72) / (10 * 2.0);
        return Math.pow(10, power);
    }

    public void setCharacteristicChangeListener(OnCharacteristicChangeListener charactoristicChangeListener) {
        this.onCharacteristicChangeListener = charactoristicChangeListener;
    }

    private OnCharacteristicChangeListener onCharacteristicChangeListener;

    public interface OnCharacteristicChangeListener{
        void onCharacteristicChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
    }

    private OnScanStartListener scanStartListener;

    public void setScanStartListener(OnScanStartListener scanStartListener) {
        this.scanStartListener = scanStartListener;
    }

    public interface OnScanStartListener {
        void scanStart();
    }


    private OnScanFinishListener scanFinishListener;

    public void setScanFinishListener(OnScanFinishListener scanFinishListener) {
        this.scanFinishListener = scanFinishListener;
    }

    public interface OnScanFinishListener {
        void scanFinish(BleDevice bleDevice);
    }

    private OnConnectSuccessListener connectSuccessListener;

    public void setConnectSuccessListener(OnConnectSuccessListener connectSuccessListener) {
        this.connectSuccessListener = connectSuccessListener;
    }

    public interface OnConnectSuccessListener {
        void connectSuccess(BleDevice bleDevice, BluetoothGatt gatt);
    }

    private OnConnectFailListener connectFailListener;

    public void setConnectFailListener(OnConnectFailListener connectFailListener) {
        this.connectFailListener = connectFailListener;
    }

    public interface OnConnectFailListener {
        void connectFail(BleDevice bleDevice, String description);
    }

    private OnDisconnectListener disconnectListener;

    public void setDisconnectListener(OnDisconnectListener disconnectListener) {
        this.disconnectListener = disconnectListener;
    }

    public interface OnDisconnectListener {
        void disconnect(BleDevice bleDevice, BluetoothGatt gatt);
    }

    private OnReconnectSuccessListener reconnectSuccessListener;

    public void setReconnectSuccessListener(OnReconnectSuccessListener reconnectSuccessListener) {
        this.reconnectSuccessListener = reconnectSuccessListener;
    }

    public interface OnReconnectSuccessListener {
        void reconnectSuccess(BleDevice bleDevice);
    }

    private OnReconnectFailListener reconnectFailListener;

    public void setReconnectFailListener(OnReconnectFailListener reconnectFailListener) {
        this.reconnectFailListener = reconnectFailListener;
    }

    public interface OnReconnectFailListener {
        void reconnectFail(BleDevice bleDevice);
    }

    private OnDeviceSelfDisableListener deviceSelfDisableListener;

    public void setDeviceSelfDisableListener(OnDeviceSelfDisableListener deviceSelfDisableListener) {
        this.deviceSelfDisableListener = deviceSelfDisableListener;
    }

    public interface OnDeviceSelfDisableListener {
        void deviceSelfDisable();
    }

    private OnDeviceAwayListener deviceAwayListener;

    public void setDeviceAwayListener(OnDeviceAwayListener deviceAwayListener) {
        this.deviceAwayListener = deviceAwayListener;
    }

    public interface OnDeviceAwayListener {
        void deviceAway(BleDevice bleDevice);
    }

}
