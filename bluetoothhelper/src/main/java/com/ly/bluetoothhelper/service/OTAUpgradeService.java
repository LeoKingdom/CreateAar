package com.ly.bluetoothhelper.service;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetManager;
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
import com.ly.bluetoothhelper.callbacks.DataCallback;
import com.ly.bluetoothhelper.callbacks.NotifyCallback;
import com.ly.bluetoothhelper.callbacks.ProgressCallback;
import com.ly.bluetoothhelper.callbacks.WriteCallback;
import com.ly.bluetoothhelper.helper.BleConnectHelper;
import com.ly.bluetoothhelper.helper.BluetoothHelper;
import com.ly.bluetoothhelper.utils.ActionUtils;
import com.ly.bluetoothhelper.utils.DataPacketUtils;
import com.ly.bluetoothhelper.utils.OrderSetUtils;
import com.ly.bluetoothhelper.utils.SharePreferenceUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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

    private BleConnectHelper bleConnectHelper; //蓝牙操作辅助类
    private BluetoothHelper bluetoothBaseHelper; //蓝牙操作类
    private BleDevice bleDevice;
    private byte[] initialTotalBytes;
    private ProgressCallback progressCallback;
    private NotifyCallback notifyCallback;
    private WriteCallback writeCallback;
    private DataCallback dataCallback;
    private int totalFrame; //总帧数
    int currentFrame = 1; //当前帧
    private int currentBin = 1; //当前bin文件表示
    private int currentPacket = 0;//当前bin当前帧当前包
    private int reCurrentPacket = 0;//重传的当前bin当前帧当前包
    private String filePath;//下载
    private int CURRENT_ACTION;
    private Thread thread;
    private int currentTotalPacket;
    private byte[] loseList;
    private byte[] currentFrameBytes;
    private boolean isBin = false;
    private boolean isReconnect = false;
    private List<String> fileNameList = new ArrayList<>();
    private String macAddress;
    private boolean isReTransTest = true;//重传机制测试版
    private int cp=0;
    private int tp=0;
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void getBinData(TransfirmDataBean dataBean) {
        if (dataBean != null) {
            int what = dataBean.getWhat();
            long delay = dataBean.getDelayTimes();
            handler.sendEmptyMessageDelayed(what, delay);
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
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_NOTIFY;
                    if (!isReconnect) {
                        if (dataCallback != null) {
                            dataCallback.binChecking();
                        }
                    }
                    openNotify();
                    break;

                case ActionUtils.ACTION_OTA_ORDER_I: //发送ota升级命令(含校验bin的合法性)
                    Log.e("currentBin----", currentBin + "");
                    byte[] oadOrder = OrderSetUtils.ORDER_OAD;
                    byte[] binCountBytes = new byte[]{(byte) fileNameList.size(), (byte) currentBin};
                    byte[] headByte = TransformUtils.subBytes(initialTotalBytes, 0, 5);
                    byte[] checkBytes = TransformUtils.combineArrays(oadOrder, binCountBytes, headByte);
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_ORDER_I;
                    writeBytes(checkBytes);
                    break;
                case ActionUtils.ACTION_OTA_DATA_HEAD_I: //发送ota数据帧帧头
                    byte[] curFrameBytes = DataPacketUtils.currentPacket(initialTotalBytes, currentFrame, totalFrame);
                    byte[] dataHeadBytes = DataPacketUtils.eachFrameFirstPacket(curFrameBytes.length, totalFrame, currentFrame);
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_DATA_HEAD_I;
                    writeBytes(dataHeadBytes);
                    break;

                case ActionUtils.ACTION_OTA_NEXT_BIN:
                    currentFrame = 1;
                    totalFrame = 0;
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_NEXT_BIN;
                    isReconnect = false;
//                    openNotify();
                    getDataBytes(currentBin);
                    break;

                case ActionUtils.ACTION_OTA_DATA_DATA_I: //发送ota数据帧
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_DATA_DATA_I;
                    if (currentFrame <= totalFrame) {
                        currentFrameBytes = DataPacketUtils.sortEachFrame(initialTotalBytes, currentFrame, totalFrame);
                        currentTotalPacket = currentFrameBytes.length % 20 == 0 ? currentFrameBytes.length / 20 : currentFrameBytes.length / 20 + 1;
                        if (progressCallback != null) {
                            progressCallback.setMax(currentTotalPacket);
                        }
                        if (isReTransTest && isReconnect) {
                            int length = currentFrameBytes.length - 20 * currentPacket;
//                            Log.e("reData----", currentFrameBytes.length + "/" + length + "/" + currentPacket);
                            byte[] reTransBytes = TransformUtils.subBytes(currentFrameBytes, 20 * (currentPacket - 1), length);
                            writeOnThread(reTransBytes, true);
                        } else {
                            writeOnThread(currentFrameBytes, true);
                        }
                    }
                    break;
                case ActionUtils.ACTION_OTA_DATA_LOSE_I:
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_DATA_LOSE_I;
                    if (loseList == null) { //完整接收一帧数据
                        if (currentFrame == totalFrame && cp == tp) {
                            //传输完一个bin
                            if (currentBin == fileNameList.size()) {//传输完成
                                if (dataCallback != null) {
                                    dataCallback.done();
                                }
                            }
                            if (currentBin < fileNameList.size()) {
                                currentBin++;
                                handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_NEXT_BIN, 2000);
                            }
                            if (isReconnect) {//如果是重传,清除sp缓存信息
                                SharePreferenceUtils.setValue(OTAUpgradeService.this, "data-" + macAddress, "");
                            }
                            return;
                        }
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
                        if (dataCallback != null) {
                            dataCallback.checkOutTime();
                        }
                    }
                    break;
                case ActionUtils.ACTION_OTA_RECONNECT_SEND:
                    //断点(包括断开重传,下一次重传),从当前帧重发(终端还没实现,目前终端实现是在当前传输情况断开进入阻塞,直到接收到下一包数据继续)
                    Object cacheOb = SharePreferenceUtils.getValue(OTAUpgradeService.this, "data-" + macAddress, null);
                    if (cacheOb instanceof String) {
                        String cacheStr = (String) cacheOb;
                        String[] cacheInfos = cacheStr.split(",");
                        int cb = Integer.valueOf(cacheInfos[0]);//当前bin
                        int ctf = Integer.valueOf(cacheInfos[1]);//总帧
                        int cf = Integer.valueOf(cacheInfos[2]);//当前帧
                        int tpa = Integer.valueOf(cacheInfos[3]);//当前帧总包数
                        int cpa = Integer.valueOf(cacheInfos[4]);//当前包
                        Log.e("re_send----", cb + "/" + cf + "/" + cpa);
                        getDataBytes(cb);
                        currentFrame = cf;
                        currentPacket = reCurrentPacket = cpa;
                        if (isReTransTest) {
                            if (tpa == cpa) {
                                currentFrame++;
                            }
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_DATA_I, 1000);
                        } else {
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 1000);
                        }
                    }
                    break;
                case ActionUtils.ACTION_DEVICE_RECONNECT:
                    isReconnect = true;
                    bleConnectHelper.openVirtualLeash(true, macAddress, "");
                    break;
            }
        }
    };

    public void sendMsg(Handler handler, int what, long delayTimes) {
        handler.sendEmptyMessageDelayed(what, delayTimes);
    }


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

        bluetoothBaseHelper.write(bleDevice, datas, new BluetoothHelper.WriteListener() {
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                Log.e("justWrite----", TransformUtils.bytesToHexString(justWrite));
                if (writeCallback != null) {
                    writeCallback.writeSuccess(CURRENT_ACTION, current, total, justWrite);
                }

                if (CURRENT_ACTION == ActionUtils.ACTION_OTA_ORDER_I) {
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_VALIFY_OUTTIME, 60000);
                } else if (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_HEAD_I) {
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_DATA_I, 400);
                } else if (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_DATA_I) {
                    if (isReconnect) {
                        currentPacket = reCurrentPacket + current;
                    } else {
                        currentPacket = current;
                    }
                    current = isReconnect ? reCurrentPacket + current : current;
                    total = isReconnect ? reCurrentPacket + total : total;
                    float percent = (float) current / currentTotalPacket * 100;
                    if (progressCallback != null) {
                        progressCallback.setProgress(percent, current, currentFrame, currentBin);
                    }
                    cp = current;
                    tp = total;
                    Log.e("info---", currentFrame + "/" + totalFrame + "/" + current + "/" + total);

                } else if (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_LOSE_I) {
                    Log.e("loseWrite----", TransformUtils.bytesToHexString(justWrite));
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

        bluetoothBaseHelper.setNotify(bleDevice, new BluetoothHelper.BleNotifyListener() {
            @Override
            public void onNotifySuccess() {
                if (isReconnect) {
                    if (notifyCallback != null) {
                        notifyCallback.deviceReconn();
                    }

                } else {
                    getDataBytes(currentBin);
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_ORDER_I, 3000);
                }
                if (notifyCallback != null) {
                    notifyCallback.success();
                }

            }

            @Override
            public void onNotifyFailed(BleException exception) {
                if (notifyCallback != null) {
                    notifyCallback.fail(exception.getDescription());
                }
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
                            if (dataCallback != null) {
                                dataCallback.binCheckDone(false);
                            }
                        } else {
                            //校验bin包成功,可以开始传输ota包,
                            if (dataCallback != null) {
                                dataCallback.binCheckDone(true);
                            }
                            isBin = true;
                            toast("校验成功");
                            CURRENT_ACTION = ActionUtils.ACTION_OTA_DATA_HEAD_I;
//                            loadingWidget.hide();
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 1000);
                        }
                    } else if (data[6] == (byte) 0x03 && data[5] == (byte) 0x20) { //ota数据包
                        //成功接收完一帧,开始发送下一帧
                        //0xAB 0x00 0x04 0x03  0x01  0x20 0x03  0x03  0x01 0x0A 0x64 收包回复
                        //帧头 数据-长度 总帧 当前帧 MId  EId  丢包数 之后为丢包序号(上限五包,超过填0xFF)
                        //若出现丢包情况,尚未确定是继续下一帧还是先补包
                        byte[] losePacketList = DataPacketUtils.losePackets(currentFrameBytes, data);
                        loseList = losePacketList;
                        handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_LOSE_I, 1000);

                    }
                }

                //待解决: notify接收不完整,现商议方案->app通过发送命令,ble再次发送同样回复,待继续商议....
                //帧回复
                if (CURRENT_ACTION==ActionUtils.ACTION_OTA_DATA_DATA_I){

                }else if (CURRENT_ACTION==ActionUtils.ACTION_OTA_ORDER_I){ //bin回复

                }
            }
        });
    }


    private void initHelper() {
        bleConnectHelper = BleConnectHelper.getInstance().init(getApplication(), 0);
        bluetoothBaseHelper = new BluetoothHelper(getApplication(), 0);
        bluetoothBaseHelper.initUuid(null,
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

    private void initData() {
        try {
            AssetManager assetManager = getAssets();
            String[] assetsList = assetManager.list("");
            for (int i = assetsList.length - 1; i >= 0; i--) {
                if (assetsList[i].startsWith("dict") || assetsList[i].startsWith("clade")) {
                    fileNameList.add(assetsList[i]);
                }
                Log.e("name---", assetsList[i] + "/" + fileNameList.size());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private byte[] getDataBytes(int currentBin) {
        byte[] currentBytes = null;
        if (fileNameList == null || fileNameList.size() == 0) {
            if (dataCallback != null) {
                dataCallback.fileNotFound("no file was found");
            }
        } else {
            try {
                Object cacheSP = SharePreferenceUtils.getValue(this, "data-" + macAddress, "");
                InputStream inputStream = null;
                if (cacheSP instanceof String) {
                    String cacheStr = (String) cacheSP;
                    if (cacheStr.contains(",") && !isReTransTest) {
                        String[] currentInfo = cacheStr.split(",");
                        int cb = Integer.valueOf(currentInfo[0]);
                        inputStream = getResources().getAssets().open(fileNameList.get(cb - 1));
                    } else {
                        inputStream = getResources().getAssets().open(fileNameList.get(currentBin - 1));
                    }

                }
                initialTotalBytes = TransformUtils.streamToByte(inputStream);
                totalFrame = (initialTotalBytes.length / 1024) % 4 != 0 ? ((initialTotalBytes.length / 1024 / 4) + 1) : (initialTotalBytes.length / 1024 / 4);
                if (CURRENT_ACTION == ActionUtils.ACTION_OTA_NEXT_BIN) {
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_ORDER_I, 200);
                }
            } catch (IOException e) {
                Log.e("ble file data----", "file data can not found");
            }

        }
        return initialTotalBytes;
    }

    protected void onHandleIntent(Intent intent) {
        initData();
        String action = intent.getAction();

//        byte[] bytes = intent.getByteArrayExtra("dataByte");
        if (Objects.equals(action, ActionUtils.ACTION_DEVICE_SCAN)) {
            //设备mac地址
            if (bleDevice != null) {
                handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_NOTIFY, 3000);
            } else {
                macAddress = intent.getStringExtra("mac_address");
                bleConnectHelper.openVirtualLeash(true, macAddress, "");
            }

            bleConnectHelper.setConnectSuccessListener((bleDevice, gatt) -> {
                if (bleDevice != null) {
                    this.bleDevice = bleDevice;
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_NOTIFY, 3000);
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_SUCCESS_S, gatt, bleDevice);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bleConnectHelper.setConnectFailListener((bleDevice, description) -> {
                if (bleDevice != null) {
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_FAIL_S, bleDevice);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bleConnectHelper.setScanFinishListener((bleDevice) -> {
                if (bleDevice != null) {
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_SCAN_SUCCESS_S, bleDevice);
                    EventBus.getDefault().post(msgBean);
                } else {
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_SCAN_FAIL_S, null);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bleConnectHelper.setDisconnectListener((isActiveDisConnect, bleDevice1, gatt) -> {
                if (!isActiveDisConnect) {
                    //断开连接,记录数据传输情况
                    isReconnect = true;
                    if (currentFrame < totalFrame) {
                        Log.e("cache---", currentBin + "/" + totalFrame + "/" + currentFrame + "/" + currentPacket);
                        String spCache = currentBin + "," + totalFrame + "," + currentFrame + "," + tp + "," + currentPacket;
                        SharePreferenceUtils.setValue(this, "data-" + macAddress, spCache); //记录当前设备传输情况
                    }
                    MsgBean msgBean = new MsgBean(ActionUtils.ACTION_DISCONNECT_S, bleDevice);
                    EventBus.getDefault().post(msgBean);
                }
            });
            bleConnectHelper.setReconnectSuccessListener((bleDevice -> {
                //若重新连接上,尚未确定是继续传输还是???,暂定为关闭service,若下次启动,将从断位开始
//                stopSelf();
                MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_SUCCESS_S, bleDevice);
                EventBus.getDefault().post(msgBean);
            }));
            //监听characteristic变化
            bleConnectHelper.setCharacteristicChangeListener(((gatt, characteristic) -> {
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
