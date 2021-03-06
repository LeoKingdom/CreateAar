package com.ly.bluetoothhelper.helper;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.provider.Settings;
import com.ly.bluetoothhelper.utils.Utils;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import fastble.BleManager;
import fastble.callback.BleGattCallback;
import fastble.callback.BleMtuChangedCallback;
import fastble.callback.BleNotifyCallback;
import fastble.callback.BleReadCallback;
import fastble.callback.BleRssiCallback;
import fastble.callback.BleScanAndConnectCallback;
import fastble.callback.BleScanCallback;
import fastble.callback.BleWriteCallback;
import fastble.data.BleDevice;
import fastble.exception.BleException;
import fastble.scan.BleScanRuleConfig;
import fastble.utils.BleLog;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/5/29 15:55
 * version: 1.0
 * <p>
 * 蓝牙辅助
 */
public class BluetoothHelper {

    private BleManager bleManager;
    private BleUuidHelper uuidHelper;
    private int reconnectCount = 5;
    private int operateTime = 10000;//操作超时时间,主要在使用开启notify时需要设置长一点

    public BluetoothHelper(Application application) {
        bleManager = BleManager.getInstance();
        initProperties(application);
    }

    public BluetoothHelper(Application application, int reConnCount) {
        bleManager = BleManager.getInstance();
        this.reconnectCount = reConnCount;
        this.initProperties(application);
    }

    public BluetoothHelper(Application application, int reConnCount, int operateTime) {
        bleManager = BleManager.getInstance();
        this.reconnectCount = reConnCount;
        this.operateTime = operateTime;
        this.initProperties(application);
    }


    public BleManager getBleManager() {
        return bleManager;
    }

