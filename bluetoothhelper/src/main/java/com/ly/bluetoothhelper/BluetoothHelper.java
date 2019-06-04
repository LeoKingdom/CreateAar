package com.ly.bluetoothhelper;

import android.app.Activity;
import android.app.Application;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.ArrayMap;
import android.util.Log;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleGattCallback;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleReadCallback;
import com.clj.fastble.callback.BleRssiCallback;
import com.clj.fastble.callback.BleScanAndConnectCallback;
import com.clj.fastble.callback.BleScanCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;
import com.clj.fastble.utils.BleLog;
import com.ly.bluetoothhelper.BleUuidHelper;


import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/5/29 15:55
 * version: 1.0
 * <p>
 * 蓝牙辅助
 */
public class BluetoothHelper {

    private static BluetoothHelper bluetoothHelper;
    private boolean ifOpenNotify = true;
    private BleManager bleManager;
    private BleUuidHelper uuidHelper;

    public Map<String, BleDevice> getConnDeviceMap() {
        return connDeviceMap;
    }

    public Map<String, BleDevice> getDisConnDeviceMap() {
        return disConnDeviceMap;
    }

    private Map<String, BleDevice> connDeviceMap = new HashMap<>();
    private Map<String, BleDevice> disConnDeviceMap = new HashMap<>();

    public Map<String, Integer> getReconnCountMap() {
        return reconnCountMap;
    }

    public void setReconnCountMap(Map<String, Integer> reconnCountMap) {
        this.reconnCountMap = reconnCountMap;
    }

    private Map<String, Integer> reconnCountMap = new HashMap<>();

    private BluetoothHelper() {
        bleManager = BleManager.getInstance();
    }

    public static BluetoothHelper getBluetoothHelper() {
        if (bluetoothHelper == null) {
            synchronized (BluetoothHelper.class) {
                if (bluetoothHelper == null) {
                    bluetoothHelper = new BluetoothHelper();
                }
            }
        }
        return bluetoothHelper;
    }

    public BleManager getBleManager() {
        return bleManager;
    }

    public boolean isOpenBle() {
        if (bleManager != null) {
            return bleManager.isBlueEnable();
        }
        return false;
    }

    public void enableBle() {
        if (bleManager != null) {
            bleManager.enableBluetooth();
        }
    }

    public void setIfOpenNotify(boolean ifOpenNotify) {
        this.ifOpenNotify = ifOpenNotify;
    }


    public BluetoothHelper initProperties(Application application) {
        initBle(application);
        return this;
    }

    public BluetoothHelper initUuid(UUID[] service_uuids, String service_uuid, String notify_uuid, String read_uuid, String read_cha_uuid, String write_uuid, String write_cha_uuid) {
        uuidHelper = new BleUuidHelper.Builder()
                .setServiceUuids(service_uuids)
                .setServiceUuid(service_uuid)
                .setNotiyUuid(notify_uuid)
                .setReadUuid(read_uuid)
                .setReadChaUuid(read_cha_uuid)
                .setWriteUuid(write_uuid)
                .setWriteChaUuid(write_cha_uuid)
                .build();
        return this;
    }

