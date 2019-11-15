package com.ly.bluetoothhelper.helper;

import android.app.Application;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import fastble.BleManager;
import fastble.data.BleDevice;
import fastble.exception.BleException;
import fastble.utils.BleLog;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/6/5 10:25
 * version: 1.0
 */
public class BleConnectHelper1 {
    public static final String BLE_SCAN_START = "ble.scan.start";
    public static final String BLE_SCAN_FINISH = "ble.scan.finish";
    public static final String BLE_CONNECT_SUCCESS = "ble.conn.success";
    public static final String BLE_CONNECT_FAIL = "ble.conn.fail";
    public static final String BLE_DISCONNECT = "ble.disconnect";
    public static final String BLE_NOT_FOUND = "ble.not.found";
    public static final int BLE_SCAN_START_I = 0x1001;
    public static final int BLE_SCAN_FINISH_I = 0x1002;
    public static final int BLE_CONNECT_SUCCESS_I = 0x1003;
    public static final int BLE_CONNECT_FAIL_I = 0x1004;
    public static final int BLE_DISCONNECT_I = 0x1005;
    public static final int BLE_NOT_FOUND_I = 0x1006;
    public static final int START_TIMER = 0;
    //action String
    private static final String TAG = "BluetoothService";
    private static final int BASE_SCAN_TIME = 10000;
    private static BleConnectHelper1 bleConnectHelper;
    private BleManager bleManager;
    private BluetoothHelper bluetoothHelper;
    private Map<String, BleDevice> connDeviceMap = new HashMap<>();//第一个参数是address,第二个参数是BleDevice，已连接的设备
    private Map<String, Integer> readRssiMap = new HashMap<>();//第一个参数是address,第二个参数是rrsi值，读rssi的设备
    private Map<String, String> reconnDeviceMap = new HashMap<>();//第一个参数是address,第二个参数是name,需要重连的设备
    private Set<String> needConnectMap = new HashSet<>();//需要连接的设备
    private List<String> macaddrList = new ArrayList<>(); //扫描到的所以设备mac列表
    private ReconnHandler reConnHandler = new ReconnHandler(this);
    private Timer timer;
    private TimerTask timerTask;
    private double maxDistance;
    /**
     * 扫描开始监听
     */
    private OnScanStartListener scanStartListener;
    /**
     * 扫描完成监听
     */
    private OnScanFinishListener scanFinishListener;
    /**
     * 连接成功监听
     */
    private OnConnectSuccessListener connectSuccessListener;
    /**
     * 连接失败监听
     */
    private OnConnectFailListener connectFailListener;
    /**
     * 设备间断开蓝牙连接监听
     */
    private OnDisconnectListener disconnectListener;
    /**
     * 重连成功监听
     */
    private OnReconnectSuccessListener reconnectSuccessListener;
    /**
     * 重连失败监听
     */
    private OnReconnectFailListener reconnectFailListener;
    /**
     * 自身设备关闭蓝牙监听
     */
    private OnDeviceSelfDisableListener deviceSelfDisableListener;
    /**
     * 设备是否走远监听
     */
    private OnDeviceAwayListener deviceAwayListener;
    /**
     * 特征值变化监听
     */
    private OnCharacteristicChangeListener onCharacteristicChangeListener;
    /**
     * 通知开启成功监听
     */
    private NotifySuccessListener notifySuccessListener;
    /**
     * 通知开启失败监听
     */
    private NotifyFailListener notifyFailListener;
    /**
     * 通知回复(特征值变化)监听
     */
    private NotifyCharacteristicChangeListener notifyCharacteristicChangeListener;
    /**
     * 成功写入数据监听
     */
    private WriteSuccessListener writeSuccessListener;
    /**
     * 写入数据失败监听
     */
    private WriteFailListener writeFailListener;

    private BluetoothHelper.BleConnectListener connectListener = new BluetoothHelper.BleConnectListener() {

        @Override
        public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt) {
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
        public void onConnectFail(BleDevice bleDevice, BleException e) {
            getConnectFailNext(bleDevice, e.getDescription());
            addReConnectDevice(bleDevice.getMac(), bleDevice.getName());//加入重连列表中
            startReconnect(bleDevice.getMac(), BASE_SCAN_TIME);//重新连接
        }

        @Override
        public void onBleDisable() {

        }

        @Override
        public void onDisconnect(boolean isActiveDis, BleDevice device) {

            getDisconnectNext(isActiveDis, device, null);
            removeConnectDevice(device.getMac());//从已连接的列表中移除
            if (!isActiveDis) {//不是主动断开的
                addReConnectDevice(device.getMac(), device.getName());//加入重连列表中
                startReconnect(device.getMac(), 100);//开启重连
            } else {//主动断开连接的话，移除rssimap和reconnectmap
                removeReConnectDevice(device.getMac());
                removeRssiDevice(device.getMac());
            }
        }
    };

