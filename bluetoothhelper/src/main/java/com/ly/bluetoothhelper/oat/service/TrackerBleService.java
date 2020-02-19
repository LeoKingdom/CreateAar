/**************************************************************************************************
 * Copyright 2017 Qualcomm Technologies International, Ltd.                                       *
 **************************************************************************************************/

package com.ly.bluetoothhelper.oat.service;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.ly.bluetoothhelper.callbacks.base_callback.NotifyOpenCallback;
import com.ly.bluetoothhelper.callbacks.base_callback.WriteCallback;
import com.ly.bluetoothhelper.callbacks.upgrade_callback.DataCallback;
import com.ly.bluetoothhelper.callbacks.upgrade_callback.NotifyCallback;
import com.ly.bluetoothhelper.callbacks.upgrade_callback.ProgressCallback;
import com.ly.bluetoothhelper.callbacks.upgrade_callback.UpgradeStatusCallback;
import com.ly.bluetoothhelper.helper.PetTrackerHelper;
import com.ly.bluetoothhelper.oat.annotation.ConfirmationType;
import com.ly.bluetoothhelper.oat.annotation.Enums;
import com.ly.bluetoothhelper.oat.annotation.MessageType;
import com.ly.bluetoothhelper.oat.annotation.State;
import com.ly.bluetoothhelper.oat.annotation.Support;
import com.ly.bluetoothhelper.oat.ble.BLEUtils;
import com.ly.bluetoothhelper.oat.ble.Characteristics;
import com.ly.bluetoothhelper.oat.ble.Services;
import com.ly.bluetoothhelper.oat.gaia.GaiaUpgradeManager;
import com.ly.bluetoothhelper.oat.receiver.BondStateReceiver;
import com.ly.bluetoothhelper.oat.rwcp.RWCPManager;
import com.ly.bluetoothhelper.oat.upgrade.UpgradeError;
import com.ly.bluetoothhelper.oat.upgrade.UpgradeManager;
import com.ly.bluetoothhelper.oat.upgrade.UploadProgress;
import com.ly.bluetoothhelper.oat.upgrade.codes.ResumePoints;
import com.ly.bluetoothhelper.utils.ActionUtils;
import com.ly.bluetoothhelper.utils.Consts;
import com.ly.bluetoothhelper.utils.DataPacketUtils;
import com.ly.bluetoothhelper.utils.OrderSetUtils;
import com.ly.bluetoothhelper.utils.SharePreferenceUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;
import com.ly.bluetoothhelper.utils.Utils;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

import fastble.data.BleDevice;
import fastble.exception.BleException;
import fastble.utils.BleLog;

/**
 * <p>This {@link android.app.Service Service} allows to manage the Bluetooth communication with a device and can
 * work as a Start/Stop service.</p>
 * <p>This Service also manages the Upgrade process of a Device over the {@link com.ly.bluetoothhelper.oat.gaia.GAIA GAIA}
 * protocol. It uses the {@link GaiaUpgradeManager} and the {@link UpgradeManager} to manage the Upgrade process and
 * messages received and sent by a GAIA Bluetooth Device.</p>
 */
