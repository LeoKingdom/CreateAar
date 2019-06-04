package com.ly.createaar;

import android.Manifest;
import android.bluetooth.BluetoothGatt;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.clj.fastble.data.BleDevice;
import com.clj.fastble.utils.BleLog;
import com.ly.bluetoothhelper.BluetoothHelper;

public class MainActivity extends AppCompatActivity {

    private BluetoothHelper bluetoothHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bluetoothHelper = BluetoothHelper.getBluetoothHelper().initProperties(getApplication());
//        myHandler = new MyHandler(this);
        checkLocation();
    }

    private boolean checkLocation() {
        if (Build.VERSION.SDK_INT >= 23) {
            int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                        },
                        0x0010
                );
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    public void scan(View view) {
        bluetoothHelper.scanAndConnect(true,null, "Family watch ");
        bluetoothHelper.setBleHandleListener(bleHandleListener);
        bluetoothHelper.setReconnectListener(new BluetoothHelper.BleReconnectListener() {
            @Override
            public void onReconnectSuccess(BleDevice device) {
                Log.e("reConnOk===",device.getName()+"");
            }

            @Override
            public void onReconnectFail(BleDevice bleDevice) {
                Log.e("reConnFail===",bleDevice.getName()+"");
            }

            @Override
            public void onBleDisable() {

            }

            @Override
            public void onDeviceAway(BleDevice device) {
                Log.e("deviceAway===",device.getName()+"");
            }
        });
    }

    BluetoothHelper.BleHandleListener bleHandleListener = new BluetoothHelper.BleHandleListener() {
        @Override
        public void onScanStarted(boolean success) {
            if (success) {
                // Todo show searching dialog
//                SendEventBusBean eventBusBean=new SendEventBusBean();
//                eventBusBean.setAction("ble.scan.start");
//                EventBusUtils.post(eventBusBean);
//                Intent intent = new Intent();
//                intent.setPackage("com.trackerandroid.trackerandroid"); //9.0静态注册的广播必须为定向广播,否则无法接收
//                intent.setAction("ble.scan.start");
//                sendBroadcast(intent);
            }
        }

        @Override
        public void onScanning() {

        }

        @Override
        public void onScanFinished(BleDevice bleDevice) {
//            SendEventBusBean intent = new SendEventBusBean();
//
//            intent.setAction("ble.scan.finish");
//            intent.setBleDevice( bleDevice);
//            EventBusUtils.post(intent);

            if (bleDevice == null) {
                // Todo no device found,hide searching dialog
                BleLog.e("no device was found");

            } else {
                BleLog.e("scanDevice---: " + bleDevice.getName() + "/" + bleDevice.getMac());
            }
        }

        @Override
        public void onConnectSuccess(BleDevice bleDevice, BluetoothGatt gatt, int status) {
            BleLog.e("connDevice---: " + bleDevice.getName() + "/" + bleDevice.getMac());
//            connDeviceMap.put(bleDevice.getMac(),bleDevice);
//            SendEventBusBean intent = new SendEventBusBean();
//            intent.setAction("ble.conn.success");
//            intent.setBleDevice( bleDevice);
//            EventBusUtils.post(intent);

        }

        @Override
        public void onConnectFailed(BleDevice bleDevice, String description) {
//            SendEventBusBean intent = new SendEventBusBean();
//            intent.setAction("ble.conn.success");
//            intent.setBleDevice( bleDevice);
//            EventBusUtils.post(intent);

        }

        @Override
        public void onDisconnect(BleDevice device, BluetoothGatt gatt) {
//            SendEventBusBean intent = new SendEventBusBean();
//            intent.setAction("ble.disconnect");
//            intent.setBleDevice( device);
//            EventBusUtils.post(intent);
//            if (device!=null) {
//                disConnDeviceMap.put(device.getMac(),device);
//                connDeviceMap.remove(device);
//                Intent intent = new Intent();
//                intent.setPackage("com.trackerandroid.trackerandroid");
//                intent.setAction("ble.disconnect");
//                intent.putExtra("device", device);
//                sendBroadcast(intent);
//            }
        }
    };
}