    @SuppressLint("MissingPermission")
    public boolean connectOnBondDevice(String macAddress) {
        BluetoothAdapter bleAdapter = bleManager.getBluetoothAdapter();
        if (bleAdapter != null) {
            Set<BluetoothDevice> deviceList = bleAdapter.getBondedDevices();
            for (BluetoothDevice device : deviceList) {
                //                Log.e("bond-----",device.getAddress()+"/"+macAddress);
                if (macAddress.equals(device.getAddress())) {
                    Utils.unpairDevice(device);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isSupportBle() {
        return BleManager.getInstance().isSupportBle();
    }

    /**
     * 打开蓝牙
     */
    public static void enableBle() {
        BleManager.getInstance().enableBluetooth();
    }

    /**
     * 打开蓝牙
     *
     * @param fragment
     * @param reqeustCode
     */
    public static void openBle(Fragment fragment, int reqeustCode) {
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
     * 检查gps和蓝牙开关是否有开
     *
     * @param listener OpenListener回调
     */
    public static void checkGpsAndBle(Context context, OpenListener listener) {
        boolean isOpenGps = isLocationProviderEnabled(context);
        boolean isOpenBle = isOpenBle();
        listener.open(isOpenBle, isOpenGps);
    }

    /**
     * 判断GPS是否打开
     *
     * @param context
     * @return
     */
    private static boolean isLocationProviderEnabled(Context context) {
        try {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            boolean gpsProvider = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            if (gpsProvider) return true;
            return false;
        } catch (Exception e) {

        }

        return false;
    }

    /**
     * 判断是否打开蓝牙
     *
     * @return
     */
    public static boolean isOpenBle() {
        return BleManager.getInstance().isBlueEnable();
    }


    /*----------------------------------蓝牙和gps的回调start------------------------------*/
    // 蓝牙和gps打开回调
    public interface OpenListener {
        void open(boolean isOpenBle, boolean isOpenGps);
    }

    /*----------------------------------蓝牙和gps的回调 end------------------------------*/

    /**
     * 查看设备是否连接
     *
     * @param mac
     * @return
     */
    public static boolean isConnected(String mac) {
        List<BleDevice> allConnectedDevice = BleManager.getInstance().getAllConnectedDevice();
        return BleManager.getInstance().isConnected(mac);
    }

    /**
     * 查看设备是否连接
     *
     * @param bleDevice
     * @return
     */
    public static boolean isConnected(BleDevice bleDevice) {
        return BleManager.getInstance().isConnected(bleDevice);
    }

    /**
     * 查看已连接的设备
     *
     * @return
     */
    public static List<BleDevice> getConnectedDeviceList() {
        return BleManager.getInstance().getAllConnectedDevice();
    }

    /**
     * 根据蓝牙地址获取已连接的设备
     *
     * @return
     */
    public  BleDevice getConnectDevice(String mac){
        List<BleDevice> deviceList=getConnectedDeviceList();
        for (BleDevice device:deviceList){
            if (device.getMac().equalsIgnoreCase(mac)){
                return device;
            }
        }
        return null;
    }

    /**
     * 关闭蓝牙
     */
    public static void closeBle() {
        BleManager.getInstance().disableBluetooth();
    }

    /**
     * 扫描取消
     */
    public static void cancelScan() {
        BleManager.getInstance().cancelScan();
    }

    public BleUuidHelper getUuidHelper() {
        return uuidHelper;
    }

    /**
     * 各种UUID初始化
     *
     * @param service_uuids
     * @param service_uuid
     * @param notify_uuid
     * @param read_uuid
     * @param read_cha_uuid
     * @param write_uuid
     * @param write_cha_uuid
     * @return
     */
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

    /**
     * 扫描并连接匹配的蓝牙设备
     *
     * @param address 蓝牙mac地址
     * @param name    蓝牙名称,不准确,一般不使用,除非蓝牙名称已知且不可更改
     */
    public void scanAndConnect(String address, String name, BleHandleListener handleListener) {
        if (!isOpenBle()) {
            handleListener.onBleDisable();
        }
        bleManager.scanAndConnect(new BleScanAndConnectCallback() {

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                if (characteristicChangeListener != null) {
                    characteristicChangeListener.onCharacteristicChange(gatt, characteristic);
                }
            }

            /**
             * 扫描结束
             * @param bleDevice 扫描到的设备,可以为null
             */
            @Override
            public void onScanFinished(BleDevice bleDevice) {
                if (handleListener != null) {
                    handleListener.onScanFinished(bleDevice);
                }
            }

            /**
             * 连接开始
             */
            @Override
            public void onStartConnect() {

            }

            /**
             * 连接失败
             * @param bleDevice 连接的设备,可以为null
             * @param exception 异常
             */
            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                if (!isOpenBle()) {
                    handleListener.onBleDisable();
                } else if (handleListener != null) {
                    handleListener.onConnectFailed(bleDevice, exception.getDescription());
                }
            }

            /**
             * 连接成功
             * @param bleDevice 连接成功的设备
             * @param gatt 蓝牙gatt
             * @param status gatt状态
             */
            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                if (handleListener != null) {
                    handleListener.onConnectSuccess(bleDevice, gatt, status);
                }

            }

            /**
             * 断开连接
             * @param isActiveDisConnected 是否是之前自己要求主动断开的
             * @param device 断开设备
             * @param gatt gatt
             * @param status 状态
             */
            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                if (!isOpenBle()) {
                    handleListener.onBleDisable();
                } else if (handleListener != null) {
                    handleListener.onDisconnect(isActiveDisConnected, device, gatt);
                }

            }

            /**
             * 扫描开始
             * @param success 是否已开始
             */
            @Override
            public void onScanStarted(boolean success) {
                if (handleListener != null) {
                    handleListener.onScanStarted(success);
                }
            }

            /**
             * 扫描中
             * @param bleDevice
             */
            @Override
            public void onScanning(BleDevice bleDevice) {
                if (bleDevice != null) {
                    BleLog.e("found device===" + bleDevice.getMac() + "/" + bleDevice.getName());
                }
            }

        });
    }
    public void scanAndConnect(boolean isFuzzy, String address, String name, BleHandleListener handleListener) {
        if (!isOpenBle()) {
            handleListener.onBleDisable();
        }
        bleManager.initScanRule(scanRule(isFuzzy, address, name,true));
        bleManager.scanAndConnect(new BleScanAndConnectCallback() {

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                if (characteristicChangeListener != null) {
                    characteristicChangeListener.onCharacteristicChange(gatt, characteristic);
                }
            }

            /**
             * 扫描结束
             * @param bleDevice 扫描到的设备,可以为null
             */
            @Override
            public void onScanFinished(BleDevice bleDevice) {
                if (handleListener != null) {
                    handleListener.onScanFinished(bleDevice);
                }
            }

            /**
             * 连接开始
             */
            @Override
            public void onStartConnect() {

            }

            /**
             * 连接失败
             * @param bleDevice 连接的设备,可以为null
             * @param exception 异常
             */
            @Override
            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                if (!isOpenBle()) {
                    handleListener.onBleDisable();
                } else if (handleListener != null) {
                    handleListener.onConnectFailed(bleDevice, exception.getDescription());
                }
            }

            /**
             * 连接成功
             * @param bleDevice 连接成功的设备
             * @param gatt 蓝牙gatt
             * @param status gatt状态
             */
            @Override
            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                if (handleListener != null) {
                    handleListener.onConnectSuccess(bleDevice, gatt, status);
                }

            }

            /**
             * 断开连接
             * @param isActiveDisConnected 是否是之前自己要求主动断开的
             * @param device 断开设备
             * @param gatt gatt
             * @param status 状态
             */
            @Override
            public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                if (!isOpenBle()) {
                    handleListener.onBleDisable();
                } else if (handleListener != null) {
                    handleListener.onDisconnect(isActiveDisConnected, device, gatt);
                }

            }