public class TrackerBleService extends BLEService implements GaiaUpgradeManager.GaiaManagerListener,
        BondStateReceiver.BondStateListener, RWCPManager.RWCPListener {

    /**
     * The UUID of the GAIA service.
     */
    private static final UUID GAIA_SERVICE_UUID = Services.getStringServiceUUID(Services.SERVICE_CSR_GAIA);
    /**
     * The UUID of the GAIA characteristic response endpoint.
     */
    private static final UUID GAIA_CHARACTERISTIC_RESPONSE_UUID =
            Characteristics.getCharacteristicUUID(Characteristics.CHARACTERISTIC_CSR_GAIA_RESPONSE_ENDPOINT);
    /**
     * The UUID of the GAIA characteristic command endpoint.
     */
    private static final UUID GAIA_CHARACTERISTIC_COMMAND_UUID =
            Characteristics.getCharacteristicUUID(Characteristics.CHARACTERISTIC_CSR_GAIA_COMMAND_ENDPOINT);
    /**
     * The UUID of the GAIA characteristic data endpoint.
     */
    private static final UUID GAIA_CHARACTERISTIC_DATA_ENDPOINT_UUID =
            Characteristics.getCharacteristicUUID(Characteristics.CHARACTERISTIC_CSR_GAIA_DATA_ENDPOINT);
    /**
     * To know if we are using the application in the debug mode.
     */
    private static final boolean DEBUG = Consts.DEBUG;
    /**
     * <p>The tag to display for logs.</p>
     */
    private final String TAG = "OtauBleService";
    /**
     * <p>The handler to communicate with the app.</p>
     */
    private final List<Handler> mAppListeners = new ArrayList<>();
    /**
     * <p>The binder to return to the instance which will bind this service.</p>
     */
    private final IBinder mBinder = new LocalBinder();
    /**
     * To know the characteristics which have been registered for notifications.
     */
    private final ArrayList<UUID> mNotifiedCharacteristics = new ArrayList<>();
    /**
     * To manage the GAIA packets which has been received from the device and which will be send to the device.
     * 升级管理类,传入GaiaManagerListener接口,实现的方法为
     * onVMUpgradeDisconnected()  断开升级的方法监听
     * onResumePointChanged(@ResumePoints.Enums int point);  当升级过程中的步骤有进展时，将调用此方法。
     * onUpgradeError(UpgradeError error); 升级过程中出现异常
     * onUploadProgress(UploadProgress progress); 升级过程
     * sendGAIAPacket(byte[] packet, boolean isTransferringData); 将GAIA数据包的字节传输到连接的设备方法。
     * onUpgradeFinish(); 升级完成
     * askConfirmationFor(@UpgradeManager.ConfirmationType int type);
     * onUpgradeSupported(boolean supported);
     * onRWCPEnabled(boolean enabled);
     * onRWCPNotSupported();
     */
    private final GaiaUpgradeManager mGaiaManager = new GaiaUpgradeManager(this);
    /**
     * To keep the instance of the bond state receiver to be able to unregister it.
     */
    private final BondStateReceiver mBondStateReceiver = new BondStateReceiver(this);
    /**
     * <p>To manage the Reliable Write Command Protocol - RWCP.</p>
     * <p>This protocol uses WRITE COMMANDS - "write with no response" with Android - and NOTIFICATIONS through the
     * GAIA DATA ENDPOINT GATT characteristic.</p>
     */
    private final RWCPManager mRWCPManager = new RWCPManager(this);
    /**
     * <p>To queue up the progress during the file transfer when RWCP is supported.</p>
     * <p>When RWCP is supported this service waits for the confirmation of the RWCPManager to send the progress to
     * listeners.</p>
     */
    private final Queue<UploadProgress> mProgressQueue = new LinkedList<>();
    int currentFrame = 1; //当前帧
    long s = 0;
    // --------------------------add by ly--------------------------
    private PetTrackerHelper petTrackerHelper; //蓝牙操作辅助类
    private BleDevice bleDevice;
    private byte[] initialTotalBytes;
    private ProgressCallback progressCallback;
    private NotifyCallback notifyCallback;
    private WriteCallback writeCallback;
    private DataCallback dataCallback;
    private int totalFrame; //总帧数
    private int currentBin = 1; //当前bin文件表示
    private int currentPacket = 0;//当前bin当前帧当前包
    private int reCurrentPacket = 0;//重传的当前bin当前帧当前包
    private UpgradeStatusCallback statusCallback;
    //------------------------end---------------------------

    // ====== CONSTS FIELDS ========================================================================

    //-------------------------------------------GAIA各种UUID START----------------------------------------------------------
    private int CURRENT_ACTION;
    private Thread thread;
    private int currentTotalPacket;

    //-----------------------------------------------------END-----------------------------------------------------------
    private byte[] loseList; //丢包列表,用来处理丢包逻辑(不一定会丢包)


    // ====== PRIVATE FIELDS =======================================================================
    private byte[] currentFrameBytes;
    private boolean isBin = false;
    private boolean isReconnect = false;
    private List<String> fileNameList = new ArrayList<>(); //文件路径列表(只需要文件名的话注意截取)
    private String macAddress;//蓝牙地址
    private boolean isReTransTest = true;//重传机制测试版
    private int cp = 0;//当前帧当前包(成功写入的时候赋值)
    private int tp = 0;//当前帧总包(成功写入的时候赋值)
    /**
     * To keep the information that the gaia protocol is supported by the connected device.
     */
    private @Support
    int mIsGaiaSupported = Support.DO_NOT_KNOW;
    /**
     * To keep the information that the upgrade protocol is supported by the connected device.
     */
    private @Support
    int mIsUpgradeSupported = Support.DO_NOT_KNOW;
    /**
     * To know if RWCP mode is supported by the device.
     * RWCP mode is known as supported if the DATA_ENDPOINT characteristic has the following properties: READ,
     * WRITE_NO_RESPONSE, NOTIFY.
     */
    private boolean mIsRWCPSupported = true;
    /**
     * <p>The GAIA DATA ENDPOINT characteristic known as
     * {@link #GAIA_CHARACTERISTIC_DATA_ENDPOINT_UUID GAIA_CHARACTERISTIC_DATA_ENDPOINT_UUID}.</p>
     */
    private BluetoothGattCharacteristic mCharacteristicGaiaData;
    /**
     * <p>The GAIA COMMAND characteristic known as
     * {@link #GAIA_CHARACTERISTIC_COMMAND_UUID GAIA_CHARACTERISTIC_COMMAND_UUID}.</p>
     */
    private BluetoothGattCharacteristic mCharacteristicGaiaCommand;


    // ====== PUBLIC METHODS =======================================================================
    /**
     * <p>To get the time when the file transfer starts.</p>
     */
    private long mTransferStartTime = 0;
    private int totalPackets;//总包数(所有文件)
    private boolean isTest = false;
    private int transforPacket;//当前传输的总包数,用来计算进度
    private int currTotalPackets = 0; //当前帧的总包数,用来记录断点
    private int allTotleFrame = 0;
    private int allCurrentFrame = 0;
    private byte[] frameHeadBytes;
    private String frameHeadBytesStr;
    //fota升级handler,各种业务转换
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
                    getDataBytes(currentBin);

                    break;

                case ActionUtils.ACTION_OTA_ORDER_I: //发送ota升级命令(含校验bin的合法性)
                    byte[] oadOrder = OrderSetUtils.ORDER_OAD;
                    byte[] binCountBytes = new byte[]{(byte) fileNameList.size(), (byte) currentBin};
                    String filePath = fileNameList.get(currentBin - 1);
                    String fileName = filePath.substring(filePath.lastIndexOf("/") + 1);
                    byte[] headByte = fileName.getBytes();
                    oadOrder[2] = (byte) (2 + headByte.length);//文件头dataLength:2表示总文件数和当前第几个文件
                    byte[] checkBytes = TransformUtils.combineArrays(oadOrder, binCountBytes, headByte);
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_ORDER_I;
                    writeBytes(checkBytes);
                    break;
                case ActionUtils.ACTION_OTA_DATA_HEAD_I: //发送ota数据帧帧头
                    if (currentFrame > totalFrame) {
                        //极端情况:断开连接正在发送当前帧最后一包,先忽略此种情况
                        return;
                    }
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_DATA_HEAD_I;
                    if (initialTotalBytes.length > 13) {
                        byte[] curFrameBytes = DataPacketUtils.currentPacket(initialTotalBytes, currentFrame, totalFrame);
                        byte[] dataHeadBytes = DataPacketUtils.eachFrameFirstPacket(curFrameBytes.length, totalFrame, currentFrame);
                        frameHeadBytesStr = Arrays.toString(dataHeadBytes);
                        writeBytes(dataHeadBytes);
                    } else {
                        byte[] fBytes = DataPacketUtils.eachFrameBytes(initialTotalBytes);
                        frameHeadBytesStr = Arrays.toString(fBytes);
                        writeBytes(fBytes);
                    }
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
                    Log.e("frame----", currentFrame + "/" + totalFrame+"/"+currentBin);
                    if (progressCallback != null && currentBin == 1 && currentFrame == 1) {
                        progressCallback.setMax(totalPackets); //设置进度最大值,回调
                        progressCallback.setMaxFrame(allTotleFrame);
                    }
                    if (currentFrame <= totalFrame) {
                        allCurrentFrame++;
                        float framePercent = (float) allCurrentFrame / allCurrentFrame * 100;
                        progressCallback.setFrameProgress(framePercent, allTotleFrame, allCurrentFrame, currentBin, fileNameList.size());
                        currentFrameBytes = DataPacketUtils.sortEachFrame(initialTotalBytes, currentFrame, totalFrame);
                        int currentTotalPackets = currentFrameBytes.length % 20 == 0 ? currentFrameBytes.length / 20 : currentFrameBytes.length / 20 + 1;
                        Log.e("curr---", currentFrameBytes.length + "/" + currentTotalPackets);
                        currentTotalPacket = currentTotalPackets;
                        writeBytes(currentFrameBytes);
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
                                handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_NEXT_BIN, 50);
                            }
                            if (isReconnect) {//如果是重传,清除sp缓存信息
                                SharePreferenceUtils.setValue(TrackerBleService.this, "data-" + macAddress, "");
                            }
                            return;
                        }
                        currentFrame++;
                        if (currentFrame <= totalFrame) {
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 10);
                            if (dataCallback != null) {
                                dataCallback.nextFrame(currentFrame, totalFrame);
                            }

                        }
                    } else {
                        if (loseList.length == 1) { //丢包过多,需重发此帧
//                            toast("丢包过多,重新传输");
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 10);
                        } else {
                            //发送丢失的包,不需要重发帧头
                            writeBytes(loseList);
                        }
                    }
                    break;
