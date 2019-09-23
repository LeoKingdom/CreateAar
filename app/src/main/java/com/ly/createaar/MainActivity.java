package com.ly.createaar;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.ly.bluetoothhelper.helper.BluetoothHelper;
import com.ly.bluetoothhelper.utils.OrderSetUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private VirtualLeashHelper bluetoothHelper;
    private BluetoothHelper bluetoothHelper1;
    private BleDevice bleDevice;
    private EditText editText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText = findViewById(R.id.edittext);
        bluetoothHelper = VirtualLeashHelper.getInstance().init(getApplication());
        bluetoothHelper1 = new BluetoothHelper(getApplication());
        bluetoothHelper1.initUuid(null,
                "00005500-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5");
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

    private void toast(String msg) {
        Toast.makeText(this, "show---:" + msg, Toast.LENGTH_LONG).show();
    }

    public void read(View view) {

        bluetoothHelper1.read(bleDevice, new BluetoothHelper.ReadListener() {
            @Override
            public void onReadSuccess(byte[] data) {
                toast(TransformUtils.bytesToHexString(data));
            }

            @Override
            public void onReadFailure(BleException exception) {

            }
        });
    }

    public void write(View view) {
        String msg = editText.getText().toString().trim();
//        if (TextUtils.isEmpty(msg)) return;
        byte[] datas = TransformUtils.getHexBytes(msg);
        bluetoothHelper1.write(bleDevice, new byte[]{-85, 0, 0, 1, 1, 32, 2}, new BluetoothHelper.WriteListener() {
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                Log.e("writeSuccess---", "current----" + current + "/total---" + total + "/each---" + TransformUtils.bytesToHexString(justWrite));
            }

            @Override
            public void onWriteFailure(BleException exception) {

            }
        });
    }

    public void notify(View view) {
        try {
            InputStream inputStream = getResources().getAssets().open("new_ota.bin");
            byte[] bytes = TransformUtils.streamToByte(inputStream);
            byte[] bytes1 = TransformUtils.subBytes(bytes, 0, 5);
            int length = bytes.length % 20 == 0 ? (bytes.length / 20) : (bytes.length / 20 + 1);
            byte[] newBytes = new byte[length + bytes.length];
            //由于需要带上序号,因此总的byte数组长度增大
            int totalPackets0 = (bytes.length % 19 == 0) ? (bytes.length / 19) : (bytes.length / 19 + 1);
            byte[] lastBytes = null;
            int num = 0;
            if (bytes.length>4*1024) {
                for (int j = 0; j < totalPackets0; j++) {
//                num = Integer.parseInt(Integer.toHexString(j + 1));
                    byte[] eachHeadBytes = new byte[]{(byte) (Integer.parseInt(Integer.toHexString(j + 1), 16))};
//                if (num == 204) {
//                    num = 0;
//                } else {
//                    num++;
//                }
                    int tem = 0;
                    if (j != totalPackets0 - 1) {
                        tem = 19;
                    } else {
                        tem = bytes.length - 19 * (totalPackets0 - 1);
                    }
                    byte[] eachBytes = TransformUtils.subBytes(bytes, j * 19, tem);
                    byte[] handleBytes = TransformUtils.combineArrays(eachHeadBytes, eachBytes);
                    if (j != 0) {
                        lastBytes = TransformUtils.combineArrays(lastBytes, handleBytes);
                    } else {
                        lastBytes = TransformUtils.combineArrays(handleBytes);
                    }

                    if (j == totalPackets0 - 1) {

                    } else {
                        //每一帧,长度为4kb
                        byte[] eachFrameBytes = TransformUtils.subBytes(bytes, j * 1024, 4*1024);
                    }
                }
                Log.e("newArray---", Arrays.toString(lastBytes));
                Log.e("newArraySize---", lastBytes.length + "");
            }else {

            }
            //总帧数,4kb为一帧
            int totalFrame = (bytes.length / 1024) % 4 != 0 ? ((bytes.length / 1024 / 4) + 1) : (bytes.length / 1024 / 4);
            for (int i = 0; i < totalFrame; i++) {
                int temp = 0;
                if (i != totalFrame - 1) {
                    temp = 4 * 1024;
                } else {
                    temp = bytes.length - 4 * 1024 * (totalFrame - 1);
                }
                //每一帧,长度为4kb
                byte[] eachFrameBytes = TransformUtils.subBytes(bytes, i * 1024, temp);
                int totalPackets = (eachFrameBytes.length % 19 == 0) ? (eachFrameBytes.length / 19) : (eachFrameBytes.length / 19 + 1);
                byte[] handleBytes1 = null;
                for (int j = 0; j < totalPackets; j++) {
                    byte[] eachHeadBytes = new byte[]{(byte) (Integer.parseInt(Integer.toHexString(j + 1), 16))};
                    int tem = 0;
                    if (j != totalPackets - 1) {
                        tem = 19;
                    } else {
                        tem = eachFrameBytes.length - 19 * (totalPackets - 1);
                    }
                    byte[] eachBytes = TransformUtils.subBytes(eachFrameBytes, j * 19, tem);
                    handleBytes1 = TransformUtils.combineArrays(eachHeadBytes, eachBytes);
                    int h = 0;
                    if (i < 1 && j == 0) {
                        Log.e("arr0---", Arrays.toString(TransformUtils.subBytes(eachFrameBytes, 0, 40)));
                        Log.e("arr0---", handleBytes1.length + "");
                        Log.e("arr1---", Arrays.toString(TransformUtils.subBytes(handleBytes1, 0, 20)));
                    }
                }

//                Log.e("eachFrame----" + (i + 1), eachFrameBytes.length + "");
            }
//            Log.e("array0---", bytes.length / 1024.0 + "");
////            Log.e("array00---", ((bytes.length / 1024) % 4 != 0 ? ((bytes.length / 1024 / 4) + 1) : (bytes.length / 1024 / 4)) + "");
//            Log.e("array---", (4 * 1024) / 20 + "/" + bytes.length / 20);
//            Log.e("array1---", Arrays.toString(bytes1));
//            Log.e("array1---", Arrays.toString(TransformUtils.combineArrays(OrderSetUtils.ORDER_OAD, bytes1)));
        } catch (IOException e) {
            e.printStackTrace();
        }

//        bluetoothHelper1.setNotify(bleDevice, new BluetoothHelper.BleNotifyListener() {
//            @Override
//            public void onNotifySuccess() {
//
//            }
//
//            @Override
//            public void onNotifyFailed(BleException e) {
//
//            }
//
//            @Override
//            public void onCharacteristicChanged(byte[] data) {
//                Log.e("notifyData----",TransformUtils.bytes2String(data));
//            }
//        });
    }

    public void scan(View view) {
        bluetoothHelper.openVirtualLeash(true, "01:02:04:05:06:07", "");
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
                this.bleDevice = bleDevice;
                toast(bleDevice.getName() + "/" + bleDevice.getMac());
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
            } else {
                Log.e("reconnectFail---", "ensure  your device and tracker's bluetooth is open ,and tracker is around you");
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