    private BleConnectHelper1() {
    }

    /**
     * 获取单例对象
     *
     * @return 返回单例对象
     */
    public static BleConnectHelper1 getInstance() {
        if (bleConnectHelper == null) {
            synchronized (BleConnectHelper1.class) {
                if (bleConnectHelper == null) {
                    bleConnectHelper = new BleConnectHelper1();
                }
            }
        }
        return bleConnectHelper;
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
    public BleConnectHelper1 init(Application application) {
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
    public BleConnectHelper1 init(Application application, int reconnectCount) {
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
    public BleConnectHelper1 init(Application application, int reconnectCount, double maxDistance) {
        bluetoothHelper = new BluetoothHelper(application, reconnectCount);
        bleManager = bluetoothHelper.getBleManager();
        this.maxDistance = maxDistance;
        return this;
    }

    /**
     * 初始化各种uuid
     *
     * @param service_uuids  服务uuid组
     * @param service_uuid   指定服务uuid
     * @param notify_uuid    通知uuid
     * @param read_uuid      读uuid
     * @param read_cha_uuid  .
     * @param write_uuid     .
     * @param write_cha_uuid .
     */
    public void initUuId(UUID[] service_uuids, String service_uuid, String notify_uuid, String read_uuid, String read_cha_uuid, String write_uuid, String write_cha_uuid) {
        bluetoothHelper.initUuid(null,
                service_uuid,
                notify_uuid,
                read_uuid,
                read_cha_uuid,
                write_uuid,
                write_cha_uuid);
    }

    /**
     * 扫描设备
     *
     * @param macList 需要匹配的设备list
     */
    public void scanList(Map<String, String> macList) {
        if (macList == null || macList.size() == 0) {
            return;
        }
        macaddrList.clear();
        for (Map.Entry entry : macList.entrySet()) {
            String macKey = entry.getKey().toString();
            needConnectMap.add(macKey);
            if (connDeviceMap.containsKey(macKey)) {
                removeConnectDevice(macKey);//先移除掉，因为重新开始了
            }
            if (reconnDeviceMap.containsKey(macKey)) {
                removeReConnectDevice(macKey);//先移除掉，因为重新开始了
            }

        }
        bluetoothHelper.scan(new BluetoothHelper.BleScanListener() {
            @Override
            public void onScanFinished(List<BleDevice> bleDeviceList) {
                for (BleDevice device : bleDeviceList) {
                    macaddrList.add(device.getMac().toUpperCase());
                }
                for (String mac : needConnectMap) {
                    if (!bleManager.isConnected(mac)) {
                        if (macaddrList.contains(mac)) {
                            BleDevice bleDevice = null;
                            for (BleDevice device : bleDeviceList) {
                                if (device.getMac().equalsIgnoreCase(mac)) {
                                    bleDevice = device;
                                }
                            }
                            getScanFinishNext(bleDevice);
                            bluetoothHelper.connect(mac, bleDevice.getName(), connectListener);
                            checkRssi(bleDevice, mac, macList.get(mac));//检查rssi
                        } else {
                            checkRssi(null, mac, macList.get(mac));//检查rssi
//                            if (!connDeviceMap.containsKey(mac)) {//如果没扫描到，过段时间继续扫描
                            addReConnectDevice(mac, macList.get(mac));//加入重连列表中
                            startReconnect(mac, BASE_SCAN_TIME);//重新连接
                            getScanFinishNext(null);//
//                            }
                        }
                    }

                }

            }

            @Override
            public void onScanStart() {
                getScanStartNext();
            }

            @Override
            public void onScanning(BleDevice device) {

            }

            @Override
            public void onBleDisable() {

            }
        });
    }

    /**
     * 判断是否连接
     *
     * @param macAddress 蓝牙地址
     * @return 返回true or false
     */
    public boolean isConnected(String macAddress) {
        return bleManager.isConnected(macAddress);
    }

    /**
     * 根据mac获取当前设备(通常为当前需要进行通信的设备)
     *
     * @param mac 蓝牙地址
     * @return 设备
     */
    public BleDevice getCurrentDevice(String mac) {
        if (connDeviceMap.containsKey(mac)) {
            return connDeviceMap.get(mac);
        }
        return null;
    }

    /**
     * 开启蓝牙通知
     *
     * @param macAddress 蓝牙地址
     */
    public void openNotify(String macAddress) {
        if (bluetoothHelper.getUuidHelper() == null) {
            BleDevice device = connDeviceMap.get(macAddress);
            notifyFail(device, "uuid is not initialize,please initialization!");
            return;
        }
        if (connDeviceMap.containsKey(macAddress)) {
            BleDevice device = connDeviceMap.get(macAddress);
            notify(device);
        }
    }

    public void openNotify(BleDevice device){
        if (bluetoothHelper.getUuidHelper() == null) {
            notifyFail(device, "uuid is not initialize,please initialization!");
            return;
        }
        notify(device);
    }

    private void notify(BleDevice device){
        bluetoothHelper.setNotify(device, new BluetoothHelper.BleNotifyListener() {
            @Override
            public void onNotifySuccess() {
                notifySuccess(device);
            }

            @Override
            public void onNotifyFailed(BleException e) {
                notifyFail(device,e.getDescription());
            }

            @Override
            public void onCharacteristicChanged(String mac,byte[] data) {
                notifyChange(mac,data);
            }
        });

    }

    /**
     * 写入数据(十六进制字节数组)
     *
     * @param macAddress 蓝牙地址
     * @param data       数据
     */
    public void writeData(String macAddress, byte[] data) {
        if (bluetoothHelper.getUuidHelper() == null) {
            BleDevice device = connDeviceMap.get(macAddress);
            notifyFail(device, "uuid is not initialize,please initialization!");
            return;
        }
        if (connDeviceMap.containsKey(macAddress)) {
            BleDevice device = connDeviceMap.get(macAddress);
            bluetoothHelper.write(device, data, new BluetoothHelper.WriteListener() {
                @Override
                public void onWriteSuccess(int current, int total, byte[] justWrite) {
                    writeSuccess(current, total, justWrite);
                }

                @Override
                public void onWriteFailure(BleException exception) {
                    writeFail(exception.getDescription());
                }
            });
        }
    }


    public void openVirtualLeash(boolean isFuzzzy, String address, String name) {
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
                getDeviceSelfDisableNext(address);
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
        Log.e(TAG, "deviceInfo----" + bleDevice + "/" + address + "/" + readRssiMap.size() + "/" + connDeviceMap.size());
        if (bleDevice == null) {//如果扫描不到了
            if (readRssiMap.containsKey(address)) {//之前有存rssi的话，说明之前扫描到过
                Integer valueRssi = readRssiMap.get(address);
                double distance = getDistance(valueRssi);//判断距离
                distance = new BigDecimal(distance).setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
                Log.e(TAG, "distance----" + distance + "/" + maxDistance);
                if (distance > maxDistance) {//如果距离大于10米了，那就走远了
                    getDeviceAwayNext(address, name);
                }
                if (distance < 1.0) { //1米范围内扫描不到,统一定义为离线
                    deviceSelfDisableListener.deviceSelfDisable(address);
                }
            }
        }
        if (bleDevice != null) {//说明还能扫描到
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
        timer.schedule(timerTask, 0, 2000);
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

    //----------------对外listeners----------------------

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

    /**
     * 开启重连
     *
     * @param key 蓝牙地址
     */
    private void reconnListening(String key) {
        Map<String, String> reconnDeviceMap = getReconnDeviceMap();
        Set<Map.Entry<String, String>> keys = reconnDeviceMap.entrySet();
        Map<String, String> scanMap = new HashMap<>();
        if (reconnDeviceMap != null && reconnDeviceMap.size() > 0) {//遍历需要重连的设备
            for (Map.Entry<String, String> entry : keys) {
                String key1 = entry.getKey();
                if (key1.equals(key)) {
                    scanMap.put(key1, entry.getValue());
                }
            }
            scanList(scanMap);
        } else {

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

    public void setScanStartListener(OnScanStartListener scanStartListener) {
        this.scanStartListener = scanStartListener;
    }

    private void getScanStartNext() {
        if (scanStartListener != null) {
            scanStartListener.scanStart();
        }
    }

    public void setScanFinishListener(OnScanFinishListener scanFinishListener) {
        this.scanFinishListener = scanFinishListener;
    }

    private void getScanFinishNext(BleDevice bleDevice) {
        if (scanFinishListener != null) {
            scanFinishListener.scanFinish(bleDevice);
        }
    }

    public void setConnectSuccessListener(OnConnectSuccessListener connectSuccessListener) {
        this.connectSuccessListener = connectSuccessListener;
    }

    private void getConnectSuccessNext(BleDevice bleDevice, BluetoothGatt gatt) {
        if (connectSuccessListener != null) {
            connectSuccessListener.connectSuccess(bleDevice, gatt);
        }
    }

    public void setConnectFailListener(OnConnectFailListener connectFailListener) {
        this.connectFailListener = connectFailListener;
    }

    private void getConnectFailNext(BleDevice bleDevice, String description) {
        if (connectFailListener != null) {
            connectFailListener.connectFail(bleDevice, description);
        }
    }

    public void setDisconnectListener(OnDisconnectListener disconnectListener) {
        this.disconnectListener = disconnectListener;
    }

    private void getDisconnectNext(boolean isActiveDisConnect, BleDevice bleDevice, BluetoothGatt gatt) {
        if (disconnectListener != null) {
            disconnectListener.disconnect(isActiveDisConnect, bleDevice, gatt);
        }
    }

    public void setReconnectSuccessListener(OnReconnectSuccessListener reconnectSuccessListener) {
        this.reconnectSuccessListener = reconnectSuccessListener;
    }

    private void getReconnectSuccessNext(BleDevice bleDevice) {
        if (reconnectSuccessListener != null) {
            reconnectSuccessListener.reconnectSuccess(bleDevice);
        }
    }

    public void setReconnectFailListener(OnReconnectFailListener reconnectFailListener) {
        this.reconnectFailListener = reconnectFailListener;
    }

    private void getReconnectFailNext(BleDevice bleDevice) {
        if (reconnectFailListener != null) {
            reconnectFailListener.reconnectFail(bleDevice);
        }
    }

    public void setDeviceSelfDisableListener(OnDeviceSelfDisableListener deviceSelfDisableListener) {
        this.deviceSelfDisableListener = deviceSelfDisableListener;
    }

    private void getDeviceSelfDisableNext(String mac) {
        if (deviceSelfDisableListener != null) {
            deviceSelfDisableListener.deviceSelfDisable(mac);
        }
    }

    public void setDeviceAwayListener(OnDeviceAwayListener deviceAwayListener) {
        this.deviceAwayListener = deviceAwayListener;
    }

    private void getDeviceAwayNext(String address, String name) {
        if (deviceAwayListener != null) {
            deviceAwayListener.deviceAway(address, name);
        }
    }

    private void notifySuccess(BleDevice device) {
        if (notifySuccessListener != null) {
            notifySuccessListener.notifySuccess(device);
        }
    }

    private void notifyFail(BleDevice device, String err) {
        if (notifyFailListener != null) {
            notifyFailListener.notifyFail(device,err);
        }
    }

    private void notifyChange(String mac,byte[] data) {
        if (notifyCharacteristicChangeListener != null) {
            notifyCharacteristicChangeListener.characteristicChange(mac,data);
        }
    }

    private void writeSuccess(int crr, int tl, byte[] data) {
        if (writeSuccessListener != null) {
            writeSuccessListener.writeSuccess(crr, tl, data);
        }
    }

    private void writeFail(String err) {
        if (writeFailListener != null) {
            writeFailListener.writeFail(err);
        }
    }

    public void setNotifySuccessListener(NotifySuccessListener notifySuccessListener) {
        this.notifySuccessListener = notifySuccessListener;
    }

    public void setNotifyFailListener(NotifyFailListener notifyFailListener) {
        this.notifyFailListener = notifyFailListener;
    }

    public void setNotifyCharacteristicChangeListener(NotifyCharacteristicChangeListener notifyCharacteristicChangeListener) {
        this.notifyCharacteristicChangeListener = notifyCharacteristicChangeListener;
    }

    public void setWriteSuccessListener(WriteSuccessListener writeSuccessListener) {
        this.writeSuccessListener = writeSuccessListener;
    }

    public void setWriteFailListener(WriteFailListener writeFailListener) {
        this.writeFailListener = writeFailListener;
    }

    /**
     * 设备返回来的数据变化
     *
     * @param charactoristicChangeListener
     */
    public void setCharacteristicChangeListener(OnCharacteristicChangeListener charactoristicChangeListener) {
        this.onCharacteristicChangeListener = charactoristicChangeListener;
    }

    private void getCharacteristicChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (onCharacteristicChangeListener != null) {
            onCharacteristicChangeListener.onCharacteristicChange(gatt, characteristic);
        }
    }

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

    public interface OnCharacteristicChangeListener {
        void onCharacteristicChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
    }

    public interface NotifySuccessListener {
        void notifySuccess(BleDevice device);
    }

    public interface NotifyFailListener {
        void notifyFail(BleDevice device, String err);
    }

    public interface NotifyCharacteristicChangeListener {
        void characteristicChange(String mac,byte[] data);
    }

    public interface WriteSuccessListener {
        void writeSuccess(int current, int total, byte[] packet);
    }

    public interface WriteFailListener {
        void writeFail(String err);
    }

    //重连的hanlder
    public static class ReconnHandler extends Handler {
        private WeakReference<BleConnectHelper1> reconnectHelperWeakReference;

        public ReconnHandler(BleConnectHelper1 reconnectHelper) {
            reconnectHelperWeakReference = new WeakReference<>(reconnectHelper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            BleConnectHelper1 connect = reconnectHelperWeakReference.get();
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

}