    private void initBle(Application application) {
        bleManager.init(application);
        bleManager.enableLog(true)
                .setReConnectCount(5, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(5000);
    }

    private BleScanRuleConfig scanRule(boolean isFuzzy, String address, String name) {
        BleScanRuleConfig ruleConfig = new BleScanRuleConfig.Builder()
                .setServiceUuids(null)
                .setDeviceMac(address)
                .setDeviceName(isFuzzy, name)
                .setAutoConnect(true)
                .setScanTimeOut(10000)
                .build();
        return ruleConfig;
    }

    /**
     * 扫描并连接匹配的蓝牙设备
     *
     * @param address 蓝牙mac地址
     * @param name    蓝牙名称,不准确,一般不使用,除非蓝牙名称已知且不可更改
     */
    public void scanAndConnect(boolean isFuzzy, String address, String name) {
        bleManager.initScanRule(scanRule(isFuzzy, address, name));
        bleManager.scanAndConnect(scanAndConnectCallback);
    }

    /**
     * 扫描蓝牙设备
     */
    public void scan() {
        bleManager.scan(scanCallback);
    }

    /**
     * 连接设备
     *
     * @param mac 蓝牙mac地址
     */
    public void connect(String mac) {
        bleManager.connect(mac, gattCallback);
    }

    public void connect(BleDevice bleDevice) {
        bleManager.connect(bleDevice, gattCallback);
    }


    /**
     * 扫描监听
     */
    private BleScanCallback scanCallback = new BleScanCallback() {
        @Override
        public void onScanFinished(List<BleDevice> scanResultList) {
            if (scanListener != null) {
                scanListener.onScanFinished(scanResultList);
            }
        }

        @Override
        public void onScanStarted(boolean success) {

        }

        @Override
        public void onScanning(BleDevice bleDevice) {

        }
    };

    /**
     * 连接监听
     */
    private BleGattCallback gattCallback = new BleGattCallback() {
        @Override
        public void onStartConnect() {

        }

        @Override
        public void onConnectFail(BleDevice bleDevice, BleException exception) {
            if (connectListener != null) {
                connectListener.onConnectFail(bleDevice, exception);
            }
        }

        @Override
        public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
            Log.e("conn----", bleDevice.getMac() + "/" + bleDevice.getName());
            if (connectListener != null) {
                connectListener.onConnectSuccess(bleDevice, gatt);
            }
        }

        @Override
        public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {

        }
    };

    public BleScanAndConnectCallback getScanAndConnectCallback() {
        return scanAndConnectCallback;
    }

    /**
     * 扫描并连接监听
     */
    private BleScanAndConnectCallback scanAndConnectCallback = new BleScanAndConnectCallback() {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if (bleChangeStatus != null) {
                bleChangeStatus.onChangeConnectionState(gatt, status, newState);
            }
        }

        @Override
        public void onScanFinished(BleDevice bleDevice) {
            if (bleHandleListener != null) {
                bleHandleListener.onScanFinished(bleDevice);
            }

        }

        @Override
        public void onStartConnect() {

        }

        @Override
        public void onConnectFail(BleDevice bleDevice, BleException exception) {
            if (bleHandleListener != null) {
                bleHandleListener.onConnectFailed(bleDevice, exception.getDescription());
            }

            if (reconnectListener != null && disConnDeviceMap.size() > 0) {

                if (disConnDeviceMap.containsKey(bleDevice.getName())) {
                    reconnectListener.onReconnectFail(bleDevice);
                }
//                if (disConnDeviceMap.containsKey(bleDevice.getMac())) {
//                    reconnectListener.onReconnectFail(bleDevice);
//                }

//                try {
////                    BleDevice device = disConnDeviceMap.get(bleDevice.getMac());
//                    BleDevice device = disConnDeviceMap.get(bleDevice.getName());
//                    int rssi = device == null ? 0 : device.getRssi();
//                    double distance = getDistance(rssi);
//                    Log.e("info----", bleDevice.getMac() + "/" + rssi + "/" + distance + "/" + bleDevice.getRssi());
//                    if (bleDevice.getRssi() == 0 || distance > 5) {
//                        reconnectListener.onDeviceAway(bleDevice);
//                    }
//                } catch (NullPointerException e) {
//                    Log.e("Rssi---", "Rssi get from a null object");
//                }

            }

        }

        @Override
        public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
            if (reconnectListener != null) {
                if (reconnSate && disConnDeviceMap.containsKey(bleDevice.getName())) {
                    reconnSate = false;
                    reconnectListener.onReconnectSuccess(bleDevice);
                    Log.e("reconnSuRssi----", bleDevice.getRssi() + "");
                }
            }
//            connDeviceMap.put(bleDevice.getMac(),bleDevice);
//            reconnCountMap.put(bleDevice.getMac(),bleManager.getReConnectCount());
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
            if (bleHandleListener != null) {
                bleHandleListener.onConnectSuccess(bleDevice, gatt, status);
            }

        }

        @Override
        public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
            Log.e("disConn----", device.getMac() + "/" + device.getName() + "/" + device.getRssi());
            reconnSate = true;
