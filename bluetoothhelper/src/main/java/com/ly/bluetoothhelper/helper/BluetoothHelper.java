//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.ly.bluetoothhelper.helper;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.support.v4.app.Fragment;
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
import com.ly.bluetoothhelper.utils.TransformUtils;

import java.util.List;
import java.util.UUID;

public class BluetoothHelper {
    private BleManager bleManager = BleManager.getInstance();
    private BleUuidHelper uuidHelper;
    private BleGattCallback connectCallback = new BleGattCallback() {
        public void onStartConnect() {
        }

        public void onConnectFail(BleDevice bleDevice, BleException exception) {
            if (BluetoothHelper.this.connectListener != null) {
                BluetoothHelper.this.connectListener.onConnectFail(bleDevice, exception);
            }

        }

        public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
            Log.e("conn----", bleDevice.getMac() + "/" + bleDevice.getName());
            if (BluetoothHelper.this.connectListener != null) {
                BluetoothHelper.this.connectListener.onConnectSuccess(bleDevice, gatt);
            }

        }

        public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
        }
    };
    private BluetoothHelper.BleConnectListener connectListener;

    public BluetoothHelper(Application application) {
        this.initProperties(application);
    }

    public BleManager getBleManager() {
        return this.bleManager;
    }

    public static boolean isSupportBle() {
        return BleManager.getInstance().isSupportBle();
    }

    public static void enableBle() {
        BleManager.getInstance().enableBluetooth();
    }

    public static void openBle(Fragment fragment, int reqeustCode) {
        Intent enableBtIntent = new Intent("android.bluetooth.adapter.action.REQUEST_ENABLE");
        fragment.startActivityForResult(enableBtIntent, reqeustCode);
    }

    public static void openGPS(Fragment fragment, int requestcode) {
        Intent locationIntent = new Intent("android.settings.LOCATION_SOURCE_SETTINGS");
        fragment.startActivityForResult(locationIntent, requestcode);
    }

    public static void checkGpsAndBle(Context context, BluetoothHelper.OpenListener listener) {
        boolean isOpenGps = isLocationProviderEnabled(context);
        boolean isOpenBle = isOpenBle();
        listener.open(isOpenBle, isOpenGps);
    }

    @SuppressLint("WrongConstant")
    private static boolean isLocationProviderEnabled(Context context) {
        try {
            LocationManager locationManager = (LocationManager)context.getSystemService("location");
            boolean gpsProvider = locationManager.isProviderEnabled("gps");
            return gpsProvider;
        } catch (Exception var3) {
            return false;
        }
    }

    public static boolean isOpenBle() {
        return BleManager.getInstance().isBlueEnable();
    }

    public static boolean isConnected(String mac) {
        List<BleDevice> allConnectedDevice = BleManager.getInstance().getAllConnectedDevice();
        return BleManager.getInstance().isConnected(mac);
    }

    public static boolean isConnected(BleDevice bleDevice) {
        return BleManager.getInstance().isConnected(bleDevice);
    }

    public static List<BleDevice> getConnectedDeviceList() {
        return BleManager.getInstance().getAllConnectedDevice();
    }

    public static void closeBle() {
        BleManager.getInstance().disableBluetooth();
    }

    public BluetoothHelper initUuid(UUID[] service_uuids, String service_uuid, String notify_uuid, String read_uuid, String read_cha_uuid, String write_uuid, String write_cha_uuid) {
        this.uuidHelper = (new BleUuidHelper.Builder()).setServiceUuids(service_uuids).setServiceUuid(service_uuid).setNotiyUuid(notify_uuid).setReadUuid(read_uuid).setReadChaUuid(read_cha_uuid).setWriteUuid(write_uuid).setWriteChaUuid(write_cha_uuid).build();
        return this;
    }

    public void scanAndConnect(boolean isFuzzy, String address, String name, final BluetoothHelper.BleHandleListener handleListener) {
        this.bleManager.initScanRule(this.scanRule(isFuzzy, address, name));
        this.bleManager.scanAndConnect(new BleScanAndConnectCallback() {

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
                if (characteristicChangeListener!=null){
                    characteristicChangeListener.onCharacteristicChange(gatt,characteristic);
                }
            }

            public void onScanFinished(BleDevice bleDevice) {
                Log.e("device----",bleDevice+"");
                if (handleListener != null) {
                    handleListener.onScanFinished(bleDevice);
                }

            }

            public void onStartConnect() {
            }

            public void onConnectFail(BleDevice bleDevice, BleException exception) {
                if (handleListener != null) {
                    handleListener.onConnectFailed(bleDevice, exception.getDescription());
                }

            }

            public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
                if (handleListener != null) {
                    handleListener.onConnectSuccess(bleDevice, gatt, status);
                }

            }

            public void onDisConnected(boolean isActiveDisConnected, BleDevice device, BluetoothGatt gatt, int status) {
                if (handleListener != null) {
                    handleListener.onDisconnect(device, gatt);
                }

            }

            public void onScanStarted(boolean success) {
                if (handleListener != null) {
                    handleListener.onScanStarted(success);
                }

            }

            public void onScanning(BleDevice bleDevice) {
                if (bleDevice != null) {
                    BleLog.e("found device===" + bleDevice.getMac() + "/" + bleDevice.getName());
                }

            }
        });
    }

    public void scan(final BluetoothHelper.BleScanListener scanListener) {
        this.bleManager.scan(new BleScanCallback() {
            public void onScanFinished(List<BleDevice> scanResultList) {
                if (scanListener != null) {
                    scanListener.onScanFinished(scanResultList);
                }

            }

            public void onScanStarted(boolean success) {
            }

            public void onScanning(BleDevice bleDevice) {
            }
        });
    }

    public void cancelScan() {
        this.bleManager.cancelScan();
    }

    public void connect(String mac, String name, BluetoothHelper.BleConnectListener connectListener) {
        this.connectListener = connectListener;
        this.bleManager.connect(mac, this.connectCallback);
    }

    public void connect(BleDevice bleDevice, BluetoothHelper.BleConnectListener connectListener) {
        this.connectListener = connectListener;
        this.bleManager.connect(bleDevice, this.connectCallback);
    }

    public void disconnect(BleDevice bleDevice) {
        this.bleManager.disconnect(bleDevice);
    }

    public void read(BleDevice bleDevice, final BluetoothHelper.ReadListener readListener) {
        BleManager.getInstance().read(bleDevice, this.uuidHelper.getServiceUuid(), this.uuidHelper.getReadChaUuid(), new BleReadCallback() {
            public void onReadSuccess(byte[] data) {
                if (readListener != null) {
                    readListener.onReadSuccess(data);
                }

            }

            public void onReadFailure(BleException exception) {
                if (readListener != null) {
                    readListener.onReadFailure(exception);
                }

            }
        });
    }

    public void setNotify(BleDevice bleDevice, final BleNotifyListener listener) {
        if (this.uuidHelper == null) {
            this.uuidHelper = new BleUuidHelper();
        }
        BleManager.getInstance().notify(bleDevice, this.uuidHelper.getServiceUuid(), this.uuidHelper.getNotiyUuid(), new BleNotifyCallback() {
            public void onNotifySuccess() {
                if (listener != null) {
                    listener.onNotifySuccess();
                }

            }

            public void onNotifyFailure(BleException exception) {
                if (listener != null) {
                    listener.onNotifyFailed(exception);
                }

            }

            public void onCharacteristicChanged(byte[] data) {
                if (listener != null) {
                    listener.onCharacteristicChanged(data);
                }

            }
        });
    }

    public void write(BleDevice bleDevice, byte[] dates, final BluetoothHelper.WriteListener listener) {
        BleManager.getInstance().write(bleDevice, this.uuidHelper.getServiceUuid(), this.uuidHelper.getWriteUuid(), dates, new BleWriteCallback() {
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                listener.onWriteSuccess(current, total, justWrite);
                Log.i("writeblll----", TransformUtils.bytesToHexString(justWrite));
            }

            public void onWriteFailure(BleException exception) {
                listener.onWriteFailure(exception);
                Log.e("writeblll---",exception.toString());

            }
        });
    }

    public void readRssi(BleDevice bleDevice, final BluetoothHelper.RemoteRssiListener rssiListener) {
        BleManager.getInstance().readRssi(bleDevice, new BleRssiCallback() {
            public void onRssiFailure(BleException exception) {
                rssiListener.onRssiFailure(exception);
            }

            public void onRssiSuccess(int rssi) {
                rssiListener.onRemoteRssi(rssi);
            }
        });
    }

    private void initBle(Application application) {
        this.bleManager.init(application);
        this.bleManager.enableLog(true).setReConnectCount(5, 5000L).setConnectOverTime(20000L).setOperateTimeout(5000);
    }

    private BleScanRuleConfig scanRule(boolean isFuzzy, String address, String name) {
        BleScanRuleConfig ruleConfig = (new com.clj.fastble.scan.BleScanRuleConfig.Builder()).setServiceUuids((UUID[])null).setDeviceMac(address).setDeviceName(isFuzzy, new String[]{name}).setAutoConnect(true).setScanTimeOut(10000L).build();
        return ruleConfig;
    }

    private void initProperties(Application application) {
        this.initBle(application);
    }

    public interface RemoteRssiListener {
        void onRemoteRssi(int var1);

        void onRssiFailure(BleException var1);
    }

    public interface WriteListener {
        void onWriteSuccess(int var1, int var2, byte[] var3);

        void onWriteFailure(BleException var1);
    }

    public interface BleNotifyListener {
        void onNotifySuccess();

        void onNotifyFailed(BleException var1);

        void onCharacteristicChanged(byte[] var1);
    }

    public interface ReadListener {
        void onReadSuccess(byte[] var1);

        void onReadFailure(BleException var1);
    }

    public interface BleConnectListener {
        void onConnectSuccess(BleDevice var1, BluetoothGatt var2);

        void onConnectFail(BleDevice var1, BleException var2);
    }

    public interface BleScanListener {
        void onScanFinished(List<BleDevice> var1);
    }

    public interface BleHandleListener {
        void onScanStarted(boolean var1);

        void onScanning();

        void onScanFinished(BleDevice var1);

        void onConnectSuccess(BleDevice var1, BluetoothGatt var2, int var3);

        void onConnectFailed(BleDevice var1, String var2);

        void onDisconnect(BleDevice var1, BluetoothGatt var2);
    }

    public void setCharacteristicChangeListener(CharacteristicChangeListener charactoristicChangeListener) {
        this.characteristicChangeListener = charactoristicChangeListener;
    }

    private CharacteristicChangeListener characteristicChangeListener;

    public interface CharacteristicChangeListener{
        void onCharacteristicChange(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic);
    }

    public interface Status {
        int BLE_DISABLE = 8193;
        int BLE_REMOTE_DISABLE = 8194;
        int BLE_NOT_SURPPORT = 8195;
        int BLE_LOCATION_DISABLE = 8196;
        int BLE_TRACKER_NOT_FOUND = 8197;
        int BLE_TRACKER_OUT = 8198;
        int BLE_VIRTUAL_LEASH_OPEN_DIALOG = 8199;
        int BLE_SCAN_AND_CONNECT = 8200;
        int BLE_PERMISSION_GRANT = 8201;
        int BLE_GPS_FORCE_OPEN = 8208;
        int BLE_TRACKER_IN = 8209;
    }

    public interface OpenListener {
        void open(boolean var1, boolean var2);
    }
}
