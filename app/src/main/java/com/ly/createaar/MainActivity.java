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

    private VirtualLeashHelper bluetoothHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bluetoothHelper = VirtualLeashHelper.getInstance().init(getApplication());
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
        bluetoothHelper.openVirtualLeash(true, null, "Family watch ");
        bluetoothHelper.openReconnectListen();
        bluetoothHelper.setScanStartListener(() -> {
            Log.e("scanStart---", "run");
        });
        bluetoothHelper.setScanFinishListener((bleDevice) -> {
            if (bleDevice != null) {
                Log.e("scanFinish---", bleDevice.getName() + "");
            }
        });

        bluetoothHelper.setConnectSuccessListener((bleDevice, gatt) -> {
            if (bleDevice != null) {
                Log.e("connectSuccess---", bleDevice.getName() + "");
            }
        });

        bluetoothHelper.setConnectFailListener((bleDevice, description) -> {
            if (bleDevice != null) {
                Log.e("connectFail---", bleDevice.getName() + "");
            }
        });

        bluetoothHelper.setReconnectSuccessListener((bleDevice) -> {
            if (bleDevice != null) {
                Log.e("reconnectSuccess---", bleDevice.getName() + "");
            }
        });

        bluetoothHelper.setReconnectFailListener((bleDevice -> {
            if (bleDevice != null) {
                Log.e("reconnectFail---", bleDevice.getName() + "");
            }else {
                Log.e("reconnectFail---",  "ensure  your device and tracker's bluetooth is open ,and tracker is around you");
            }
        }));
        bluetoothHelper.setDeviceSelfDisableListener(() -> {
            Log.e("bluetooth---", "is close");
        });
        bluetoothHelper.setDeviceAwayListener((bleDevice) -> {
            if (bleDevice != null) {
                Log.e("tracker---", bleDevice.getName() + "is run away");
            }
        });
    }


}
