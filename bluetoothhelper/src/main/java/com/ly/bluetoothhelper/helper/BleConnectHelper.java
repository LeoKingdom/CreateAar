package com.ly.bluetoothhelper.helper;

import android.app.Application;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import com.clj.fastble.BleManager;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.BleLog;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/6/5 10:25
 * version: 1.0
 */
public class BleConnectHelper {
    //action String
    public static final String BLE_SCAN_START = "ble.scan.start";
    public static final String BLE_SCAN_FINISH = "ble.scan.finish";
    public static final String BLE_CONNECT_SUCCESS = "ble.conn.success";
    public static final String BLE_CONNECT_FAIL = "ble.conn.fail";
    public static final String BLE_DISCONNECT = "ble.disconnect";
    public static final String BLE_NOT_FOUND = "ble.not.found";
    public static final int START_TIMER = 0;

    private static BleConnectHelper bleConnectHelper;
    private BleManager bleManager;
    private BluetoothHelper bluetoothHelper;
    private Map<String, BleDevice> connDeviceMap = new HashMap<>();//第一个参数是address,第二个参数是BleDevice，已连接的设备
    private Map<String, Integer> readRssiMap = new HashMap<>();//第一个参数是address,第二个参数是rrsi值，读rssi的设备
    private Map<String, String> reconnDeviceMap = new HashMap<>();//第一个参数是address,第二个参数是name,需要重连的设备
    private Set<String> needConnectMap = new HashSet<>();//需要连接的设备
    private ReconnHandler reConnHandler = new ReconnHandler(this);
    private Timer timer;
    private TimerTask timerTask;
    private double maxDistance;

    private BleConnectHelper() {
    }

    public Map<String, String> getReconnDeviceMap() {
        return reconnDeviceMap;
    }

    public Map<String, Integer> getReadRssiMap() {
        return readRssiMap;
    }

    /**
     * 初始化蓝牙配置
     *
     * @param application
     * @return
     */
    public BleConnectHelper init(Application application) {
        bluetoothHelper = new BluetoothHelper(application);
        bleManager = bluetoothHelper.getBleManager();
        return this;
    }

    /**
     * 初始化蓝牙配置
     *
     * @param application
     * @param reconnectCount 重连次数
     * @return
     */
    public BleConnectHelper init(Application application, int reconnectCount) {
        bluetoothHelper = new BluetoothHelper(application, reconnectCount);
        bleManager = bluetoothHelper.getBleManager();
        return this;
    }

    /**
     * 初始化蓝牙配置
     *
     * @param application
     * @param reconnectCount 重连次数
     * @param maxDistance    最大连接距离
     * @return
     */
    public BleConnectHelper init(Application application, int reconnectCount, double maxDistance) {
        bluetoothHelper = new BluetoothHelper(application, reconnectCount);
        bleManager = bluetoothHelper.getBleManager();
        this.maxDistance = maxDistance;
        return this;
    }

    /**
     * 获取单例对象
     *
     * @return 返回单例对象
     */
    public static BleConnectHelper getInstance() {
        if (bleConnectHelper == null) {
            synchronized (BleConnectHelper.class) {
                if (bleConnectHelper == null) {
                    bleConnectHelper = new BleConnectHelper();
                }
            }
        }
        return bleConnectHelper;
    }

    public void openVirtualLeash(boolean isFuzzzy,String address,String name) {
        needConnectMap.add(address);
        openVirtualLeash(address, name);
    }