            /**
             * 扫描开始
             * @param success 是否已开始
             */
            @Override
            public void onScanStarted(boolean success) {
                if (handleListener != null) {
                    handleListener.onScanStarted(success);
                }
            }

            /**
             * 扫描中
             * @param bleDevice
             */
            @Override
            public void onScanning(BleDevice bleDevice) {
                if (bleDevice != null) {
                    BleLog.e("found device===" + bleDevice.getMac() + "/" + bleDevice.getName());
                }
            }

        });
    }

    /**
     * 断开所有设备连接
     */
    public void disConnectAllDevice() {
        bleManager.disconnectAllDevice();
    }

    /**
     * 断开连接
     *
     * @param bleDevice
     */
    public void disConnectDevice(BleDevice bleDevice) {
        bleManager.disconnect(bleDevice);
    }


    public BleDevice getBleDeviceFromMac(String mac){
        BluetoothDevice bluetoothDevice = bleManager.getBluetoothAdapter().getRemoteDevice(mac);
        BleDevice bleDevice = new BleDevice(bluetoothDevice, 0, null, 0);
        return bleDevice;
    }

    /**
     * 扫描蓝牙设备
     */
    public void scan(BleScanListener scanListener) {
        if (!isOpenBle()) {
            scanListener.onBleDisable();
        }
        bleManager.scan(new BleScanCallback() {
            @Override
            public void onScanFinished(List<BleDevice> scanResultList) {
                if (scanListener != null) {
                    scanListener.onScanFinished(scanResultList);
                }
            }

            @Override
            public void onScanStarted(boolean success) {
                if (scanListener != null) {
                    scanListener.onScanStart();
                }
            }

            @Override
            public void onScanning(BleDevice bleDevice) {
                if (scanListener != null) {
                    scanListener.onScanning(bleDevice);
                }
            }
        });
    }


    /**
     * 连接设备
     *
     * @param mac 蓝牙mac地址
     */
    public void connect(String mac, BleConnectListener connectListener) {
        this.connectListener = connectListener;
        BleScanRuleConfig config=new BleScanRuleConfig.Builder().setAutoConnect(true).build();
        bleManager.initScanRule(config);
        bleManager.connect(mac, connectCallback);
    }

    /**
     * 连接设备
     *
     * @param bleDevice 蓝牙设备
     */
    public void connect(BleDevice bleDevice, BleConnectListener connectListener) {
        this.connectListener = connectListener;
        BleScanRuleConfig config=new BleScanRuleConfig.Builder().setAutoConnect(true).build();
        bleManager.initScanRule(config);
        bleManager.connect(bleDevice, connectCallback);
    }

    /**
     * 蓝牙设备连接监听
     */
    private BleGattCallback connectCallback = new BleGattCallback() {

        /**
         * 开始连接
         */
        @Override
        public void onStartConnect() {

        }

        /**
         * 连接失败
         * @param bleDevice 连接设备
         * @param exception 异常信息
         */
        @Override
        public void onConnectFail(BleDevice bleDevice, BleException exception) {
            if (!isOpenBle()) {
                connectListener.onBleDisable();
            } else if (connectListener != null) {
                connectListener.onConnectFail(bleDevice, exception);
            }
        }

        /**
         * 连接成功
         * @param bleDevice 连接成功的设备
         * @param gatt 蓝牙gatt
         * @param status 状态码
         */
        @Override
        public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
            if (connectListener != null) {
                connectListener.onConnectSuccess(bleDevice, gatt);
            }
        }

        /**
         * 断开连接
         * @param isActiveDisConnected 是否是之前自己要求主动断开的
         * @param device 断开设备
         * @param gatt gatt
         * @param status 状态
         */
        @Override
        public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
            if (!isOpenBle()) {
                connectListener.onBleDisable();
            } else if (connectListener != null) {
                connectListener.onDisconnect(isActiveDisConnected, device);
            }
        }
    };

    /**
     * 手动断开设备连接
     *
     * @param bleDevice
     */
    public void disconnect(BleDevice bleDevice) {
        bleManager.disconnect(bleDevice);
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


    public void setMTU(BleDevice device, int mtu, MTUSetListener mtuSetListener) {
        bleManager.setMtu(device, mtu, new BleMtuChangedCallback() {
            @Override
            public void onSetMTUFailure(BleException exception) {
                if (mtuSetListener != null) {
                    mtuSetListener.setFail(exception.getDescription());
                }
            }

            @Override
            public void onMtuChanged(int mtu) {
                if (mtuSetListener != null) {
                    mtuSetListener.setSuccess(mtu);
                }
            }
        });
    }


    /**
     * 对外暴露的接口,处理蓝牙的细分回调,如连接,扫描等操作 START
     */

    public interface MTUSetListener {
        void setFail(String err);

        void setSuccess(int mtu);
    }

    /*----------------------------------扫描并连接的回调 start------------------------------*/
    public interface BleHandleListener {

        void onScanStarted(boolean success);

        void onScanFinished(BleDevice bleDevice);

        void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status);

        void onConnectFailed(BleDevice bleDevice, String description);

        void onDisconnect(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt);

        void onBleDisable();
    }

    /*----------------------------------扫描并连接的回调 end------------------------------*/

    /**
     * 设备通知的回调
     *
     * @param charactoristicChangeListener
     */
    public void setCharacteristicChangeListener(CharacteristicChangeListener charactoristicChangeListener) {
        this.characteristicChangeListener = charactoristicChangeListener;
    }

    private CharacteristicChangeListener characteristicChangeListener;

    public interface CharacteristicChangeListener {
        void onCharacteristicChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
    }

    /*----------------------------------单独扫描的回调 start------------------------------*/
    public interface BleScanListener {
        void onScanFinished(List<BleDevice> bleDeviceList);
        void onScanStart();
        void onScanning(BleDevice device);
        void onBleDisable();
    }
    /*----------------------------------单独扫描的回调 end------------------------------*/


    /*----------------------------------单独连接的回调 start------------------------------*/
    private BleConnectListener connectListener;

    public interface BleConnectListener {
        void onConnectSuccess(BleDevice device, BluetoothGatt gatt);

        void onConnectFail(BleDevice device, BleException e);

        void onBleDisable();

        void onDisconnect(boolean isActiveDisConnected, BleDevice device);
    }

    /*----------------------------------单独连接的回调 end------------------------------*/

    /**
     * 读取设备
     *
     * @param bleDevice
     * @param readListener
     */
    public void read(BleDevice bleDevice, ReadListener readListener) {
        BleManager.getInstance().read(
                bleDevice,
                uuidHelper.getServiceUuid(),
                uuidHelper.getReadChaUuid(),
                new BleReadCallback() {
                    @Override
                    public void onReadSuccess(byte[] data) {//对应了onCharacteristicRead的回调
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


    //读操作监听回调
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
    public void setNotify(BleDevice bleDevice, BleNotifyListener listener) {
        if (uuidHelper == null) uuidHelper = new BleUuidHelper();
        BleManager.getInstance().notify(bleDevice, uuidHelper.getServiceUuid(), uuidHelper.getNotiyUuid(), false, new BleNotifyCallback() {
            // 打开通知操作成功
            @Override
            public void onNotifySuccess() {//对应了onDescriptorWrite的回调
                if (listener != null) {
                    listener.onNotifySuccess();
                }
            }

            // 打开通知操作失败
            @Override
            public void onNotifyFailure(BleException exception) {
                if (listener != null) {
                    listener.onNotifyFailed(exception);
                }
            }

            // 打开通知后，设备发过来的数据将在这里出现
            @Override
            public void onCharacteristicChanged(String mac,byte[] data) {//对应了onCharacteristicChanged的回调
                if (listener != null) {
                    listener.onCharacteristicChanged(mac,data);
                }
            }
        });
    }

    public void stopNotify(BleDevice device){
        if (uuidHelper == null) uuidHelper = new BleUuidHelper();
        BleManager.getInstance().stopNotify(device,uuidHelper.getServiceUuid(),uuidHelper.getNotiyUuid());
    }


    public interface BleNotifyListener {
        void onNotifySuccess();

        void onNotifyFailed(BleException e);

        void onCharacteristicChanged(String mac,byte[] data);
    }


    /**
     * 写数据的回调,默认大于20字节时，会分割数据，大于20字节直接是默认写完一次后回调了onCharacteristicWrite后，就继续发下一个数据了。
     *
     * @param bleDevice 蓝牙设备
     * @param dates     传输的字节数据
     * @param listener  监听回调
     */
    public void write(BleDevice bleDevice, byte[] dates, WriteListener listener) {
        BleManager.getInstance().write(
                bleDevice,
                uuidHelper.getServiceUuid(),
                uuidHelper.getWriteUuid(),
                dates,
                new BleWriteCallback() {
                    @Override
                    public void onWriteSuccess(int current, int total, byte[] justWrite) {//对应了onCharacteristicWrite的回调
                        listener.onWriteSuccess(current, total, justWrite);
                    }

                    @Override
                    public void onWriteFailure(BleException exception) {
                        // 发送数据到设备失败
                        listener.onWriteFailure(exception);
                    }
                });
    }

    /**
     * 写数据的回调,默认大于20字节时，会分割数据，大于20字节直接是默认写完一次后回调了onCharacteristicWrite后，就继续发下一个数据了。
     *
     * @param bleDevice           蓝牙设备
     * @param intervalBetweenTime 两包之间间隔时间  ms
     * @param dates               传输的字节数据
     * @param listener            监听回调
     */
    public void write(BleDevice bleDevice, long intervalBetweenTime, byte[] dates, WriteListener listener) {
        BleManager.getInstance().write(
                bleDevice,
                uuidHelper.getServiceUuid(),
                uuidHelper.getWriteUuid(),
                dates,
                intervalBetweenTime,
                new BleWriteCallback() {
                    @Override
                    public void onWriteSuccess(int current, int total, byte[] justWrite) {//对应了onCharacteristicWrite的回调
                        listener.onWriteSuccess(current, total, justWrite);
                    }

                    @Override
                    public void onWriteFailure(BleException exception) {
                        // 发送数据到设备失败
                        listener.onWriteFailure(exception);
                    }
                });
    }

    /**
     * 写数据的回调,默认大于20字节时，会分割数据，大于20字节直接是默认写完一次后回调了onCharacteristicWrite后，就继续发下一个数据了。
     *
     * @param bleDevice 蓝牙设备
     * @param dates     传输的字节数据
     * @param listener  监听回调
     */
    public void write(BleDevice bleDevice, byte[] dates, boolean nextPacketSuccess, long betweenPacketInterval, WriteListener listener) {
        BleManager.getInstance().setIntervalBetweenPacket(betweenPacketInterval);
        BleManager.getInstance().setWhenNextPacketSuccess(nextPacketSuccess);
        BleManager.getInstance().write(
                bleDevice,
                uuidHelper.getServiceUuid(),
                uuidHelper.getWriteUuid(),
                dates,
                new BleWriteCallback() {
                    @Override
                    public void onWriteSuccess(int current, int total, byte[] justWrite) {//对应了onCharacteristicWrite的回调
                        listener.onWriteSuccess(current, total, justWrite);
                    }

                    @Override
                    public void onWriteFailure(BleException exception) {
                        // 发送数据到设备失败
                        listener.onWriteFailure(exception);
                    }
                });
    }

    //写数据的监听
    public interface WriteListener {
        /**
         * 写入操作
         *
         * @param current   当前包
         * @param total     总大小
         * @param justWrite 每一包
         */
        void onWriteSuccess(int current, int total, byte[] justWrite);

        void onWriteFailure(BleException exception);
    }


    /**
     * 读取信号强度的回调
     *
     * @param bleDevice
     * @param rssiListener
     */
    public void readRssi(BleDevice bleDevice, RemoteRssiListener rssiListener) {
        BleManager.getInstance().readRssi(
                bleDevice,
                new BleRssiCallback() {

                    @Override
                    public void onRssiFailure(BleException exception) {
                        // 读取设备的信号强度失败
                        rssiListener.onRssiFailure(exception);
                    }

                    @Override
                    public void onRssiSuccess(int rssi) {//对应了onReadRemoteRssi的回调
                        // 读取设备的信号强度成功
                        rssiListener.onRemoteRssi(rssi);
                    }
                });
    }


    public interface RemoteRssiListener {
        void onRemoteRssi(int rssi);

        void onRssiFailure(BleException exception);
    }


    /**
     * 初始化fastblue配置参数
     *
     * @param application
     */
    private void initBle(Application application) {
        bleManager.init(application);
        bleManager.enableLog(true)
                .setReConnectCount(reconnectCount, 5000)
                .setConnectOverTime(20000)
                .setOperateTimeout(operateTime);
    }

    /**
     * 配置扫描规则
     *
     * @param isFuzzy 是否模糊扫描
     * @param address 需要扫描的mac地址
     * @param name    需要扫描的蓝牙名称
     * @return
     */
    public BleScanRuleConfig scanRule(boolean isFuzzy, String address, String name,boolean isAutoConnect) {
        BleScanRuleConfig ruleConfig = new BleScanRuleConfig.Builder()
                .setServiceUuids(null)
                .setDeviceMac(address)
                .setDeviceName(isFuzzy, name)
                .setAutoConnect(isAutoConnect)
                .setScanTimeOut(10000)
                .build();
        return ruleConfig;
    }

    /**
     * BluetoothHelper初始化
     *
     * @param application
     * @return
     */
    private void initProperties(Application application) {
        initBle(application);
    }
}
