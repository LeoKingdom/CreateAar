package com.ly.createaar;

import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.ly.bluetoothhelper.helper.BluetoothHelper;
import com.ly.bluetoothhelper.oat.service.OtauBleService;
import com.ly.bluetoothhelper.utils.ActionUtils;
import com.ly.bluetoothhelper.utils.OrderSetUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/24 13:19
 * version: 1.0
 */
public class OTAUpgradeService extends Service {

    private VirtualLeashHelper bluetoothHelper;
    private BluetoothHelper bluetoothHelper1;
    private BleDevice bleDevice;
    private byte[] newPackets;
    private int total;

    public void setScanAndConnectListener(ScanAndConnectListener scanAndConnectListener) {
        this.scanAndConnectListener = scanAndConnectListener;
    }

    private ScanAndConnectListener scanAndConnectListener;
    int currentPacket = 0;
    int totalPacket = 0;


    private void initHelper() {
        bluetoothHelper = VirtualLeashHelper.getInstance().init(getApplication());
        bluetoothHelper1 = new BluetoothHelper(getApplication());
        bluetoothHelper1.initUuid(null,
                "00005500-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5");
    }

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case ActionUtils.ACTION_OTA_ORDER_I:
                    byte[] oadOrder = OrderSetUtils.ORDER_OAD;
                    byte[] headByte = getHeadBytes(null);
                    byte[] checkBytes = TransformUtils.combineArrays(oadOrder, headByte);

                    bluetoothHelper1.write(bleDevice, checkBytes, new BluetoothHelper.WriteListener() {
                        @Override
                        public void onWriteSuccess(int current, int total, byte[] justWrite) {
                            Log.e("writeSuccess---", "current----" + current + "/total---" + total + "/each---" + TransformUtils.bytesToHexString(justWrite));
                        }

                        @Override
                        public void onWriteFailure(BleException exception) {

                        }
                    });
                    break;
                case ActionUtils.ACTION_OTA_DATA_I:
                    newPackets = combinePacket();
                    total = (newPackets.length / 1024) % 4 != 0 ? ((newPackets.length / 1024 / 4) + 1) : (newPackets.length / 1024 / 4);
                    byte[] currPacket = currentPacket(newPackets, currentPacket, total);
                    writePacket(currPacket);
                    break;

                    case ActionUtils.ACTION_OTA_DATA_NEXT:

                        break;

            }
        }
    };

    class LocalBinder extends Binder {
        public OTAUpgradeService getService() {
            return OTAUpgradeService.this;
        }
    }

    private IBinder mBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        initHelper();
        onHandleIntent(intent);
        return super.onStartCommand(intent, flags, startId);
    }


    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        if (Objects.equals(action, ActionUtils.ACTION_DEVICE_SCAN)) {
            String macAddress = intent.getStringExtra("mac_address");
            bluetoothHelper.openVirtualLeash(true, macAddress, "");
            bluetoothHelper.setConnectSuccessListener((bleDevice, gatt) -> {
                if (bleDevice != null) {
                    this.bleDevice = bleDevice;
                    setNotify(bleDevice);
                    handler.obtainMessage(ActionUtils.ACTION_OTA_ORDER_I).sendToTarget();
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_SUCCESS, bleDevice);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bluetoothHelper.setConnectFailListener((bleDevice, description) -> {
                if (bleDevice != null) {
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_FAIL, bleDevice);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bluetoothHelper.setScanFinishListener((bleDevice) -> {
                if (bleDevice != null) {
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_SCAN_SUCCESS, bleDevice);
                    EventBus.getDefault().post(msgBean);
                } else {
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_SCAN_FAIL, null);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bluetoothHelper.setReconnectSuccessListener((bleDevice1 -> {

            }));
        } else if (Objects.equals(action, "OTA_START")) {
            //处理bin文件,转换成byte数组
            byte[] packetsByte = combinePacket();

        }
    }

    private void setNotify(BleDevice bleDevice) {

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
                            handler.obtainMessage(ActionUtils.ACTION_OTA_DATA_I).sendToTarget();
                        }
                    }
                }

                Log.e("notifyData----", TransformUtils.bytesToHexString(data));
            }
        });
    }

    private void writePacket(byte[] eachFrameBytes) {

        Log.e("device----",bleDevice+"/"+Arrays.toString(eachFrameBytes));
        bluetoothHelper1.write(bleDevice, eachFrameBytes, new BluetoothHelper.WriteListener() {
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

    private byte[] getHeadBytes(File file) {
        byte[] bytes = null;
        try {
            InputStream inputStream = getResources().getAssets().open("ap");
            bytes = TransformUtils.streamToByte(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] bytes1 = TransformUtils.subBytes(bytes, 0, 5);
        return bytes1;
    }

    private byte[] combinePacket() {
        //合并后的字节数组
        byte[] lastBytes = null;
        try {
            InputStream inputStream = getResources().getAssets().open("ap");
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

    private void toast(String msg) {
        Toast.makeText(this, "show---:" + msg, Toast.LENGTH_LONG).show();
    }

}
