package com.ly.createaar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
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
import com.ly.bluetoothhelper.utils.ActionUtils;
import com.ly.bluetoothhelper.utils.OrderSetUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends AppCompatActivity {

    private VirtualLeashHelper bluetoothHelper;
    private BluetoothHelper bluetoothHelper1;
    private BleDevice bleDevice;
    private EditText editText;
    private boolean ota_ready = false;
    private boolean ota_order_send = false;
    private Queue<byte[]> packetQueue = new LinkedList<>();
    private int currentPacket = 0;
    private OTAUpgradeService otaUpgradeService;
    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    break;
            }
        }
    };

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            OTAUpgradeService.LocalBinder binder = (OTAUpgradeService.LocalBinder) service;
            otaUpgradeService = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EventBus.getDefault().register(this);
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

//        if (bleDevice == null) {
//            toast("设备未连接");
//            return;
//        }
//        byte[] bytes = null;
//        byte[] headBytes = null;
//        try {
//            InputStream inputStream = getResources().getAssets().open("ap");
//            bytes = TransformUtils.streamToByte(inputStream);
//            headBytes = TransformUtils.subBytes(bytes, 0, 5);
//
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//
//        byte[] orderBytes = null;
//        if (view.getId() == R.id.ota) {
//            ota_order_send = true;
//            orderBytes = TransformUtils.combineArrays(OrderSetUtils.ORDER_OAD, headBytes);
//        } else if (view.getId() == R.id.version) {
//            orderBytes = OrderSetUtils.ORDER_VERSION;
//        }
//
//        bluetoothHelper1.write(bleDevice, orderBytes, new BluetoothHelper.WriteListener() {
//            @Override
//            public void onWriteSuccess(int current, int total, byte[] justWrite) {
//                Log.e("writeSuccess---", "current----" + current + "/total---" + total + "/each---" + TransformUtils.bytesToHexString(justWrite));
//            }
//
//            @Override
//            public void onWriteFailure(BleException exception) {
//
//            }
//        });