//
                case ActionUtils.ACTION_OTA_VALIFY_OUTTIME:
                    if (!isBin) {
//                        toast("校验超时");
                        if (dataCallback != null) {
                            dataCallback.checkOutTime();
                        }
                    }
                    break;
                case ActionUtils.ACTION_OTA_RECONNECT_SEND:
                    SharePreferenceUtils.setValue(TrackerBleService.this, macAddress, "");
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_RECONNECT_SEND;
                    byte[] curFrameBytes = DataPacketUtils.currentPacket(initialTotalBytes, currentFrame, totalFrame);
                    byte[] dataHeadBytes = DataPacketUtils.eachFrameFirstPacket(curFrameBytes.length, totalFrame, currentFrame);
                    dataHeadBytes[6] = (byte) 0x04;
                    writeBytes(dataHeadBytes);
                    break;
                case ActionUtils.ACTION_OPEN_NOTIFY:
                    openNotify();
                    break;
            }
        }
    };

    /**
     * <p>To start the Upgrade process with the given file.</p>
     * 升级的入口,即调用此方法开始升级
     *
     * @param file The file to use to upgrade the Device. file为null表示升级fota,否则升级蓝牙芯片
     */
    @SuppressLint("NewApi")
    public void startUpgrade(File file) {
        //设置数据包直接的时间间隔,降低数据包丢失的可能性;CONNECTION_PRIORITY_HIGH:  30-40ms
        if (file != null) {
            super.getBluetoothGatt().requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
            mGaiaManager.startUpgrade(file);
            mProgressQueue.clear();
            mTransferStartTime = 0;
        } else {
            BleDevice device = petTrackerHelper.getConnectDevice(macAddress);
            if (device != null) {
                handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_NOTIFY, 20);
            } else {
                statusCallback.deviceNotConnect();
            }
        }
    }

    /***************************************fota升级 start***************************************************/

    /**
     * 初始化,升级需要用到的各种uuid
     */
    private void initHelper() {
//        EventBus.getDefault().register(this);
        petTrackerHelper = PetTrackerHelper.getInstance(getApplication());
        petTrackerHelper.init(1);
    }

    public Handler getHandler() {
        return handler;
    }

    public void sendMsg(int what, long delayTimes) {
        if (handler == null) return;
        handler.sendEmptyMessageDelayed(what, delayTimes);
    }

    public void setCurrentAction(int action) {
        this.CURRENT_ACTION = action;
    }

    public void setResendData() {
        CURRENT_ACTION = ActionUtils.ACTION_OTA_RECONNECT_SEND;
        //断点(包括断开重传,下一次重传),从当前帧重发(终端还没实现,目前终端实现是在当前传输情况断开进入阻塞,直到接收到下一包数据继续)
        String cache = (String) SharePreferenceUtils.getValue(TrackerBleService.this, macAddress, "");
//                    Object cacheOb = SharePreferenceUtils.getValue(OtauBleService.this, cacheKey, null);
        if (cache.contains(",")) {
            String[] cacheInfos = cache.split(",");
            int att = Integer.valueOf(cacheInfos[0]);//文件所有包数
            int ctt = Integer.valueOf(cacheInfos[1]);//当前传送的包数(对于所有文件而言)
            int cfile = Integer.valueOf(cacheInfos[2]);//当前文件
            int cf = Integer.valueOf(cacheInfos[3]);//当前帧
            int cfp = Integer.valueOf(cacheInfos[4]);//当前帧总包数
            Log.e("re_send----", cfp+"/"+att + "/" + cf + "/" + ctt + "/" + cfile);
            currentBin = cfile;
            totalPackets=att;
            transforPacket=ctt;
            if (progressCallback!=null){
                progressCallback.setMax(totalPackets);
            }
            if (transforPacket > cfp) transforPacket -= cfp;
            getDataBytes(cfile);
            currentFrame = cf+1;
            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_RECONNECT_SEND, 30);
        }
    }

    /**
     * 在工作线程中执行写入操作
     *
     * @param data
     * @param withThread
     */
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

    /**
     * 向蓝牙设备写入数据
     *
     * @param datas
     */
    private void writeBytes(byte[] datas) {
//        Log.e("action----", CURRENT_ACTION + "/" + datas.length);
        petTrackerHelper.writeCharacteristic(bleDevice, Consts.betweenTimes, datas, new WriteCallback() {
            @Override
            public void writeSuccess(int actionType, int current, int total, byte[] justWrite) {
                Log.e("write----",Arrays.toString(justWrite));
                if (writeCallback != null) {
                    writeCallback.writeSuccess(CURRENT_ACTION, current, total, justWrite);
                }
                if (CURRENT_ACTION == ActionUtils.ACTION_OTA_ORDER_I) {
                    BleLog.e("binHead----", TransformUtils.bytesToHexString(justWrite));
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_VALIFY_OUTTIME, 60000);
                } else if (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_HEAD_I) {
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_DATA_I, 10);
                } else if (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_DATA_I) {
                    if (isReconnect) {
                        currentPacket = reCurrentPacket + current;
                    } else {
                        currentPacket = current;
                    }
                    transforPacket++;
                    current = isReconnect ? reCurrentPacket + current : current;
                    total = isReconnect ? reCurrentPacket + total : total;
                    float percent = (float) transforPacket / totalPackets * 100;
                    if (progressCallback != null) {
                        if (percent > 100) percent = 100;
                        progressCallback.setProgress(percent, current, totalFrame, currentFrame, currentBin, fileNameList.size());
                    }
                    cp = current;
                    tp = total;
                    Log.e("info---", currentFrame + "/" + totalFrame + "/" + current + "/" + total);

                } else if (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_LOSE_I) {
                    Log.e("loseWrite----", TransformUtils.bytesToHexString(justWrite));
                }
            }

            @Override
            public void error(String err) {
                super.error(err);
                if (writeCallback != null) {
                    writeCallback.error(err);
                }
            }
        });
    }

    /**
     * 打开通知,及根据各种回复数据进行处理,主要的业务转换逻辑
     */
    public void openNotify() {
        if (bleDevice == null) {
//            toast("设备未连接");
            if (notifyCallback != null) {
                notifyCallback.deviceNotConnect();
            }
            return;
        }
        petTrackerHelper.setNotify(bleDevice, new NotifyOpenCallback() {
            @Override
            public void onNotifySuccess(BleDevice device) {
                if (notifyCallback != null) {
                    notifyCallback.onNext();
                }
            }

            @Override
            public void onNotifyFailed(BleException e) {
                if (notifyCallback != null) {
                    notifyCallback.error(e.getDescription());
                }
            }

            @Override
            public void onCharacteristicChanged(String mac, byte[] data) {
                Log.e("notifyData----", TransformUtils.bytesToHexString(data));
                if (notifyCallback != null) {
                    notifyCallback.characteristicChange(CURRENT_ACTION, data);
                }
                if (data.length >= 8) {
                    byte responeByte = data[data.length - 1];
                    byte moduId = data[5];
                    byte eventId = data[6];
                    //module Id                                     //event Id
                    if (eventId == (byte) 0x02 && moduId == (byte) 0x20) { //ota校验命令
                        if (responeByte == (byte) 0x00) {
                            //校验bin包成功,可以开始传输ota包,
                            if (dataCallback != null) {
                                dataCallback.binCheckDone(true);
                            }
                            isBin = true;
//                            toast("校验成功");
//                            loadingWidget.hide();
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 20);
                        } else if (responeByte == (byte) 0xFF) {
//                            toast("校验失败");
                            if (dataCallback != null) {
                                dataCallback.binCheckDone(false);
                            }
                        }
                    } else if (eventId == (byte) 0x03 && moduId == (byte) 0x20) { //ota数据包
                        //成功接收完一帧,开始发送下一帧
                        //0xAB 0x00 0x04 0x03  0x01  0x20 0x03  0x03  0x01 0x0A 0x64 收包回复
                        //帧头 数据-长度 总帧 当前帧 MId  EId  丢包数 之后为丢包序号(上限五包,超过填0xFF)
                        //若出现丢包情况,尚未确定是继续下一帧还是先补包
                        //帧回复data异常,回复命令以触发再次回复
                        if (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_DATA_I || (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_HEAD_I && initialTotalBytes.length <= 13)) {

                            byte[] losePacketList = DataPacketUtils.losePackets(currentFrameBytes, data);
                            loseList = losePacketList;
                            if (isTest) {
                                handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_LOSE_I, 10);
                            } else {
                                handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_LOSE_I, 10);
                            }
                        }

                    } else if (eventId == (byte) 0x04 && moduId == (byte) 0x20) {//重传命令
                        if (data.length > 8) {
                            currentBin = data[7];
                            currentFrame = data[8]+1;
                            getDataBytes(currentBin);
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 30);
                        }
                    }

                }
            }
        });
    }

    /**
     * 初始化fota升级所需要的文件
     *
     * @param unZipPath fota升级包解压文件夹路径
     */
    public void initData(String unZipPath) {
        int totalBytesLength = 0;//计算总进度使用
        int totalFrame1 = 0;
        try {
//            String sdDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test-data";
//            String sdDirSob = Environment.getExternalStorageDirectory().getAbsolutePath() + "/test-data/unzip";
//            File file = new File(sdDir);
            File zipFile = new File(unZipPath);
            if (!zipFile.exists()) {
                statusCallback.fileIsEmpty();
                return;
            }

            String[] zipFiles = zipFile.list();
            for (String fileName : zipFiles) {
//                if (fileName.startsWith("hi")||fileName.startsWith("ap")||fileName.startsWith("app")) continue;
//                if (fileName.startsWith("hd")) {
                fileNameList.add(unZipPath + "/" + fileName);
                String filePath = unZipPath + "/" + fileName;
                FileInputStream file1 = new FileInputStream(new File(filePath));
                byte[] bytes = TransformUtils.streamToByte(file1);
                totalBytesLength += bytes.length;
                if (bytes.length < 4096) {
                    totalFrame1 = 1;
                } else {
                    totalFrame1 = bytes.length % 4096 != 0 ? ((bytes.length / 1024 / 4) + 1) : (bytes.length / 1024 / 4);
                }
                allTotleFrame += totalFrame1;
//                }
            }
//            Log.e("totalLength==", totalBytesLength + "");
            int crcLength = totalBytesLength % 4096 == 0 ? totalBytesLength / 4096 : totalBytesLength / 4096 + 1;
            totalBytesLength = totalBytesLength + crcLength;
            int packets = totalBytesLength % 19 == 0 ? totalBytesLength / 19 : totalBytesLength / 19 + 1;
            totalBytesLength = totalBytesLength + packets;
//            Log.e("totalLength1==", totalBytesLength + "");
            totalPackets = totalBytesLength % 20 == 0 ? totalBytesLength / 20 : totalBytesLength / 20 + 1;
//            Log.e("totalPackets==", totalPackets + "");
        } catch (Exception e) {
            Log.e("data_sort----", e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 解析每个bin文件,转为byte数组
     *
     * @param currentBin
     * @return
     */
    private byte[] getDataBytes(int currentBin) {
        byte[] currentBytes = null;
        if (fileNameList == null || fileNameList.size() == 0) {
            if (dataCallback != null) {
                dataCallback.fileNotFound("no file was found");
            }
        } else {
            try {
                String filePath = fileNameList.get(currentBin - 1);
                FileInputStream file = new FileInputStream(new File(filePath));
                initialTotalBytes = TransformUtils.streamToByte(file);
                if (initialTotalBytes != null) {
                    if (initialTotalBytes.length < 4096) {
                        totalFrame = 1;
                    } else {
                        totalFrame = initialTotalBytes.length % 4096 != 0 ? ((initialTotalBytes.length / 1024 / 4) + 1) : (initialTotalBytes.length / 1024 / 4);
                    }
                    Log.e("initialLength----", initialTotalBytes.length + "/" + totalFrame);
                    if (CURRENT_ACTION == ActionUtils.ACTION_OTA_NEXT_BIN || CURRENT_ACTION == ActionUtils.ACTION_OTA_NOTIFY) {
                        handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_ORDER_I, 20);
                    }
                }
            } catch (Exception e) {
                Log.e("ble file data----", "file data can not found");
            }

        }
        return initialTotalBytes;
    }

    /**
     * 与追踪的service结合,设备可能已经连接,直接传入device启动下一步逻辑
     *
     * @param mac
     */
    public void connectDevice(String mac) {
        this.macAddress = mac;
        BleDevice device = petTrackerHelper.getConnectDevice(mac);
        if (device == null) {
            return;
        }
        this.bleDevice = device;
        connectToDevice(bleDevice.getDevice());
        handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OPEN_NOTIFY, 50);
    }


    /**
     * 处理eventbus发来的数据,由追踪的service发出
     *
     * @param action
     */
//    @Subscribe(threadMode = ThreadMode.MAIN)
    public void eventBusCall(String action) {
//        String action = msgBean.getAction1();
//        String action1 = msgBean.getAction2();
        if (action != null) {
//            BleDevice device = (BleDevice) msgBean.getObject();
            if (action.equals(PetTrackerHelper.BLE_CONNECT_SUCCESS)) {//连接成功

            } else if (action.equals(PetTrackerHelper.BLE_RECONNECT_SUCCESS)) {//重连成功
                CURRENT_ACTION = PetTrackerHelper.BLE_RECONNECT_SUCCESS_I;
                openNotify();
            } else if (action.equals(PetTrackerHelper.BLE_DISCONNECT)) {//断开连接,需要处理重传的逻辑
                String cache = totalPackets + "," + transforPacket + "," + currentBin + "," + currentFrame + "," + currentTotalPacket;
                SharePreferenceUtils.setValue(this, macAddress, cache);
                SharePreferenceUtils.setValue(this, macAddress + "-b", frameHeadBytes);
            }
        }
    }

    /***************************************fota升级 end***************************************************/

    /***************************************fota升级需要的回调 start***************************************************/

    /**
     * 升级各种状态回调
     *
     * @param upgradeStatusCallback
     */
    public void setUpgradeStatusCallback(UpgradeStatusCallback upgradeStatusCallback) {
        this.statusCallback = upgradeStatusCallback;
    }

    /**
     * 数据传输进度回调
     *
     * @param callback
     */
    public void setProgressCallback(ProgressCallback callback) {
        this.progressCallback = callback;
    }

    /**
     * 通知回调
     *
     * @param notifyCallback
     */
    public void setNotifyCallback(NotifyCallback notifyCallback) {
        this.notifyCallback = notifyCallback;
    }

    /**
     * 写入数据回调
     *
     * @param writeCallback
     */
    public void setWriteCallback(WriteCallback writeCallback) {
        this.writeCallback = writeCallback;
    }

    /**
     * 涉及数据的所有回调
     *
     * @param dataCallback
     */
    public void setDataCallback(DataCallback dataCallback) {
        this.dataCallback = dataCallback;
    }

    /***************************************fota升级需要的回调 end***************************************************/

    /**
     * <p>Adds the given handler to the targets list for messages from this service.</p>
     * <p>This method is synchronized to avoid to a call on the list of listeners while modifying it.</p>
     *
     * @param handler The Handler for messages.
     */
    public synchronized void addHandler(Handler handler) {
        if (!mAppListeners.contains(handler)) {
            this.mAppListeners.add(handler);
        }
    }

    /**
     * <p>Removes the given handler from the targets list for messages from this service.</p>
     * <p>This method is synchronized to avoid to a call on the list of listeners while modifying it.</p>
     *
     * @param handler The Handler to remove.
     */
    public synchronized void removeHandler(Handler handler) {
        if (mAppListeners.contains(handler)) {
            this.mAppListeners.remove(handler);
        }
    }

    /**
     * <p>To get the current {@link ResumePoints ResumePoints} of the Upgrade.</p>
     * <p>If there is no ongoing upgrade this information is useless and not accurate.</p>
     *
     * @return The current known resume point of the upgrade.
     */
    public @Enums
    int getResumePoint() {
        return mGaiaManager.getResumePoint();
    }

    /**
     * <p>To abort the upgrade. This method only acts if the Device is connected. If the Device is not connected, there
     * is no ongoing upgrade on the Device side.</p>
     */
    public void abortUpgrade() {
        if (super.getConnectionState() == State.CONNECTED) {
            if (mRWCPManager.isInProgress()) {
                mRWCPManager.cancelTransfer();
            }
            mProgressQueue.clear();
            mGaiaManager.abortUpgrade();
        }
    }

    /**
     * <p>To inform the Upgrade process about a confirmation it is waiting for.</p>
     *
     * @param type         The type of confirmation the Upgrade process is waiting for.
     * @param confirmation True if the Upgrade process should continue, false to abort it.
     */
    public void sendConfirmation(@ConfirmationType int type, boolean confirmation) {
        mGaiaManager.sendConfirmation(type, confirmation);
    }

    /**
     * To disconnect the connected device if there is any.
     */
    public void disconnectDevice() {
        sendMessageToListener(MessageType.CONNECTION_STATE_HAS_CHANGED, State.DISCONNECTING);
        if (super.getConnectionState() == State.DISCONNECTED) {
            resetDeviceInformation();
            sendMessageToListener(MessageType.CONNECTION_STATE_HAS_CHANGED, State.DISCONNECTED);
        } else {
            unregisterNotifications();
            disconnectFromDevice();
        }
    }

    /**
     * <p>To know id there is an upgrade on going.</p>
     *
     * @return true if an upgrade is already working, false otherwise.
     */
    public boolean isUpdating() {
        return mGaiaManager.isUpdating();
    }

    /**
     * <p>To know if the GAIA protocol is supported by the connected device.</p>
     * <p>If there is no device connected or if the service does not know yet if a connected device supports that
     * protocol, this method returns {@link Support Sopport#DO_NOT_KNOW DO_NOT_KNOW}.</p>
     *
     * @return One of the values of the {@link Support Support} enumeration.
     */
    public @Support
    int isGaiaSupported() {
        return mIsGaiaSupported;
    }

    /**
     * <p>To know if the Upgrade protocol is supported by the connected device.</p>
     * <p>If there is no device connected or if the service does not know yet if a connected device supports that
     * protocol, this method returns {@link Support #DO_NOT_KNOW DO_NOT_KNOW.</p>
     *
     * @return One of the values of the {@link Support Support} enumeration.
     */
    public @Support
    int isUpgradeSupported() {
        return mIsUpgradeSupported;
    }


    // ====== ANDROID SERVICE ======================================================================

    //----------------------------------------------------------------------------------------------

    /**
     * <p>To get the bond state of the selected device.</p>
     *
     * @return Any of the bond states used by the {@link BluetoothDevice BluetoothDevice} class:
     * {@link BluetoothDevice#BOND_BONDED BOND_BONDED}, {@link BluetoothDevice#BOND_BONDING BOND_BONDING} or
     * {@link BluetoothDevice#BOND_NONE BOND_NONE}. If there is no device defined for this service, this method
     * returns {@link BluetoothDevice#BOND_NONE BOND_NONE}.
     */
    @SuppressLint("MissingPermission")
    public int getBondState() {
        BluetoothDevice device = getDevice();
        return device != null ? device.getBondState() : BluetoothDevice.BOND_NONE;
    }

    /**
     * <p>Gets the connection state between the service and a BLE device.</p>
     *
     * @return the connection state.
     */
    public @State
    int getConnectionState() {
        return super.getConnectionState();
    }

    /**
     * <p>Gets the device with which this service is connected or has been connected.</p>
     *
     * @return the BLE device.
     */
    public BluetoothDevice getDevice() {
        return super.getDevice();
    }

    /**
     * <p>To enable the maximum MTU size supported by the device.</p>
     * <p>If it is requested to enable the maximum MTU size, this method will start the negotiations with
     * <code>256</code> as the requested size. The maximum Android can is
     * {@link BLEService#MTU_SIZE_MAXIMUM MTU_SIZE_MAXIMUM}.</p>
     * <p>If it is requested to disable the maximum MTU size, this method sets up the MTU size at its default value:
     * {@link BLEService#MTU_SIZE_DEFAULT MTU_SIZE_DEFAULT}.</p>
     *
     * @param enabled True to activate the maximum possible MTU size, false to set up the MTU size to default.
     * @return True if the request could be queued.
     */
    //判断是否支持MTU(最大传输单元,默认20或者23)设置,默认最大256字节,请求设备支持最大支持多大(目前板子为103字节)
    public boolean enableMaximumMTU(boolean enabled) {
        final int MAX_MTU_SUPPORTED = 256;
        //noinspection UnnecessaryLocalVariable
        final int DEFAULT_MTU = MTU_SIZE_DEFAULT;

        int size = enabled ? MAX_MTU_SUPPORTED : DEFAULT_MTU;

        return requestMTUSize(size);
    }

    /**
     * <p>To enable or  the RWCP transfer mode. This method checks if the RWCP mode might be supported prior to
     * attempt to activate it using the GAIA protocol by calling
     * {@link GaiaUpgradeManager#setRWCPMode(boolean) setRWCPMode}.</p>
     *
     * @param enabled True to set the transfer mode to RWCP, false otherwise.
     * @return True if the request could be initiated.
     */
    public boolean enableRWCP(boolean enabled) {
        if (!mIsRWCPSupported && enabled) {
            Log.w(TAG, "Request to enable or disable RWCP received but the feature is not supported.");
            return false;
        }

        mGaiaManager.setRWCPMode(enabled);
        return true;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Service bound");
        this.initialize();
        initHelper();
//        onHandleIntent(intent);
        this.showDebugLogs(Consts.DEBUG);
        mRWCPManager.showDebugLogs(Consts.DEBUG);
        registerBondReceiver();
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Service unbound");
        unregisterBondReceiver();
        disconnectDevice();
        return super.onUnbind(intent);
    }

    /*
     * The system calls this method when the service is no longer used and is being destroyed. Your service should
     * implement this to clean up any resources such as threads, registered listeners, receivers, etc. This is the last
     * call the service receives.
     */
    @Override
    public void onDestroy() {
        disconnectDevice();
        Log.i(TAG, "service destroyed");
        super.onDestroy();
    }

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void onVMUpgradeDisconnected() {
    }


    // ====== IMPLEMENTED GAIA METHODS ===============================================================

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void onResumePointChanged(@Enums int point) {
        sendMessageToListener(MessageType.UPGRADE_STEP_HAS_CHANGED, point);
    }

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void onUpgradeError(UpgradeError error) {
        if (DEBUG) Log.e(TAG, "ERROR during upgrade: " + error.getString());
        sendMessageToListener(MessageType.UPGRADE_ERROR, error);
        if (mRWCPManager.isInProgress()) {
            mRWCPManager.cancelTransfer();
            mProgressQueue.clear();
        }
    }

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void onUploadProgress(UploadProgress progress) {
        if (mGaiaManager.isRWCPEnabled()) {
            // queued the progress as the transmission to the device is validated by the RWCP manager
            mProgressQueue.add(progress);
        } else {
            sendMessageToListener(MessageType.UPGRADE_UPLOAD_PROGRESS, progress);
        }
    }

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public boolean sendGAIAPacket(byte[] data, boolean isTransferringData) {
        if (mGaiaManager.isRWCPEnabled() && isTransferringData) {
            if (mTransferStartTime <= 0) {
                mTransferStartTime = System.currentTimeMillis();
            }
            mRWCPManager.sendData(data);
            return true;
        } else {
            boolean done = requestWriteCharacteristic(mCharacteristicGaiaCommand, data);
            if (done && DEBUG) {
                Log.i(TAG, "Attempt to send GAIA packet on COMMAND characteristic: " + Utils.getStringFromBytes(data));
            } else if (!done) {
                Log.w(TAG, "Attempt to send GAIA packet on COMMAND characteristic FAILED: " + Utils.getStringFromBytes
                        (data));
            }
            return done;
        }
    }

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void onUpgradeFinish() {
        sendMessageToListener(MessageType.UPGRADE_FINISHED);
    }

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void askConfirmationFor(@ConfirmationType int type) {
        if (!sendMessageToListener(MessageType.UPGRADE_REQUEST_CONFIRMATION, type)) {
            // default behaviour? use a notification?
            sendConfirmation(type, true);
        }
    }

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void onUpgradeSupported(boolean supported) {
        mIsUpgradeSupported = supported ? Support.SUPPORTED : Support.NOT_SUPPORTED;
        sendMessageToListener(MessageType.UPGRADE_SUPPORT, mIsUpgradeSupported);
    }

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void onRWCPEnabled(boolean enabled) {
        requestCharacteristicNotification(mCharacteristicGaiaData, enabled);
    }

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void onRWCPNotSupported() {
        mIsRWCPSupported = false;
        sendMessageToListener(MessageType.RWCP_SUPPORTED, false);
    }

    @Override // RWCPManager.RWCPListener
    public boolean sendRWCPSegment(byte[] bytes) {
        boolean done = requestWriteNoResponseCharacteristic(mCharacteristicGaiaData, bytes);
        if (done && DEBUG) {
            Log.i(TAG, "Attempt to send RWCP segment on DATA ENDPOINT characteristic: "
                    + Utils.getStringFromBytes(bytes));
        } else if (!done) {
            Log.w(TAG, "Attempt to send RWCP segment on DATA ENDPOINT characteristic FAILED: "
                    + Utils.getStringFromBytes(bytes));
        }
        return done;
    }


    // ====== IMPLEMENTED RWCP METHODS ===============================================================

    @Override // RWCPManager.RWCPListener
    public void onTransferFailed() {
        abortUpgrade();
        sendMessageToListener(MessageType.TRANSFER_FAILED);
    }

    @Override // RWCPManager.RWCPListener
    public void onTransferFinished() {
        mGaiaManager.onTransferFinished();
        mProgressQueue.clear();
    }

    @Override // RWCPManager.RWCPListener
    public void onTransferProgress(int acknowledged) {
        UploadProgress progress = null;
        while (acknowledged > 0 && !mProgressQueue.isEmpty()) {
            progress = mProgressQueue.poll();
            acknowledged--;
        }
        if (progress != null) {
            // when using RWCP the elapsed time given by the Upgrade Manager is distorted
            long elapsed = System.currentTimeMillis() - mTransferStartTime;
            progress.setTime(elapsed);
            sendMessageToListener(MessageType.UPGRADE_UPLOAD_PROGRESS, progress);
        }
    }

    @Override // BLEService
    public boolean connectToDevice(BluetoothDevice device) {
        boolean done = super.connectToDevice(device);
        if (done) {
            sendMessageToListener(MessageType.CONNECTION_STATE_HAS_CHANGED, State.CONNECTING);
        }
        return done;
    }


    // ====== OTHER IMPLEMENTED METHODS ===============================================================

    @Override // BondStateReceiver.BondStateListener
    public void onBondStateChange(BluetoothDevice device, int state) {
        BluetoothDevice connectedDevice = getDevice();
        if (device != null && connectedDevice != null && device.getAddress().equals(connectedDevice.getAddress())) {
            Log.i(TAG, "ACTION_BOND_STATE_CHANGED for " + device.getAddress()
                    + " with bond state " + BLEUtils.getBondStateName(state));

            sendMessageToListener(MessageType.DEVICE_BOND_STATE_HAS_CHANGED, state);

            if (state == BluetoothDevice.BOND_BONDED) {
                requestCharacteristicNotification(GAIA_CHARACTERISTIC_RESPONSE_UUID, true);
            }
        }
    }

    /**
     * <p>To inform the listener by sending it a message.</p>
     *
     * @param message The message type to send.
     */
    @SuppressWarnings("UnusedReturnValue") // the return value is used for some implementations
    private boolean sendMessageToListener(@MessageType int message) {
        if (!mAppListeners.isEmpty()) {
            for (int i = 0; i < mAppListeners.size(); i++) {
                mAppListeners.get(i).obtainMessage(message).sendToTarget();
            }
        }
        return !mAppListeners.isEmpty();
    }


    // ====== ACTIVITY COMMUNICATION ===============================================================

    /**
     * <p>To inform the listener by sending it a message.</p>
     *
     * @param message The message type to send.
     * @param object  Any object to send to the listener.
     */
    private boolean sendMessageToListener(@MessageType int message, Object object) {
        if (!mAppListeners.isEmpty()) {
            for (int i = 0; i < mAppListeners.size(); i++) {
                mAppListeners.get(i).obtainMessage(message, object).sendToTarget();
            }
        }
        return !mAppListeners.isEmpty();
    }

    // extends BLEService
    @Override
    protected void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        Log.i(TAG, "onConnectionStateChange: " + BLEUtils.getGattStatusName(status, true));
        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            sendMessageToListener(MessageType.CONNECTION_STATE_HAS_CHANGED, State.CONNECTED);
            Log.i(TAG, "Attempting to start service discovery: " + gatt.discoverServices());
            // now wait for onServicesDiscovered to be called in order to communicate over GATT with the device
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            resetDeviceInformation();
            sendMessageToListener(MessageType.CONNECTION_STATE_HAS_CHANGED, State.DISCONNECTED);
            if (isUpdating()) {
                reconnectToDevice();
            }
            if (mRWCPManager.isInProgress()) {
                mRWCPManager.cancelTransfer();
                mProgressQueue.clear();
            }
        }
    }


    // ====== BLE SERVICE ==========================================================================

    // extends BLEService
    @Override
    protected void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            boolean gaiaServiceAvailable = false;
            boolean gaiaResponseCharacteristicAvailable = false;
            boolean gaiaCommandCharacteristicAvailable = false;
            boolean gaiaDataCharacteristicAvailable = false;

            // Loops through available GATT Services to know if GAIA service is available
            for (BluetoothGattService gattService : gatt.getServices()) {
                if (gattService.getUuid().equals(GAIA_SERVICE_UUID)) {
                    gaiaServiceAvailable = true;
                    // Loops through available Characteristics to know if GAIA services are available
                    for (BluetoothGattCharacteristic gattCharacteristic : gattService.getCharacteristics()) {
                        UUID characteristicUUID = gattCharacteristic.getUuid();
                        if (characteristicUUID.equals(GAIA_CHARACTERISTIC_RESPONSE_UUID)
                                && (gattCharacteristic.getProperties() & BluetoothGattCharacteristic
                                .PROPERTY_NOTIFY) > 0) {
                            gaiaResponseCharacteristicAvailable = true;
                        } else if (characteristicUUID.equals(GAIA_CHARACTERISTIC_COMMAND_UUID)
                                && (gattCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE)
                                > 0) {
                            mCharacteristicGaiaCommand = gattCharacteristic;
                            gaiaCommandCharacteristicAvailable = true;
                        } else if (characteristicUUID.equals(GAIA_CHARACTERISTIC_DATA_ENDPOINT_UUID)
                                && (gattCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ)
                                > 0) {
                            gaiaDataCharacteristicAvailable = true;
                            int properties = gattCharacteristic.getProperties();
                            mIsRWCPSupported =
                                    (properties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0
                                            && (properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0;
                            sendMessageToListener(MessageType.RWCP_SUPPORTED, mIsRWCPSupported);
                            mCharacteristicGaiaData = gattCharacteristic;
                        }
                    }
                }
            }

            boolean isGaiaSupported = gaiaServiceAvailable && gaiaCommandCharacteristicAvailable
                    && gaiaDataCharacteristicAvailable && gaiaResponseCharacteristicAvailable;

            mIsGaiaSupported = isGaiaSupported ? Support.SUPPORTED : Support.NOT_SUPPORTED;

            if (isGaiaSupported) {
                if (!isUpdating()) {
                    sendMessageToListener(MessageType.GAIA_SUPPORT, mIsGaiaSupported);
                }
                onGAIAServiceReady();
            } else {
                sendMessageToListener(MessageType.GAIA_SUPPORT, mIsGaiaSupported);
                StringBuilder message = new StringBuilder();
                message.append("GAIA Service ");
                if (gaiaServiceAvailable) {
                    message.append("available with the following characteristics: \n");
                    message.append(gaiaCommandCharacteristicAvailable ? "\t- GAIA COMMAND" : "\t- GAIA COMMAND not " +
                            "available or with wrong properties");
                    message.append(gaiaDataCharacteristicAvailable ? "\t- GAIA DATA" : "\t- GAIA DATA not " +
                            "available or with wrong properties");
                    message.append(gaiaResponseCharacteristicAvailable ? "\t- GAIA RESPONSE" : "\t- GAIA RESPONSE not" +
                            " available or with wrong properties");
                } else {
                    message.append("not available.");
                }
                Log.w(TAG, message.toString());
            }
        }
    }

    // extends BLEService
    @Override
    protected void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        // we expect this method to be called when inducing pairing is attempted.
        // if there is a successful read over the GAIA DATA CHARACTERISTIC, the application does not need to bond
        // and can process.
        if (characteristic != null) {
            UUID uuid = characteristic.getUuid();
            if (uuid.equals(GAIA_CHARACTERISTIC_DATA_ENDPOINT_UUID) && status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Successful read characteristic to induce pairing: no need to bond device.");
                requestCharacteristicNotification(GAIA_CHARACTERISTIC_RESPONSE_UUID, true);
            } else {
                Log.i(TAG, "Received reading over characteristic: " + characteristic.getUuid());
            }
        }
    }

    // extends BLEService
    @Override
    protected void onReceivedCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        if (characteristic != null) {
            UUID uuid = characteristic.getUuid();
            if (uuid.equals(GAIA_CHARACTERISTIC_RESPONSE_UUID)) {
                byte[] data = characteristic.getValue();
                if (data != null) {
                    mGaiaManager.onReceiveGAIAPacket(data);
                }
            } else if (uuid.equals(GAIA_CHARACTERISTIC_DATA_ENDPOINT_UUID)) {
                byte[] data = characteristic.getValue();
                if (data != null) {
                    mRWCPManager.onReceiveRWCPSegment(data);
                }
            } else {
                Log.i(TAG, "Received notification over characteristic: " + characteristic.getUuid());
            }
        }
    }

    /**
     * 写特征描述,在请求描述符写入操作时调用此方法。
     *
     * @param gatt       The Bluetooth gatt which requested the descriptor write operation.
     * @param descriptor The Bluetooth Characteristic Descriptor for which a request has been made.
     * @param status
     */
    @Override
    protected void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        UUID characteristicUuid = descriptor.getCharacteristic().getUuid();
        mNotifiedCharacteristics.add(characteristicUuid);
        if (characteristicUuid.equals(GAIA_CHARACTERISTIC_RESPONSE_UUID)) {
            mGaiaManager.onGAIAConnectionReady();
            if (mIsRWCPSupported) {
                mGaiaManager.getRWCPStatus();
            }
        } else if (characteristicUuid.equals(GAIA_CHARACTERISTIC_DATA_ENDPOINT_UUID)) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                boolean enabled = Arrays.equals(descriptor.getValue(),
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                sendMessageToListener(MessageType.RWCP_ENABLED, enabled);
            } else {
                mIsRWCPSupported = false;
                mGaiaManager.onRWCPNotSupported();
                sendMessageToListener(MessageType.RWCP_SUPPORTED, false);
            }
        }
    }

    // extends BLEService

    // extends BLEService
    @Override
    protected void onRemoteRssiRead(BluetoothGatt gatt, int rssi, int status) {

    }

    /**
     * 最大传输单元,此方法当调用修改蓝牙设备之间最大传输字节(默认最大20字节)方法(即BluetoothGatt.requestMtu())时被回调调用,前提是主从蓝牙设备都支持修改,否则无效
     *
     * @param gatt   The Bluetooth gatt which requested the remote rssi.
     * @param mtu    The mtu value chosen with the remote device.
     * @param status
     */
    @Override
    protected void onMTUChanged(BluetoothGatt gatt, int mtu, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.i(TAG, "MTU size had been updated to " + mtu);
            sendMessageToListener(MessageType.MTU_UPDATED, mtu);
        } else {
            Log.w(TAG, "MTU request failed, mtu size is: " + mtu);
            sendMessageToListener(MessageType.MTU_SUPPORTED, false);
        }

        // updating upgrade managers with the max size available.
        final int ATT_INFORMATION_LENGTH = 3;
        int dataSize = mtu - ATT_INFORMATION_LENGTH;
        mGaiaManager.setPacketMaximumSize(dataSize);
    }

    // extends BLEService

    // extends BLEService
    @Override
    protected void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
    }

    // extends BLEService
    @Override
    protected void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

    }

    @SuppressLint("NewApi")
    @Override
    public boolean reconnectToDevice() {
        boolean success = super.reconnectToDevice();
        if (isUpdating()) {
            super.getBluetoothGatt().requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        }
        return success;
    }

    /**
     * <p>To reset the values linked to the device when it's disconnected or disconnecting.</p>
     */
    private void resetDeviceInformation() {
        mIsGaiaSupported = Support.DO_NOT_KNOW;
        mIsUpgradeSupported = Support.DO_NOT_KNOW;
        mGaiaManager.reset();
        mRWCPManager.cancelTransfer();
        mCharacteristicGaiaCommand = null;
        mCharacteristicGaiaData = null;
        mProgressQueue.clear();
        mNotifiedCharacteristics.clear();
    }


    // ====== PRIVATE METHODS ======================================================================

    /**
     * <p>This method will act depending on the bond state of a connected device.</p>
     * <ul>
     * <li>Device bonded: the process for notification registration starts.</li>
     * <li>Device not bonded: the process to induce pairing if needed starts.</li>
     * </ul>
     */
    @SuppressLint("MissingPermission")
    private void onGAIAServiceReady() {
        BluetoothDevice device = getDevice();

        if (device == null) {
            return;
        }

        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
            // some applications need pairing, some other don"t.
            // to be able to cover all cases we read a characteristic in order to induce the pairing if needed
            // if pairing is requested the DATA characteristic requires pairing to be read.
            requestReadCharacteristicForPairing(GAIA_CHARACTERISTIC_DATA_ENDPOINT_UUID);
        } else {
            // carry on the process: device already paired
            Log.i(TAG, "Device already bonded " + device.getAddress());
            requestCharacteristicNotification(GAIA_CHARACTERISTIC_RESPONSE_UUID, true);
        }
    }

    /**
     * <p>To register the bond stat receiver to be aware of any bond state change.</p>
     */
    private void registerBondReceiver() {
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST);
        this.registerReceiver(mBondStateReceiver, filter);
    }

    /**
     * <p>To unregister the bond stat receiver when the application is stopped or we don't need it anymore.</p>
     */
    private void unregisterBondReceiver() {
        unregisterReceiver(mBondStateReceiver);
    }

    /**
     * <p>To unregister from all characteristic notifications this activity has registered.</p>
     */
    private void unregisterNotifications() {
        for (int i = 0; i < mNotifiedCharacteristics.size(); i++) {
            requestCharacteristicNotification(mNotifiedCharacteristics.get(i), false);
        }
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


    // ====== INNER CLASS ==========================================================================

    /**
     * <p>The class which allows an entity to communicate with this service when its bind.</p>
     */
    public class LocalBinder extends Binder {
        /**
         * <p>To retrieve the binder service.</p>
         *
         * @return the service.
         */
        public TrackerBleService getService() {
            return TrackerBleService.this;
        }
    }

}
