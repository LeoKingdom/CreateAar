package com.ly.bluetoothhelper.service;

import android.annotation.SuppressLint;
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
import com.ly.bluetoothhelper.beans.MsgBean;
import com.ly.bluetoothhelper.beans.TransfirmDataBean;
import com.ly.bluetoothhelper.callbacks.BaseCallback;
import com.ly.bluetoothhelper.callbacks.DataCallback;
import com.ly.bluetoothhelper.callbacks.NotifyCallback;
import com.ly.bluetoothhelper.callbacks.ProgressCallback;
import com.ly.bluetoothhelper.callbacks.WriteCallback;
import com.ly.bluetoothhelper.helper.BLEHelper;
import com.ly.bluetoothhelper.helper.BluetoothHelper;
import com.ly.bluetoothhelper.utils.ActionUtils;
import com.ly.bluetoothhelper.utils.DataPacketUtils;
import com.ly.bluetoothhelper.utils.OrderSetUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

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

    private BLEHelper bluetoothHelper; //蓝牙操作辅助类
    private BluetoothHelper bluetoothHelper1; //蓝牙操作类
    private BleDevice bleDevice;
    private byte[] initialTotalBytes;
    private ProgressCallback progressCallback;
    private NotifyCallback notifyCallback;
    private WriteCallback writeCallback;
    private DataCallback dataCallback;
    private List<BaseCallback> baseCallbackList;
    private boolean openNotifySuccess = false;
    private int totalFrame; //总帧数
    int currentFrame = 1; //当前帧
    private String filePath;//下载
    private int CURRENT_ACTION;
    private Thread thread;
    private int currentPacket;
    private byte[] loseList;
    private byte[] currentFrameBytes;
    private boolean isBin = false;

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void getBinData(TransfirmDataBean dataBean) {
        if (dataBean != null) {
            initialTotalBytes = dataBean.getDatas();
        }
    }

    public Handler getHandler() {
        return handler;
    }

    @SuppressLint("HandlerLeak")
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case ActionUtils.ACTION_OTA_NOTIFY:
                    if (dataCallback!=null){
                        dataCallback.binChecking();
                    }
                    openNotify();
                    break;

                case ActionUtils.ACTION_OTA_ORDER_I: //发送ota升级命令(含校验bin的合法性)
                    byte[] oadOrder = OrderSetUtils.ORDER_OAD;
                    byte[] headByte = TransformUtils.subBytes(initialTotalBytes, 0, 5);
                    byte[] checkBytes = TransformUtils.combineArrays(oadOrder, headByte);
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_ORDER_I;
                    writeBytes(checkBytes);
                    break;
                case ActionUtils.ACTION_OTA_DATA_HEAD_I: //发送ota数据帧帧头
                    byte[] curFrameBytes = DataPacketUtils.currentPacket(initialTotalBytes, currentFrame, totalFrame);
                    byte[] dataHeadBytes = DataPacketUtils.eachFrameFirstPacket(curFrameBytes.length, totalFrame, currentFrame);
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_DATA_HEAD_I;
                    writeBytes(dataHeadBytes);
                    break;

                case ActionUtils.ACTION_OTA_BIN_DATA:
                    if (msg != null) {
                        initialTotalBytes = (byte[]) msg.obj;

                    }
                    break;

                case ActionUtils.ACTION_OTA_DATA_DATA_I: //发送ota数据帧
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_DATA_DATA_I;
                    if (currentFrame <= totalFrame) {
                        currentFrameBytes = DataPacketUtils.sortEachFrame(initialTotalBytes, currentFrame, totalFrame);
                        currentPacket = currentFrameBytes.length % 20 == 0 ? currentFrameBytes.length / 20 : currentFrameBytes.length / 20 + 1;
                        if (progressCallback != null) {
                            progressCallback.setMax(currentPacket);
                        }
                        writeOnThread(currentFrameBytes, true);

                    }
                    break;
                case ActionUtils.ACTION_OTA_DATA_LOSE_I:
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_DATA_LOSE_I;
                    if (loseList == null) { //完整接收一帧数据
                        currentFrame++;
                        if (currentFrame <= totalFrame) {
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 400);
                            if (dataCallback != null) {
                                dataCallback.nextFrame(currentFrame, totalFrame);
                            }
                        }
                    } else {
                        if (loseList.length == 1) { //丢包过多,需重发此帧
                            toast("丢包过多,重新传输");
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 400);
                        } else {
                            //发送丢失的包,不需要重发帧头
                            writeBytes(loseList);
                        }
                    }
                    break;