//        bluetoothHelper1.read(bleDevice, new BluetoothHelper.ReadListener() {
//            @Override
//            public void onReadSuccess(byte[] data) {
//                toast(TransformUtils.bytesToHexString(data));
//            }
//
//            @Override
//            public void onReadFailure(BleException exception) {
//
//            }
//        });
    }

    public void write(View view) {
        if (bleDevice == null) {
            toast("设备未连接");
            return;
        }
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

    private byte[] currentPacket(byte[] datas, int curr, int total) {
        byte[] eachFrameBytes = null;
        if (curr == total - 1) {
            //最后一帧,不一定是4kb
            int lastPacketLenght = datas.length - (total - 1) * 4 * 1024;
            eachFrameBytes = TransformUtils.subBytes(datas, curr * 1024, lastPacketLenght);
        } else {
            //每一帧,长度为4kb
            eachFrameBytes = TransformUtils.subBytes(datas, curr * 1024, 4 * 1024);
        }
        return eachFrameBytes;
    }

    private byte[] combinePacket() {
        //合并后的字节数组
        byte[] lastBytes = null;
        try {
            InputStream inputStream = getResources().getAssets().open("new_ota.bin");
            byte[] bytes = TransformUtils.streamToByte(inputStream);
            byte[] bytes1 = TransformUtils.subBytes(bytes, 0, 5);
            int length = bytes.length % 20 == 0 ? (bytes.length / 20) : (bytes.length / 20 + 1);
            byte[] newBytes = new byte[length + bytes.length];
            //由于需要带上序号,因此总的byte数组长度增大
            int totalPackets0 = (bytes.length % 19 == 0) ? (bytes.length / 19) : (bytes.length / 19 + 1);
            int num = 0;
            int currentPacket = 0;
            if (bytes.length > 4 * 1024) {
                for (int j = 0; j < totalPackets0; j++) {
                    num++;
                    if (num == 205) {
                        num = 1;
                    }
                    byte[] eachHeadBytes = new byte[]{(byte) (Integer.parseInt(Integer.toHexString(num), 16))};
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
                }
            } else {
                for (int j = 0; j < totalPackets0; j++) {
                    byte[] eachHeadBytes = new byte[]{(byte) (Integer.parseInt(Integer.toHexString(j + 1), 16))};
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
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return lastBytes;
    }

    public void notify(View view) {
        if (bleDevice == null) {
            toast("设备未连接");
            return;
        }

        bluetoothHelper1.setNotify(bleDevice, new BluetoothHelper.BleNotifyListener() {
            @Override
            public void onNotifySuccess() {

            }

            @Override
            public void onNotifyFailed(BleException e) {

            }

            @Override
            public void onCharacteristicChanged(byte[] data) {
                if (data.length > 3) {
                    //module Id                                     //event Id
                    if (data[data.length - 2] == (byte) 0x02 && data[data.length - 3] == (byte) 0x20) { //ota校验命令
                        if (data[data.length - 1] == (byte) 0xFF) {
                            Log.e("ota-check----", "check fail");
                        } else {
                            //校验bin包成功,可以开始传输ota包
                            handler.obtainMessage(0).sendToTarget();
                        }
                    }
                }

                Log.e("notifyData----", TransformUtils.bytesToHexString(data));
            }
        });
    }

    public void scan(View view) {
        Intent intent = new Intent(this, OTAUpgradeService.class);
        intent.setAction(ActionUtils.ACTION_DEVICE_SCAN);
        intent.putExtra("mac_address", "01:02:04:05:06:07");
        startService(intent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void deverse(MsgBean msgBean) {
        if (msgBean != null) {
            Object o = msgBean.getObject();
            String msg = msgBean.getMsg();
            if (o instanceof BleDevice) {
                BleDevice device = (BleDevice) o;
                if (msg.equals(ActionUtils.ACTION_CONNECT_SUCCESS)) {
                    connectSuccess(device);
                } else if (msg.equals(ActionUtils.ACTION_CONNECT_FAIL)) {
                    connetFail(device);
                } else if (msg.equals(ActionUtils.ACTION_SCAN_SUCCESS)) {
                    scanSuccess(device);
                } else if (msg.equals(ActionUtils.ACTION_SCAN_FAIL)) {
                    scanFail();
                }
            }
        }

    }


    public void scanFail() {

    }


    public void scanSuccess(BleDevice bleDevice) {

    }


    public void connetFail(BleDevice b) {

    }

    private void setNotify() {
        if (bleDevice == null) {
            toast("设备未连接");
            return;
        }

        bluetoothHelper1.setNotify(bleDevice, new BluetoothHelper.BleNotifyListener() {
            @Override
            public void onNotifySuccess() {

            }

            @Override
            public void onNotifyFailed(BleException e) {

            }

            @Override
            public void onCharacteristicChanged(byte[] data) {
                if (data.length > 3) {
                    //module Id                                     //event Id
                    if (data[data.length - 2] == (byte) 0x02 && data[data.length - 3] == (byte) 0x20) { //ota校验命令
                        if (data[data.length - 1] == (byte) 0xFF) {
                            Log.e("ota-check----", "check fail");
                        } else {
                            //校验bin包成功,可以开始传输ota包
                            handler.obtainMessage(ActionUtils.ACTION_OTA_ORDER_I).sendToTarget();
                        }
                    }
                }

                Log.e("notifyData----", TransformUtils.bytesToHexString(data));
            }
        });
    }


    public void connectSuccess(BleDevice bleDevice) {
        this.bleDevice = bleDevice;
        setNotify();
        sendOtaOrder();
        Log.e("device---", bleDevice.getMac() + "");
    }

    private void sendOtaOrder() {
        if (bleDevice == null) {
            toast("设备未连接");
            return;
        }
        byte[] bytes = null;
        byte[] headBytes = null;
        try {
            InputStream inputStream = getResources().getAssets().open("ap");
            bytes = TransformUtils.streamToByte(inputStream);
            headBytes = TransformUtils.subBytes(bytes, 0, 5);

        } catch (IOException e) {
            e.printStackTrace();
        }

        byte[] orderBytes = null;
        ota_order_send = true;
        orderBytes = TransformUtils.combineArrays(OrderSetUtils.ORDER_OAD, headBytes);
        bluetoothHelper1.write(bleDevice, orderBytes, new BluetoothHelper.WriteListener() {
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                Log.e("writeSuccess---", "current----" + current + "/total---" + total + "/each---" + TransformUtils.bytesToHexString(justWrite));
            }

            @Override
            public void onWriteFailure(BleException exception) {

            }
        });
    }
}
