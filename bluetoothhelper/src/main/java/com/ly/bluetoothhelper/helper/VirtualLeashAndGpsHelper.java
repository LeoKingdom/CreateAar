package com.ly.bluetoothhelper.helper;

import android.app.Activity;
import android.app.Application;
import android.bluetooth.BluetoothGatt;
import android.content.IntentSender;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import com.ly.bluetoothhelper.callbacks.ConnectCallback;
import com.ly.bluetoothhelper.callbacks.ReadRssiCallback;
import com.ly.bluetoothhelper.callbacks.ScanCallback;
import com.ly.bluetoothhelper.callbacks.VirtualLeashCallback.*;
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
import fastble.data.BleDevice;
import fastble.exception.BleException;
import fastble.utils.BleLog;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/6/5 10:25
 * version: 1.0
 */
public class VirtualLeashAndGpsHelper extends BleBaseHelper{
    public static final int START_TIMER = 0;
    private static final String TAG = "BluetoothService";
    private static final int BASE_SCAN_TIME = 10000;
    private static VirtualLeashAndGpsHelper bleConnectHelper;
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

    private ConnectCallback connectListener = new ConnectCallback() {
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

    private VirtualLeashAndGpsHelper(Application application) {
        super(application);
    }
    /**
     * 获取单例对象
     *
     * @return 返回单例对象
     */
    public static VirtualLeashAndGpsHelper getInstance(Application app) {
        if (bleConnectHelper == null) {
            synchronized (VirtualLeashAndGpsHelper.class) {
                if (bleConnectHelper == null) {
                    bleConnectHelper = new VirtualLeashAndGpsHelper(app);
                }
            }
        }
        return bleConnectHelper;
    }

    @Override
    public void init() {
        String[] uuids = { "00005500-d102-11e1-9b23-00025b00a5a5", "00005501-d102-11e1-9b23-00025b00a5a5"};
        setService_UUID(uuids[0]).setRead_UUID(uuids[1]).setWrite_UUID(uuids[1]).setNotify_UUID(uuids[1]);
    }

    public Map<String, String> getReconnDeviceMap() {
        return reconnDeviceMap;
    }

    public Map<String, Integer> getReadRssiMap() {
        return readRssiMap;
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
        scan(new ScanCallback() {
            @Override
            public void onScanFinished(List<BleDevice> bleDeviceList) {
                for (BleDevice device : bleDeviceList) {
                    macaddrList.add(device.getMac().toUpperCase());
                }
                for (String mac : needConnectMap) {
                    if (!isConnected(mac)) {
                        if (macaddrList.contains(mac)) {
                            BleDevice bleDevice = null;
                            for (BleDevice device : bleDeviceList) {
                                if (device.getMac().equalsIgnoreCase(mac)) {
                                    bleDevice = device;
                                }
                            }
                            getScanFinishNext(bleDevice);
                            connect(bleDevice.getMac(), connectListener);
                            checkRssi(bleDevice, mac, macList.get(mac));//检查rssi
                        } else {
                            checkRssi(null, mac, macList.get(mac));//检查rssi
//                            if (!connDeviceMap.containsKey(mac)) {//如果没扫描到，过段时间继续扫描
                            addReConnectDevice(mac, macList.get(mac));//加入重连列表中
                            startReconnect(mac, BASE_SCAN_TIME);//重新连接
                            getScanFinishNext(null);//
//                            }
                        }
                    }else {
                        connDeviceMap.put(mac,getConnectDevice(mac));
                    }
                }
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
        BleLog.e(TAG, "deviceInfo----" + bleDevice + "/" + address + "/" + readRssiMap.size() + "/" + connDeviceMap.size());
        if (bleDevice == null) {//如果扫描不到了
            if (readRssiMap.containsKey(address)) {//之前有存rssi的话，说明之前扫描到过
                Integer valueRssi = readRssiMap.get(address);
                double distance = getDistance(valueRssi);//判断距离
                distance = new BigDecimal(distance).setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
                BleLog.e(TAG, "distance----" + distance + "/" + maxDistance);
                if (distance > maxDistance) {//如果距离大于10米了，那就走远了
                    getDeviceAwayNext(address, name);
                } else { //1米范围内扫描不到,统一定义为离线
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
                        readRssi(device.getValue(), new ReadRssiCallback() {
                            @Override
                            public void onRemoteRssi(int rssi) {
                                readRssiMap.put(device.getKey(), rssi);
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

    public void cancelHandler() {
        if (reConnHandler != null) {
            reConnHandler.removeCallbacks(null);
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

    /**
     * 移除所有map,关闭virtual leash开关时调用
     * @param mac
     */
    public void removeDevice(String mac) {
        needConnectMap.remove(mac);
        readRssiMap.remove(mac);
        connDeviceMap.remove(mac);
        reconnDeviceMap.remove(mac);
    }


    /**
     * 开始扫描回调
     * @param scanStartListener
     */
    public void setScanStartListener(OnScanStartListener scanStartListener) {
        this.scanStartListener = scanStartListener;
    }

    /**
     * 开始扫描回调对内方法
     */
    private void getScanStartNext() {
        if (scanStartListener != null) {
            scanStartListener.scanStart();
        }
    }

    /**
     * 扫描结束回调
     * @param scanFinishListener
     */
    public void setScanFinishListener(OnScanFinishListener scanFinishListener) {
        this.scanFinishListener = scanFinishListener;
    }

    /**
     * 扫描结束回调对内方法
     * @param bleDevice
     */
    private void getScanFinishNext(BleDevice bleDevice) {
        if (scanFinishListener != null) {
            scanFinishListener.scanFinish(bleDevice);
        }
    }

    /**
     * 连接成功回调
     * @param connectSuccessListener
     */
    public void setConnectSuccessListener(OnConnectSuccessListener connectSuccessListener) {
        this.connectSuccessListener = connectSuccessListener;
    }

    /**
     * 扫描成功回调对内方法
     * @param bleDevice
     * @param gatt
     */
    private void getConnectSuccessNext(BleDevice bleDevice, BluetoothGatt gatt) {
        if (connectSuccessListener != null) {
            connectSuccessListener.connectSuccess(bleDevice, gatt);
        }
    }

    /**
     * 连接失败回调
     * @param connectFailListener
     */
    public void setConnectFailListener(OnConnectFailListener connectFailListener) {
        this.connectFailListener = connectFailListener;
    }

    /**
     * 连接失败回调对内方法
     * @param bleDevice
     * @param description
     */
    private void getConnectFailNext(BleDevice bleDevice, String description) {
        if (connectFailListener != null) {
            connectFailListener.connectFail(bleDevice, description);
        }
    }

    /**
     * 断开连接回调
     * @param disconnectListener
     */
    public void setDisconnectListener(OnDisconnectListener disconnectListener) {
        this.disconnectListener = disconnectListener;
    }

    /**
     * 断开连接对内方法
     * @param isActiveDisConnect
     * @param bleDevice
     * @param gatt
     */
    private void getDisconnectNext(boolean isActiveDisConnect, BleDevice bleDevice, BluetoothGatt gatt) {
        if (disconnectListener != null) {
            disconnectListener.disconnect(isActiveDisConnect, bleDevice, gatt);
        }
    }

    /**
     * 重连成功回调
     * @param reconnectSuccessListener
     */
    public void setReconnectSuccessListener(OnReconnectSuccessListener reconnectSuccessListener) {
        this.reconnectSuccessListener = reconnectSuccessListener;
    }

    /**
     * 重连成功回调对内方法
     * @param bleDevice
     */
    private void getReconnectSuccessNext(BleDevice bleDevice) {
        if (reconnectSuccessListener != null) {
            reconnectSuccessListener.reconnectSuccess(bleDevice);
        }
    }

    /**
     * 重连失败回调
     * @param reconnectFailListener
     */
    public void setReconnectFailListener(OnReconnectFailListener reconnectFailListener) {
        this.reconnectFailListener = reconnectFailListener;
    }

    /**
     * 重连失败回调对内方法
     * @param bleDevice
     */
    private void getReconnectFailNext(BleDevice bleDevice) {
        if (reconnectFailListener != null) {
            reconnectFailListener.reconnectFail(bleDevice);
        }
    }

    /**
     * 蓝牙关闭回调,通常在扫描过程中手动关闭蓝牙时使用
     * @param deviceSelfDisableListener
     */
    public void setDeviceSelfDisableListener(OnDeviceSelfDisableListener deviceSelfDisableListener) {
        this.deviceSelfDisableListener = deviceSelfDisableListener;
    }

    /**
     * 蓝牙关闭回调对内方法
     * @param mac
     */
    private void getDeviceSelfDisableNext(String mac) {
        if (deviceSelfDisableListener != null) {
            deviceSelfDisableListener.deviceSelfDisable(mac);
        }
    }

    /**
     * 设备不在蓝牙范围回调
     * @param deviceAwayListener
     */
    public void setDeviceAwayListener(OnDeviceAwayListener deviceAwayListener) {
        this.deviceAwayListener = deviceAwayListener;
    }

    /**
     * 设备不在蓝牙范围回调对内方法
     * @param address
     * @param name
     */
    private void getDeviceAwayNext(String address, String name) {
        if (deviceAwayListener != null) {
            deviceAwayListener.deviceAway(address, name);
        }
    }

    /**
     * 成功开启通知回调
     * @param notifySuccessListener
     */
    public void setNotifySuccessListener(NotifySuccessListener notifySuccessListener) {
        this.notifySuccessListener = notifySuccessListener;
    }
    /**
     *成功开启通知回调对内方法
     * @param device
     */
    private void notifySuccess(BleDevice device) {
        if (notifySuccessListener != null) {
            notifySuccessListener.notifySuccess(device);
        }
    }
    /**
     * 开启通知失败回调
     * @param notifyFailListener
     */
    public void setNotifyFailListener(NotifyFailListener notifyFailListener) {
        this.notifyFailListener = notifyFailListener;
    }
    /**
     * 开启通知失败回调对内方法
     * @param
     */
    private void notifyFail(BleDevice device, String err) {
        if (notifyFailListener != null) {
            notifyFailListener.notifyFail(device, err);
        }
    }

    /**
     * 从端设备数据变化回调
     * @param notifyCharacteristicChangeListener
     */
    public void setNotifyCharacteristicChangeListener(NotifyCharacteristicChangeListener notifyCharacteristicChangeListener) {
        this.notifyCharacteristicChangeListener = notifyCharacteristicChangeListener;
    }
    /**
     * 从端设备数据变化回调对内方法
     * @param
     */
    private void notifyChange(String mac, byte[] data) {
        if (notifyCharacteristicChangeListener != null) {
            notifyCharacteristicChangeListener.characteristicChange(mac, data);
        }
    }

    /**
     * 写操作回调
     * @param writeSuccessListener
     */
    public void setWriteSuccessListener(WriteSuccessListener writeSuccessListener) {
        this.writeSuccessListener = writeSuccessListener;
    }

    /**
     * 写入数据成功回调对内方法
     * @param crr
     * @param tl
     * @param data
     */
    private void writeSuccess(int crr, int tl, byte[] data) {
        if (writeSuccessListener != null) {
            writeSuccessListener.writeSuccess(crr, tl, data);
        }
    }

    /**
     * 写入数据失败回调
     * @param writeFailListener
     */
    public void setWriteFailListener(WriteFailListener writeFailListener) {
        this.writeFailListener = writeFailListener;
    }

    /**
     * 写入数据失败回调对内方法
     * @param err
     */
    private void writeFail(String err) {
        if (writeFailListener != null) {
            writeFailListener.writeFail(err);
        }
    }

    /**
     * 重新连接handler,此处等同于定时器的作用
     */
    public static class ReconnHandler extends Handler {
        private WeakReference<VirtualLeashAndGpsHelper> reconnectHelperWeakReference;

        public ReconnHandler(VirtualLeashAndGpsHelper reconnectHelper) {
            reconnectHelperWeakReference = new WeakReference<>(reconnectHelper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            VirtualLeashAndGpsHelper connect = reconnectHelperWeakReference.get();
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
