package com.ly.createaar;

import android.annotation.SuppressLint;
import android.app.Service;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
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
import com.ly.bluetoothhelper.utils.ActionUtils;
import com.ly.bluetoothhelper.utils.DataPacketUtils;
import com.ly.bluetoothhelper.utils.OrderSetUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/24 13:19
 * version: 1.0
 * <p>
 * ota升级服务
 */
public class OTAUpgradeService extends Service {

    private VirtualLeashHelper bluetoothHelper; //蓝牙操作辅助类
    private BluetoothHelper bluetoothHelper1; //蓝牙操作类
    private BleDevice bleDevice; //蓝牙设备
    private BluetoothGatt bluetoothGatt;
    private int totalFrame; //总帧数
    int currentFrame = 1; //当前帧
    private byte[] totalPacketBytes = null; //文件字节流
    private int CURRENT_WHAT = -1;
    private List<byte[]> loseList;

    public void setScanAndConnectListener(ScanAndConnectListener scanAndConnectListener) {
        this.scanAndConnectListener = scanAndConnectListener;
    }

    private ScanAndConnectListener scanAndConnectListener;


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
                    writePacket(dataHeadBytes);
                    break;

                case ActionUtils.ACTION_OTA_DATA_DATA_I: //发送ota数据帧
                    if (currentFrame <= totalFrame) {
                        byte[] currPacket = DataPacketUtils.currentPacket(totalPacketBytes, currentFrame, totalFrame);
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

    class LocalBinder extends Binder {
        public OTAUpgradeService getService() {
            return OTAUpgradeService.this;
        }
    }

    //暂不用,以防需要获取service内部属性和方法时使用
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
            //设备mac地址
            String macAddress = intent.getStringExtra("mac_address");
            //数据字节流
            totalPacketBytes = intent.getByteArrayExtra("dataByte");
            if (totalPacketBytes != null) {
                //总帧数
                totalFrame = (totalPacketBytes.length / 1024) % 4 != 0 ? ((totalPacketBytes.length / 1024 / 4) + 1) : (totalPacketBytes.length / 1024 / 4);
            }
            bluetoothHelper.openVirtualLeash(true, macAddress, "");
            bluetoothHelper.setConnectSuccessListener((bleDevice, gatt) -> {
                if (bleDevice != null) {
                    this.bleDevice = bleDevice;
                    this.bluetoothGatt = gatt;
//                    setNotify(bleDevice);
//                    CURRENT_WHAT = ActionUtils.ACTION_OTA_ORDER_I;
//                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_ORDER_I, 500);
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_SUCCESS_S, bleDevice);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bluetoothHelper.setConnectFailListener((bleDevice, description) -> {
                if (bleDevice != null) {
                    CURRENT_WHAT = ActionUtils.ACTION_CONNECT_FAIL_I;
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_FAIL_S, bleDevice);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bluetoothHelper.setScanFinishListener((bleDevice) -> {
                if (bleDevice != null) {
                    CURRENT_WHAT = ActionUtils.ACTION_SCAN_SUCCESS_I;
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_SCAN_SUCCESS_S, bleDevice);
                    EventBus.getDefault().post(msgBean);
                } else {
                    CURRENT_WHAT = ActionUtils.ACTION_SCAN_FAIL_I;
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_SCAN_FAIL_S, null);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bluetoothHelper.setDisconnectListener((bleDevice, bluetoothGatt) -> {
                //断开连接,记录数据传输情况
                MsgBean msgBean = new MsgBean(ActionUtils.ACTION_DISCONNECT_S, bleDevice);
                EventBus.getDefault().post(msgBean);
            });
            bluetoothHelper.setReconnectSuccessListener((bleDevice -> {
                //若重新连接上,尚未确定是继续传输还是???,暂定为关闭service,若下次启动,将从断位开始
//                stopSelf();
                this.bleDevice = bleDevice;
//                    setNotify(bleDevice);
//                    CURRENT_WHAT = ActionUtils.ACTION_OTA_ORDER_I;
//                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_ORDER_I, 500);
                MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_SUCCESS_S, bleDevice);
                EventBus.getDefault().post(msgBean);
            }));
            //监听characteristic变化
//            bluetoothHelper.setCharacteristicChangeListener(((gatt, characteristic) -> {
//                Log.e("crtChange---",TransformUtils.bytes2String(characteristic.getValue()));
//            }));
        } else if (Objects.equals(action, "OTA_START")) {
            //处理bin文件,转换成byte数组
//            byte[] packetsByte = combinePacket();

        }
    }


    private void setNotify(BleDevice bleDevice) {

        bluetoothHelper1.setNotify(bleDevice, new BluetoothHelper.BleNotifyListener() {
            @Override
            public void onNotifySuccess() {

            }

            @Override
            public void onNotifyFailed(BleException e) {
                Log.e("notifyFail---", e.toString() + "");
            }

            @Override
            public void onCharacteristicChanged(byte[] data) {
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
                            CURRENT_WHAT = ActionUtils.ACTION_OTA_DATA_HEAD_I;
                            handler.obtainMessage(ActionUtils.ACTION_OTA_DATA_HEAD_I).sendToTarget();
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

                Log.e("notifyData----", TransformUtils.bytesToHexString(data));
            }
        });
    }

    private void writePacket(byte[] eachFrameBytes) {

        Log.e("device----", bleDevice + "/" + Arrays.toString(eachFrameBytes));
        bluetoothHelper1.write(bleDevice, eachFrameBytes, new BluetoothHelper.WriteListener() {
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                if (CURRENT_WHAT == ActionUtils.ACTION_OTA_DATA_HEAD_I) { //数据头写入成功
                    CURRENT_WHAT = ActionUtils.ACTION_OTA_DATA_DATA_I;
                    handler.obtainMessage(ActionUtils.ACTION_OTA_DATA_DATA_I).sendToTarget();
                }
                Log.e("writeSuccess---", "current----" + current + "/total---" + total + "/each---" + TransformUtils.bytesToHexString(justWrite));
            }

            @Override
            public void onWriteFailure(BleException exception) {

            }
        });
    }


    private byte[] combinePacket() {
        //合并后的字节数组
        byte[] lastBytes = null;
        try {
            InputStream inputStream = getResources().getAssets().open("ap");
            lastBytes = DataPacketUtils.combinePacket(inputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lastBytes;
    }

    private void toast(String msg) {
        Toast.makeText(this, "show---:" + msg, Toast.LENGTH_LONG).show();
    }

}