    /**
     * 扫描并连接
     *
     * @param address 蓝牙地址
     * @param name    蓝牙名称
     */
    public void openVirtualLeash(String address, String name) {
        removeConnectDevice(address);//先移除掉，因为重新开始了
        removeReConnectDevice(address);//先移除掉，因为重新开始了
        bluetoothHelper.setCharacteristicChangeListener((gatt, characteristic) -> getCharacteristicChange(gatt, characteristic));
        bluetoothHelper.scanAndConnect(true, address, name, new BluetoothHelper.BleHandleListener() {
            @Override
            public void onScanStarted(boolean success) {
                if (success) {
                    getScanStartNext();
                }
            }

            @Override
            public void onScanFinished(BleDevice bleDevice) {
                BleLog.e("device was found: " + bleDevice);
                if (needConnectMap.contains(address)) {
                    getScanFinishNext(bleDevice);
                    checkRssi(bleDevice, address, name);//检查rssi
                    if (bleDevice == null && !connDeviceMap.containsKey(address)) {//如果没扫描到，过段时间继续扫描
                        addReConnectDevice(address, name);//加入重连列表中
                        startReconnect(address, 2000);//重新连接
                    }
                }
            }

            @Override
            public void onConnectFailed(BleDevice bleDevice, String description) {
                getConnectFailNext(bleDevice, description);
                addReConnectDevice(bleDevice.getMac(), bleDevice.getName());//加入重连列表中
                startReconnect(bleDevice.getMac(), 2000);//重新连接
            }

            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                if (isReConnectDevice(bleDevice.getMac())) {
                    getReconnectSuccessNext(bleDevice);
                } else {
                    getConnectSuccessNext(bleDevice, gatt);
                }
                addConnectDevice(bleDevice.getMac(), bleDevice);//加入已连接的列表中
                removeReConnectDevice(bleDevice.getMac());//从断开连接的列表中移除
                readRssi();//读rssi
            }

            @Override
            public void onDisconnect(boolean isActiveDis, BleDevice device, BluetoothGatt bluetoothGatt) {
                Log.e("disConn----", device.getMac() + "/" + device.getName() + "/" + device.getRssi());
                getDisconnectNext(isActiveDis, device, bluetoothGatt);
                removeConnectDevice(device.getMac());//从已连接的列表中移除
                if (!isActiveDis) {//不是主动断开的
                    addReConnectDevice(device.getMac(), device.getName());//加入重连列表中
                    startReconnect(device.getMac(), 100);//开启重连
                } else {//主动断开连接的话，移除rssimap和reconnectmap
                    removeReConnectDevice(device.getMac());
                    removeRssiDevice(device.getMac());
                }
            }

            @Override
            public void onBleDisable() {
                //蓝牙断开连接了
                getDeviceSelfDisableNext();
            }
        });
    }


    /**
     * 检查rssi来判断是否走远了
     *
     * @param bleDevice 蓝牙bleDevice
     * @param address   蓝牙mac地址
     */
    private void checkRssi(BleDevice bleDevice, String address, String name) {
        if (bleDevice == null && !connDeviceMap.containsKey(address)) {//如果扫描不到了
            if (readRssiMap.containsKey(address)) {//之前有存rssi的话，说明之前扫描到过
                Integer valueRssi = readRssiMap.get(address);
                double distance = getDistance(valueRssi);//判断距离
                if (distance > maxDistance) {//如果距离大于10米了，那就走远了
                    getDeviceAwayNext(address, name);
                }
            }
        } else {//说明还能扫描到
            readRssiMap.put(bleDevice.getMac(), bleDevice.getRssi());
        }
    }

    /**
     * 开启读rssi
     */
    public void readRssi() {
        clearRssiTimer();//清除之前的
        timer = new Timer();
        timerTask = new TimerTask() {
            @Override
            public void run() {
                if (connDeviceMap.size() > 0) {//只检查已成功连接的设备
                    for (Map.Entry<String, BleDevice> device : connDeviceMap.entrySet()) {
                        bluetoothHelper.readRssi(device.getValue(), new BluetoothHelper.RemoteRssiListener() {
                            @Override
                            public void onRemoteRssi(int rssi) {
                                Log.e("rssi----", rssi + "/" + getDistance(rssi));
                                readRssiMap.put(device.getKey(), rssi);
                            }

                            @Override
                            public void onRssiFailure(BleException exception) {

                            }
                        });
                    }
                }
            }
        };
        timer.schedule(timerTask, 0, 5000);
    }

    /**
     * 清除timer
     */
    public void clearRssiTimer() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    /**
     * 通过rssi来算出距离
     *
     * @param rssi 设备的rssi值
     * @return 返回距离
     */
    private double getDistance(int rssi) {
        int iRssi = Math.abs(rssi);
        double power = (iRssi - 72) / (10 * 2.0);
        return Math.pow(10, power);
    }


    /**
     * 开始重连
     *
     * @param address      蓝牙mac地址
     * @param delaymiillis 延时时间
     */
    private void startReconnect(String address, long delaymiillis) {
        Message message = reConnHandler.obtainMessage(START_TIMER);
        message.obj = address;
        reConnHandler.sendMessageDelayed(message, delaymiillis);
    }

    //重连的hanlder
    public static class ReconnHandler extends Handler {
        private WeakReference<BleConnectHelper> reconnectHelperWeakReference;

        public ReconnHandler(BleConnectHelper reconnectHelper) {
            reconnectHelperWeakReference = new WeakReference<>(reconnectHelper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            BleConnectHelper connect = reconnectHelperWeakReference.get();
            switch (msg.what) {
                case START_TIMER:
                    String key = (String) msg.obj;//key为蓝牙地址
                    if (key != null) {
                        connect.reconnListening(key);
                    }
                    break;
            }
        }
    }

    /**
     * 开启重连
     *
     * @param key 蓝牙地址
     */
    private void reconnListening(String key) {
        Map<String, String> reconnDeviceMap = getReconnDeviceMap();
        Set<Map.Entry<String, String>> keys = reconnDeviceMap.entrySet();
        if (reconnDeviceMap.size() > 0) {//遍历需要重连的设备
            for (Map.Entry<String, String> entry : keys) {
                if (entry.getKey().equals(key)) {
                    openVirtualLeash(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * 加入重连的设备中
     *
     * @param address 蓝牙地址
     * @param name    蓝牙名称
     */
    private void addReConnectDevice(String address, String name) {
        reconnDeviceMap.put(address, name);
    }

    /**
     * 移除重连的设备
     *
     * @param address 蓝牙地址
     */
    public void removeReConnectDevice(String address) {
        reconnDeviceMap.remove(address);
    }

    /**
     * 检查是否在重连的列表中
     *
     * @param address 蓝牙地址
     * @return
     */
    public boolean isReConnectDevice(String address) {
        return reconnDeviceMap.containsKey(address);
    }

    /**
     * 加入已连接的设备
     *
     * @param address   蓝牙地址
     * @param bleDevice 蓝牙设备
     */
    public void addConnectDevice(String address, BleDevice bleDevice) {
        connDeviceMap.put(address, bleDevice);
    }

    /**
     * 移除已连接的设备
     *
     * @param address 蓝牙地址
     */
    public void removeConnectDevice(String address) {
        connDeviceMap.remove(address);
    }

    public void removeRssiDevice(String address) {
        readRssiMap.remove(address);
    }

    public void removeDevice(String mac) {
        needConnectMap.remove(mac);
        readRssiMap.remove(mac);
        connDeviceMap.remove(mac);
        reconnDeviceMap.remove(mac);
    }

    //----------------对外listeners----------------------

    /**
     * 扫描开始监听
     */
    private OnScanStartListener scanStartListener;

    public void setScanStartListener(OnScanStartListener scanStartListener) {
        this.scanStartListener = scanStartListener;
    }

    public interface OnScanStartListener {
        void scanStart();
    }

    private void getScanStartNext() {
        if (scanStartListener != null) {
            scanStartListener.scanStart();
        }
    }

    /**
     * 扫描完成监听
     */
    private OnScanFinishListener scanFinishListener;

    public void setScanFinishListener(OnScanFinishListener scanFinishListener) {
        this.scanFinishListener = scanFinishListener;
    }

    public interface OnScanFinishListener {
        void scanFinish(BleDevice bleDevice);
    }

    private void getScanFinishNext(BleDevice bleDevice) {
        if (scanFinishListener != null) {
            scanFinishListener.scanFinish(bleDevice);
        }
    }


    /**
     * 连接成功监听
     */
    private OnConnectSuccessListener connectSuccessListener;

    public void setConnectSuccessListener(OnConnectSuccessListener connectSuccessListener) {
        this.connectSuccessListener = connectSuccessListener;
    }

    public interface OnConnectSuccessListener {
        void connectSuccess(BleDevice bleDevice, BluetoothGatt gatt);
    }

    private void getConnectSuccessNext(BleDevice bleDevice, BluetoothGatt gatt) {
        if (connectSuccessListener != null) {
            connectSuccessListener.connectSuccess(bleDevice, gatt);
        }
    }

    /**
     * 连接失败监听
     */
    private OnConnectFailListener connectFailListener;

    public void setConnectFailListener(OnConnectFailListener connectFailListener) {
        this.connectFailListener = connectFailListener;
    }

    public interface OnConnectFailListener {
        void connectFail(BleDevice bleDevice, String description);
    }

    private void getConnectFailNext(BleDevice bleDevice, String description) {
        if (connectFailListener != null) {
            connectFailListener.connectFail(bleDevice, description);
        }
    }

    /**
     * 设备间断开蓝牙连接监听
     */
    private OnDisconnectListener disconnectListener;

    public void setDisconnectListener(OnDisconnectListener disconnectListener) {
        this.disconnectListener = disconnectListener;
    }

    public interface OnDisconnectListener {
        void disconnect(boolean isActiveDisConnect, BleDevice bleDevice, BluetoothGatt gatt);
    }

    private void getDisconnectNext(boolean isActiveDisConnect, BleDevice bleDevice, BluetoothGatt gatt) {
        if (disconnectListener != null) {
            disconnectListener.disconnect(isActiveDisConnect, bleDevice, gatt);
        }
    }


    /**
     * 重连成功监听
     */
    private OnReconnectSuccessListener reconnectSuccessListener;

    public void setReconnectSuccessListener(OnReconnectSuccessListener reconnectSuccessListener) {
        this.reconnectSuccessListener = reconnectSuccessListener;
    }

    public interface OnReconnectSuccessListener {
        void reconnectSuccess(BleDevice bleDevice);
    }

    private void getReconnectSuccessNext(BleDevice bleDevice) {
        if (reconnectSuccessListener != null) {
            reconnectSuccessListener.reconnectSuccess(bleDevice);
        }
    }


    /**
     * 重连失败监听
     */
    private OnReconnectFailListener reconnectFailListener;

    public void setReconnectFailListener(OnReconnectFailListener reconnectFailListener) {
        this.reconnectFailListener = reconnectFailListener;
    }

    public interface OnReconnectFailListener {
        void reconnectFail(BleDevice bleDevice);
    }

    private void getReconnectFailNext(BleDevice bleDevice) {
        if (reconnectFailListener != null) {
            reconnectFailListener.reconnectFail(bleDevice);
        }
    }


    /**
     * 自身设备关闭蓝牙监听
     */
    private OnDeviceSelfDisableListener deviceSelfDisableListener;

    public void setDeviceSelfDisableListener(OnDeviceSelfDisableListener deviceSelfDisableListener) {
        this.deviceSelfDisableListener = deviceSelfDisableListener;
    }

    public interface OnDeviceSelfDisableListener {
        void deviceSelfDisable();
    }

    private void getDeviceSelfDisableNext() {
        if (deviceSelfDisableListener != null) {
            deviceSelfDisableListener.deviceSelfDisable();
        }
    }

    /**
     * 设备是否走远监听
     */
    private OnDeviceAwayListener deviceAwayListener;

    public void setDeviceAwayListener(OnDeviceAwayListener deviceAwayListener) {
        this.deviceAwayListener = deviceAwayListener;
    }

    public interface OnDeviceAwayListener {
        void deviceAway(String address, String name);
    }

    private void getDeviceAwayNext(String address, String name) {
        if (deviceAwayListener != null) {
            deviceAwayListener.deviceAway(address, name);
        }
    }

    /**
     * 设备返回来的数据变化
     *
     * @param charactoristicChangeListener
     */
    public void setCharacteristicChangeListener(OnCharacteristicChangeListener charactoristicChangeListener) {
        this.onCharacteristicChangeListener = charactoristicChangeListener;
    }

    private OnCharacteristicChangeListener onCharacteristicChangeListener;

    public interface OnCharacteristicChangeListener {
        void onCharacteristicChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
    }

    private void getCharacteristicChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (onCharacteristicChangeListener != null) {
            onCharacteristicChangeListener.onCharacteristicChange(gatt, characteristic);
        }
    }

}
