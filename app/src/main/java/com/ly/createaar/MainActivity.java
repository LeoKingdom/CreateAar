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
import android.widget.TextView;
import android.widget.Toast;

import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.ly.bluetoothhelper.helper.BluetoothHelper;
import com.ly.bluetoothhelper.utils.ActionUtils;
import com.ly.bluetoothhelper.utils.CRCCheckUtils;
import com.ly.bluetoothhelper.utils.DataPacketUtils;
import com.ly.bluetoothhelper.utils.OrderSetUtils;
import com.ly.bluetoothhelper.utils.SharePreferenceUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;
import com.ly.bluetoothhelper.widget.LoadingWidget;
import com.ly.bluetoothhelper.widget.ProgressDialogWidget;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MainActivity extends AppCompatActivity {

    private VirtualLeashHelper bluetoothHelper;
    private BluetoothHelper bluetoothHelper1;
    private BleDevice bleDevice;
    private EditText editText;
    private TextView scanAndConnTv;
    private ProgressDialogWidget progressDialogWidget;
    private LoadingWidget loadingWidget;
    private TextView textView;
    private boolean ota_ready = false;
    private boolean ota_order_send = false;
    private Queue<byte[]> packetQueue = new LinkedList<>();
    private int totalFrame; //总帧数
    int currentFrame = 1; //当前帧
    private byte[] totalPacketBytes = null; //文件字节流
    private byte[] initialTotalBytes = null;
    private int CURRENT_WHAT = -1;
    private OTAUpgradeService otaUpgradeService;
    private int currentPacket = 0;
    private int cPacket = 0;
    private boolean isBin = false;

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {

                case ActionUtils.ACTION_OTA_NOTIFY:
                    break;

                case ActionUtils.ACTION_OTA_ORDER_I: //发送ota升级命令(含校验bin的合法性)
                    byte[] oadOrder = OrderSetUtils.ORDER_OAD;
                    byte[] headByte = TransformUtils.subBytes(totalPacketBytes, 1, 5);
                    byte[] checkBytes = TransformUtils.combineArrays(oadOrder, headByte);
                    bluetoothHelper1.write(bleDevice, checkBytes, new BluetoothHelper.WriteListener() {
                        @Override
                        public void onWriteSuccess(int current, int total, byte[] justWrite) {
                            Log.e("writeSuccess---", "current----" + current + "/total---" + total + "/each---" + TransformUtils.bytesToHexString(justWrite));
                        }

                        @Override
                        public void onWriteFailure(BleException exception) {
                            Log.e("writeException----", "" + exception.toString());
                        }
                    });
                    break;
                case ActionUtils.ACTION_OTA_DATA_HEAD_I: //发送ota数据帧帧头
                    byte[] dataHeadBytes = DataPacketUtils.eachFrameFirstPacket(totalPacketBytes.length, totalFrame, currentFrame);
                    bluetoothHelper1.write(bleDevice, dataHeadBytes, new BluetoothHelper.WriteListener() {
                        @Override
                        public void onWriteSuccess(int current, int total, byte[] justWrite) {
                            if (CURRENT_WHAT == ActionUtils.ACTION_OTA_DATA_HEAD_I) { //数据头写入成功
                                CURRENT_WHAT = ActionUtils.ACTION_OTA_DATA_DATA_I;
                                handler.obtainMessage(ActionUtils.ACTION_OTA_DATA_DATA_I).sendToTarget();
                            }
//                            Log.e("writeSuccess---", "current----" + current + "/total---" + total + "/each---" + TransformUtils.bytesToHexString(justWrite));
                        }

                        @Override
                        public void onWriteFailure(BleException exception) {
                            Log.e("writeException----", "" + exception.toString());
                        }
                    });
                    break;

                case ActionUtils.ACTION_OTA_DATA_DATA_I: //发送ota数据帧
                    if (currentFrame <= totalFrame) {
                        byte[] currPacket = DataPacketUtils.currentPacket(totalPacketBytes, currentFrame, totalFrame);
//                        Log.e("nnnn----",Arrays.toString(initialTotalBytes));
                        byte[] initialCurrentFrame = DataPacketUtils.crcFramePacket(initialTotalBytes, currentFrame, totalFrame);
                        byte crcByte = CRCCheckUtils.calcCrc8(initialCurrentFrame);
                        if (currentFrame == totalFrame) {
                            if (currPacket.length % 20 == 0) {
                                int length = currPacket.length / 20;
                                currPacket = TransformUtils.combineArrays(currPacket, new byte[]{(byte) length, crcByte});
                            }
                        } else {
                            currPacket = TransformUtils.combineArrays(currPacket, new byte[]{crcByte});
                        }
                        currentPacket = currPacket.length % 20 == 0 ? currPacket.length / 20 : currPacket.length / 20 + 1;
                        progressDialogWidget.getProgressBar().setMax(currentPacket);
                        progressDialogWidget.getProgressNumTv().setText(0 + "%");
                        progressDialogWidget.show();
                        writePacket(currPacket);

                    }
                    break;
                case ActionUtils.ACTION_OTA_DATA_LOSE_I:
                    if (loseList == null) { //完整接收一帧数据
                        currentFrame++;
                        handler.obtainMessage(ActionUtils.ACTION_OTA_DATA_DATA_I).sendToTarget();
                    } else {
                        if (loseList.size() == 0) { //丢包过多,需重发此帧
                            handler.obtainMessage(ActionUtils.ACTION_OTA_DATA_DATA_I).sendToTarget();
                        } else {
                            byte[] combineByte = null;
                            for (int i = 0; i < loseList.size(); i++) {
                                byte[] eachPacket = loseList.get(i);
                                if (combineByte == null) {
                                    combineByte = TransformUtils.combineArrays(eachPacket);
                                } else {
                                    combineByte = TransformUtils.combineArrays(combineByte, eachPacket);
                                }
                            }
                            //发送丢失的包,不需要重发帧头
                            writePacket(combineByte);
                        }
                    }
                    break;

            }
        }
    };

    private void writePacket(byte[] eachFrameBytes) {

//        Log.e("device----", bleDevice + "/" + Arrays.toString(eachFrameBytes));
        bluetoothHelper1.write(bleDevice, eachFrameBytes, new BluetoothHelper.WriteListener() {
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                cPacket = current;
                float percent = (float) current / total * 100;
                progressDialogWidget.getProgressBar().setProgress((int) percent);
                progressDialogWidget.getCurrentPacket().setText("当前传送: 第" + currentFrame + "帧,第" + current + "包");
                progressDialogWidget.getProgressNumTv().setText((int) percent + "%");
                if (currentPacket == current) {
                    currentFrame++;
                    CURRENT_WHAT = ActionUtils.ACTION_OTA_DATA_HEAD_I;
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 300);
                }
                Log.e("writeSuccess---", "current----" + current + "/total---" + total + "/each---" + TransformUtils.bytesToHexString(justWrite));
            }

            @Override
            public void onWriteFailure(BleException exception) {

            }
        });
    }

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
    private List<byte[]> loseList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EventBus.getDefault().register(this);
        editText = findViewById(R.id.edittext);
        scanAndConnTv = findViewById(R.id.scan_and_conn);
        progressDialogWidget = findViewById(R.id.progress_dialog);
        loadingWidget = findViewById(R.id.main_loading_widget);
        textView = findViewById(R.id.write_tv);
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
        totalPacketBytes = combinePacket();
        if (totalPacketBytes != null) {
            //总帧数
            totalFrame = (totalPacketBytes.length / 1024) % 4 != 0 ? ((totalPacketBytes.length / 1024 / 4) + 1) : (totalPacketBytes.length / 1024 / 4);
        }
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
        Toast.makeText(this, "" + msg, Toast.LENGTH_LONG).show();
    }

    public void read(View view) {

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
        if (view.getId() == R.id.ota) {
            ota_order_send = true;
            loadingWidget.setLoadingText("正在校验...");
            loadingWidget.show();
            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_ORDER_I, 100);
        } else if (view.getId() == R.id.version) {
            orderBytes = OrderSetUtils.ORDER_VERSION;
            writePacket(orderBytes);
        }

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

    public void clear(View view) {
        editText.setText("");
    }

    public void write(View view) {
        if (bleDevice == null) {
            toast("设备未连接");
            return;
        }
        if (view.getId() == R.id.ota_data) {
            handler.obtainMessage(ActionUtils.ACTION_OTA_DATA_HEAD_I).sendToTarget();
        } else {
            String msg = editText.getText().toString().trim();
//        if (TextUtils.isEmpty(msg)) return;
            byte[] datas = TransformUtils.getHexBytes(msg);
            bluetoothHelper1.write(bleDevice, datas, new BluetoothHelper.WriteListener() {
                @Override
                public void onWriteSuccess(int current, int total, byte[] justWrite) {
                    Log.e("writeSuccess---", "current----" + current + "/total---" + total + "/each---" + TransformUtils.bytesToHexString(justWrite));
                    textView.setText("当前写入: " + TransformUtils.bytesToHexString(datas));
                }

                @Override
                public void onWriteFailure(BleException exception) {

                }
            });
        }
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
            InputStream inputStream = getResources().getAssets().open("ap");
            lastBytes = DataPacketUtils.combinePacket(inputStream);
            InputStream inputStream1 = getResources().getAssets().open("ap");
            initialTotalBytes = TransformUtils.streamToByte(inputStream1);
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
                toast("通知开启成功");
                Log.e("notify---", "success");
            }

            @Override
            public void onNotifyFailed(BleException e) {
                toast("通知开启失败");
                Log.e("notify---", "fail---" + e.getDescription());
            }

            @Override
            public void onCharacteristicChanged(byte[] data) {
                Log.e("notifyData----", TransformUtils.bytesToHexString(data));
                if (data.length > 3) {
                    byte responeByte = data[data.length - 1];
                    byte moduId = data[data.length - 2];
                    byte eventId = data[data.length - 3];
                    //module Id                                     //event Id
                    if (moduId == (byte) 0x02 && eventId == (byte) 0x20) { //ota校验命令
                        if (responeByte == (byte) 0xFF) {
                            Log.e("ota-check----", "check fail");
                        } else {
                            //校验bin包成功,可以开始传输ota包,
                            isBin = true;
                            toast("校验成功");
                            CURRENT_WHAT = ActionUtils.ACTION_OTA_DATA_HEAD_I;
                            loadingWidget.hide();
//                            handler.obtainMessage(ActionUtils.ACTION_OTA_DATA_HEAD_I).sendToTarget();
                        }
                    } else if (moduId == (byte) 0x03 && eventId == (byte) 0x20) { //ota数据包
                        //成功接收完一帧,开始发送下一帧
                        //0xAB 0x00 0x04 0x03  0x01  0x20 0x03  0x03  0x01 0x0A 0x64 收包回复
                        //帧头 数据-长度 总帧 当前帧 MId  EId  丢包数 之后为丢包序号(上限五包,超过填0xFF)
                        //若出现丢包情况,尚未确定是继续下一帧还是先补包
                        List<byte[]> losePacketList = DataPacketUtils.losePacketList(totalPacketBytes, data);
                        loseList = losePacketList;
                        handler.obtainMessage(ActionUtils.ACTION_OTA_DATA_LOSE_I).sendToTarget();

                    }
                }


            }
        });
    }

    public void scan(View view) {
        Intent intent = new Intent(this, OTAUpgradeService.class);
        intent.setAction(ActionUtils.ACTION_DEVICE_SCAN);
        intent.putExtra("mac_address", "01:02:04:05:06:09");
        intent.putExtra("dataByte", totalPacketBytes);
        startService(intent);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void deverse(MsgBean msgBean) {
        if (msgBean != null) {
            Object o = msgBean.getObject();
            String msg = msgBean.getMsg();
            if (o instanceof BleDevice) {
                BleDevice device = (BleDevice) o;
                if (msg.equals(ActionUtils.ACTION_CONNECT_SUCCESS_S)) {
                    connectSuccess(device);
                } else if (msg.equals(ActionUtils.ACTION_CONNECT_FAIL_S)) {
                    connetFail(device);
                } else if (msg.equals(ActionUtils.ACTION_SCAN_SUCCESS_S)) {
                    scanSuccess(device);
                } else if (msg.equals(ActionUtils.ACTION_DISCONNECT_S)) {
                    disconnect(device);
                }
            } else if (o == null) {
                if (msg.equals(ActionUtils.ACTION_SCAN_FAIL_S)) {
                    scanFail();
                }
            }
        }

    }

    private void disconnect(BleDevice device) {
        this.bleDevice = null;
        scanAndConnTv.setText("扫描与连接(已断开)");
        SharePreferenceUtils.setValue(this, "current-frame", cPacket + "," + currentFrame);
    }

    public void scanFail() {
        toast("未发现蓝牙设备,请重启设备再试");
    }


    public void scanSuccess(BleDevice bleDevice) {

    }


    public void connetFail(BleDevice b) {
        toast("连接失败");
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
        scanAndConnTv.setText("扫描与连接(已连接)");
    }

}