//
                case ActionUtils.ACTION_OTA_VALIFY_OUTTIME:
                    if (!isBin) {
                        toast("校验超时");
                        if (dataCallback!=null){
                            dataCallback.checkOutTime();
                        }
                    }
                    break;

            }
        }
    };

    private class MyThread extends Thread {
        private byte[] datas;

        public MyThread(byte[] data) {
            this.datas = data;
        }

        @Override
        public void run() {
            super.run();
            writeBytes(datas);
        }
    }

    private void writeOnThread(byte[] data, boolean withThread) {
        if (withThread) {
            thread = new MyThread(data);
            if (thread.isAlive()) {
                thread.destroy();
            }
            thread.start();
        } else {
            writeBytes(data);
        }
    }

    private void writeBytes(byte[] datas) {

        bluetoothHelper1.write(bleDevice, datas, new BluetoothHelper.WriteListener() {
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                if (writeCallback != null) {
                    writeCallback.writeSuccess(CURRENT_ACTION, current, total, justWrite);
                }

                if (CURRENT_ACTION == ActionUtils.ACTION_OTA_ORDER_I) {
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_VALIFY_OUTTIME, 30000);
                } else if (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_HEAD_I) {
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_DATA_I, 400);
                } else if (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_DATA_I) {
                    float percent = (float) current / currentPacket * 100;
                    if (progressCallback != null) {
                        progressCallback.setProgress(percent,current,currentFrame);
                    }
                    if (dataCallback != null) {
                        if (currentFrame == totalFrame && current == total) {
                            dataCallback.done();
                        }
                    }
                }
            }

            @Override
            public void onWriteFailure(BleException exception) {
                if (writeCallback != null) {
                    writeCallback.fail(exception.getDescription());
                }
            }
        });
    }

    public void openNotify() {
        if (bleDevice == null) {
            toast("设备未连接");
            if (notifyCallback != null) {
                notifyCallback.noDevice();
            }
            return;
        }

        bluetoothHelper1.setNotify(bleDevice, new BluetoothHelper.BleNotifyListener() {
            @Override
            public void onNotifySuccess() {
                openNotifySuccess = true;
                handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_ORDER_I, 3000);
                if (notifyCallback != null) {
                    notifyCallback.success();
                }

//                toast("通知开启成功");
//                Log.e("notify---", "success");
            }

            @Override
            public void onNotifyFailed(BleException exception) {
                openNotifySuccess = false;
                if (notifyCallback != null) {
                    notifyCallback.fail(exception.getDescription());
                }
//                toast("通知开启失败");
//                Log.e("notify---", "fail---" + exception.getDescription());
            }

            @Override
            public void onCharacteristicChanged(byte[] data) {
                Log.e("notifyData----", TransformUtils.bytesToHexString(data));
                if (notifyCallback != null) {
                    notifyCallback.charactoristicChange(CURRENT_ACTION, data);
                }
                if (data.length > 3) {
                    byte responeByte = data[data.length - 1];
                    byte moduId = data[data.length - 2];
                    byte eventId = data[data.length - 3];
                    //module Id                                     //event Id
                    if (moduId == (byte) 0x02 && eventId == (byte) 0x20) { //ota校验命令
                        if (responeByte == (byte) 0xFF) {
                            CURRENT_ACTION = ActionUtils.ACTION_OTA_ORDER_I;
                            toast("校验失败");
                            if (dataCallback!=null){
                                dataCallback.binCheckDone(false);
                            }
                        } else {
                            //校验bin包成功,可以开始传输ota包,
                            if (dataCallback!=null){
                                dataCallback.binCheckDone(true);
                            }
                            isBin = true;
                            toast("校验成功");
                            CURRENT_ACTION = ActionUtils.ACTION_OTA_DATA_HEAD_I;
//                            loadingWidget.hide();
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 2000);
                        }
                    } else if (data[data.length - 3] == (byte) 0x03 && data[data.length - 4] == (byte) 0x20) { //ota数据包
                        //成功接收完一帧,开始发送下一帧
                        //0xAB 0x00 0x04 0x03  0x01  0x20 0x03  0x03  0x01 0x0A 0x64 收包回复
                        //帧头 数据-长度 总帧 当前帧 MId  EId  丢包数 之后为丢包序号(上限五包,超过填0xFF)
                        //若出现丢包情况,尚未确定是继续下一帧还是先补包
                        byte[] losePacketList = DataPacketUtils.losePackets(currentFrameBytes, data);
                        loseList = losePacketList;
                        handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_LOSE_I, 400);

                    }
                }
            }
        });

    }


    private void initHelper() {
        bluetoothHelper = BLEHelper.getInstance().init(getApplication());
        bluetoothHelper1 = new BluetoothHelper(getApplication());
        bluetoothHelper1.initUuid(null,
                "00005500-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5");
    }

    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    public void setNotifyCallback(NotifyCallback notifyCallback) {
        this.notifyCallback = notifyCallback;
    }

    public void setWriteCallback(WriteCallback writeCallback) {
        this.writeCallback = writeCallback;
    }

    public void setDataCallback(DataCallback dataCallback) {
        this.dataCallback = dataCallback;
    }


   public class LocalBinder extends Binder {
        public OTAUpgradeService getService() {
            return OTAUpgradeService.this;
        }
    }

    //暂不用,以防需要获取service内部属性和方法时使用
    private IBinder mBinder = new LocalBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        initHelper();
        onHandleIntent(intent);
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
        byte[] bytes = intent.getByteArrayExtra("dataByte");
        if (bytes != null) {
            initialTotalBytes = bytes;
            totalFrame = (initialTotalBytes.length / 1024) % 4 != 0 ? ((initialTotalBytes.length / 1024 / 4) + 1) : (initialTotalBytes.length / 1024 / 4);
        }
        if (Objects.equals(action, ActionUtils.ACTION_DEVICE_SCAN)) {
            //设备mac地址
            String macAddress = intent.getStringExtra("mac_address");
//            bluetoothHelper.openVirtualLeash(true, macAddress, "");
            bluetoothHelper.openVirtualLeash(true, macAddress, "");
            bluetoothHelper.setConnectSuccessListener((bleDevice, gatt) -> {
                if (bleDevice != null) {
                    this.bleDevice = bleDevice;
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_NOTIFY, 3000);
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_SUCCESS_S, gatt, bleDevice);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bluetoothHelper.setConnectFailListener((bleDevice, description) -> {
                if (bleDevice != null) {
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_FAIL_S, bleDevice);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bluetoothHelper.setScanFinishListener((bleDevice) -> {
                if (bleDevice != null) {
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_SCAN_SUCCESS_S, bleDevice);
                    EventBus.getDefault().post(msgBean);
                } else {
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
                MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_SUCCESS_S, bleDevice);
                EventBus.getDefault().post(msgBean);
            }));
            //监听characteristic变化
            bluetoothHelper.setCharacteristicChangeListener(((gatt, characteristic) -> {
                Log.e("crtChange---", TransformUtils.bytes2String(characteristic.getValue()));
            }));
        } else if (Objects.equals(action, "OTA_START")) {
            //处理bin文件,转换成byte数组
//            byte[] packetsByte = combinePacket();

        }
    }


    private void toast(String msg) {
        Toast.makeText(this, "show---:" + msg, Toast.LENGTH_LONG).show();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (thread != null && thread.isAlive()) {
            thread.destroy();
            thread = null;
        }
        return super.onUnbind(intent);
    }
}