//            disConnDeviceMap.put(device.getMac(), device);
            disConnDeviceMap.put(device.getName(), device); //测试
            scanAndConnect(true, null, device.getName());
            ReconnectHelper.ReconnHandler handler = ReconnectHelper.getInstance().getReConnHandler();
            handler.sendEmptyMessageDelayed(0, bleManager.getReConnectCount() * bleManager.getReConnectInterval());
//            new ReconnectHelper().reconnListening();
            if (bleHandleListener != null) {
                bleHandleListener.onDisconnect(device, gatt);
            }
            if (reconnSate && reconnectListener != null) {

//                Log.e("bleName===", device.getName() + "");
                double distance = getDistance(device.getRssi());
//                Log.e("info1----", device.getMac() + "/" + device.getRssi() + "/" + distance);
                if (device.getRssi() == 0 || distance > 5||device.getRssi()>=-62) {
                    reconnectListener.onDeviceAway(device);
                }

                if (!bleManager.isBlueEnable()) {
                    reconnectListener.onBleDisable();
                }
            }
        }

        @Override
        public void onScanStarted(boolean success) {
            if (bleHandleListener != null) {
                bleHandleListener.onScanStarted(success);
            }
        }

        @Override
        public void onScanning(BleDevice bleDevice) {
            if (bleDevice != null) {
                BleLog.e("found device===" + bleDevice.getMac() + "/" + bleDevice.getName());
            }
        }

    };

    public double getDistance(int rssi) {
        int iRssi = Math.abs(rssi);
        double power = (iRssi - 60) / (10 * 2.0);
        return Math.pow(10, power);
    }

    private boolean reconnSate = false;

    public void checkGpsAndOpen(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isLocationProviderEnabled(context)) {
                forceOpenGPS(context);
            }
        }
    }

    /**
     * 判断GPS是否打开
     *
     * @param context
     * @return
     */
    private boolean isLocationProviderEnabled(Context context) {
        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
//            boolean networkProvider = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            boolean gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (gpsProvider) return true;
            return false;
        } catch (Exception e) {

        }

        return false;

    }

    /**
     * 强制帮用户打开GPS,避免一些不必要的兼容问题,但在4.0以上已失效
     *
     * @param context
     */
    private void forceOpenGPS(Context context) {
        Intent GPSIntent = new Intent();
        GPSIntent.setClassName("com.android.settings",
                "com.android.settings.widget.SettingsAppWidgetProvider");
        GPSIntent.addCategory("android.intent.category.ALTERNATIVE");
        GPSIntent.setData(Uri.parse("custom:3"));
        try {
            PendingIntent.getBroadcast(context, 0, GPSIntent, 0).send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }

    public interface Status {
        int BLE_DISABLE = 0x2001; //手机端蓝牙未打开
        int BLE_REMOTE_DISABLE = 0x2002; //设备端蓝牙未打开
        int BLE_NOT_SURPPORT = 0x2003; //手机端不支持BLE
        int BLE_LOCATION_DISABLE = 0x2004; //手机端定位信息不可用,这里强制直接打开
        int BLE_TRACKER_NOT_FOUND = 0x2005; //设备未找到
        int BLE_TRACKER_OUT = 0x2006; //tracker走出范围
        int BLE_VIRTUAL_LEASH_OPEN_DIALOG = 0x2007; //打开virtual leash弹出dialog
        int BLE_SCAN_AND_CONNECT = 0x2008; //扫描连接蓝牙
        int BLE_PERMISSION_GRANT = 0x2009; //授予蓝牙权限
        int BLE_GPS_FORCE_OPEN = 0x2010; //强制打开gps
        int BLE_TRACKER_IN = 0x2011; //tracker 进圈
    }


    /**
     * 处理蓝牙的细分回调,如连接,扫描等操作 START
     */

    private BleHandleListener bleHandleListener;

    public void setBleHandleListener(BleHandleListener bleHandleListener) {
        this.bleHandleListener = bleHandleListener;
    }

    public interface BleHandleListener {


        void onScanStarted(boolean success);

        void onScanning();

        void onScanFinished(BleDevice bleDevice);

        void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status);

        void onConnectFailed(BleDevice bleDevice, String description);

        void onDisconnect(BleDevice device, BluetoothGatt gatt);
    }


    // ------------something response callback -----------------

    //扫描
    public void setScanListener(BleScanListener scanListener) {
        this.scanListener = scanListener;
    }

    private BleScanListener scanListener;

    public interface BleScanListener {
        void onScanFinished(List<BleDevice> bleDeviceList);
    }

    //连接
    public void setConnectListener(BleConnectListener connectListener) {
        this.connectListener = connectListener;
    }

    private BleConnectListener connectListener;

    public interface BleConnectListener {
        void onConnectSuccess(BleDevice device, BluetoothGatt gatt);

        void onConnectFail(BleDevice device, BleException e);
    }

    //重连监听
    public void setReconnectListener(BleReconnectListener reconnectListener) {
        this.reconnectListener = reconnectListener;
    }

    private BleReconnectListener reconnectListener;

    public interface BleReconnectListener {
        void onReconnectSuccess(BleDevice device);

        void onReconnectFail(BleDevice bleDevice);

        void onBleDisable();

        void onDeviceAway(BleDevice device);
    }

    /**
     * 监听蓝牙各种状态回调
     */
    private BleChangeStatus bleChangeStatus;

    public void setBleChangeStatus(BleChangeStatus bleChangeStatus) {
        this.bleChangeStatus = bleChangeStatus;
    }

    public interface BleChangeStatus {
        void onChangeConnectionState(BluetoothGatt gatt, int status, int newState);
    }


    /**
     * 读取设备
     *
     * @param bleDevice
     * @param readListener
     */
    public void read(BleDevice bleDevice, final ReadListener readListener) {
        BleManager.getInstance().read(
                bleDevice,
                uuidHelper.getServiceUuid(),
                uuidHelper.getReadChaUuid(),
                new BleReadCallback() {
                    @Override
                    public void onReadSuccess(byte[] data) {
                        if (readListener != null) {
                            readListener.onReadSuccess(data);
                        }
                    }

                    @Override
                    public void onReadFailure(BleException exception) {
                        if (readListener != null) {
                            readListener.onReadFailure(exception);
                        }
                    }
                });
    }


    //读
    private ReadListener readListener;

    public void setReadListener(ReadListener readListener) {
        this.readListener = readListener;
    }

    public interface ReadListener {
        void onReadSuccess(byte[] data);

        void onReadFailure(BleException exception);
    }


    /**
     * 设置通知
     *
     * @param bleDevice 设备
     *                  notify 和indicate方法都可以设置通知,区别在于:indicate方法,从端收到通知会回发一个ACK包到主端
     */
    private void setNotify(BleDevice bleDevice, BleNotifyListener listener) {
        if (uuidHelper == null) uuidHelper = new BleUuidHelper();
        this.notifyListener = listener;
        BleManager.getInstance().notify(bleDevice, uuidHelper.getServiceUuid(), uuidHelper.getNotiyUuid(), notifyCallback);
    }

    /**
     * 通知监听
     */
    private BleNotifyCallback notifyCallback = new BleNotifyCallback() {
        // 打开通知操作成功
        @Override
        public void onNotifySuccess() {
            if (notifyListener != null) {
                notifyListener.onNotifySuccess();
            }
        }

        // 打开通知操作失败
        @Override
        public void onNotifyFailure(BleException exception) {
            if (notifyListener != null) {
                notifyListener.onNotifyFailed(exception);
            }
        }

        // 打开通知后，设备发过来的数据将在这里出现
        @Override
        public void onCharacteristicChanged(byte[] data) {
            if (notifyListener != null) {
                notifyListener.onCharacteristicChanged(data);
            }
        }
    };

    private BleNotifyListener notifyListener;

    public interface BleNotifyListener {
        void onNotifySuccess();

        void onNotifyFailed(BleException e);

        void onCharacteristicChanged(byte[] data);
    }

    //写数据的监听
    public interface WriteListener {
        void onWriteSuccess(int current, int total, byte[] justWrite);

        void onWriteFailure(BleException exception);
    }

    private WriteListener writeListener;

    public void setWriteListener(WriteListener writeListener) {
        this.writeListener = writeListener;
    }


    /**
     * 写数据的回调
     *
     * @param bleDevice 蓝牙设备
     * @param dates     传输的字节数据
     * @param listener  监听回调
     */
    public void write(BleDevice bleDevice, byte[] dates, final WriteListener listener) {
        BleManager.getInstance().write(
                bleDevice,
                uuidHelper.getServiceUuid(),
                uuidHelper.getWriteUuid(),
                dates,
                new BleWriteCallback() {
                    @Override
                    public void onWriteSuccess(int current, int total, byte[] justWrite) {
                        listener.onWriteSuccess(current, total, justWrite);
                    }

                    @Override
                    public void onWriteFailure(BleException exception) {
                        // 发送数据到设备失败
                        listener.onWriteFailure(exception);
                    }
                });

    }


    //rssi监听
    public void setRssiListener(RemoteRssiListener rssiListener) {
        this.rssiListener = rssiListener;
    }

    private RemoteRssiListener rssiListener;

    public interface RemoteRssiListener {
        void onRemoteRssi(int rssi);

        void onRssiFailure(BleException exception);
    }

    /**
     * 读取信号强度的回调
     *
     * @param bleDevice
     * @param rssiListener
     */
    public void readRssi(BleDevice bleDevice, final RemoteRssiListener rssiListener) {
        BleManager.getInstance().readRssi(
                bleDevice,
                new BleRssiCallback() {

                    @Override
                    public void onRssiFailure(BleException exception) {
                        // 读取设备的信号强度失败
                        rssiListener.onRssiFailure(exception);
                    }

                    @Override
                    public void onRssiSuccess(int rssi) {
                        // 读取设备的信号强度成功
                        rssiListener.onRemoteRssi(rssi);
                    }
                });
    }


    // 蓝牙和gps打开回调
    public interface OpenListener {
        void passAll();//全都有打开

        void notPass(boolean isOpenBle, boolean isOpenGps); // gps或ble 没打开
    }

    private OpenListener openListener;

    public void setOpenListener(OpenListener openListener) {
        this.openListener = openListener;
    }

    /**
     * 检查gps和蓝牙开关是否有开
     *
     * @param listener OpenListener回调
     */
    public void checkOpenAll(Context context, OpenListener listener) {
        boolean isOpenGps = isLocationProviderEnabled(context);
        boolean isOpenBle = isOpenBle();
        if (isOpenBle && isOpenGps) {
            listener.passAll();
        } else {
            listener.notPass(isOpenBle, isOpenBle);
        }
    }

    /**
     * 打开蓝牙
     *
     * @param fragment
     * @param reqeustCode
     */
    public void openBle(Fragment fragment, int reqeustCode) {
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        fragment.startActivityForResult(enableBtIntent, reqeustCode);
    }

    /**
     * 提醒用户去设置页打开gps
     *
     * @param fragment
     * @param requestcode
     */
    public static void openGPS(Fragment fragment, int requestcode) {
        Intent locationIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        fragment.startActivityForResult(locationIntent, requestcode);
    }
    /**
     * Ble END
     */
}