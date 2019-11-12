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
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.ly.bluetoothhelper.beans.MsgBean;
import com.ly.bluetoothhelper.beans.TransfirmDataBean;
import com.ly.bluetoothhelper.callbacks.DataCallback;
import com.ly.bluetoothhelper.callbacks.NotifyCallback;
import com.ly.bluetoothhelper.callbacks.ProgressCallback;
import com.ly.bluetoothhelper.callbacks.WriteCallback;
import com.ly.bluetoothhelper.helper.BLEHelper;
import com.ly.bluetoothhelper.helper.BleConnectHelper;
import com.ly.bluetoothhelper.helper.BluetoothHelper;
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
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.UUID;

import fastble.data.BleDevice;
import fastble.exception.BleException;

/**
 * <p>This {@link android.app.Service Service} allows to manage the Bluetooth communication with a device and can
 * work as a Start/Stop service.</p>
 * <p>This Service also manages the Upgrade process of a Device over the {@link com.ly.bluetoothhelper.oat.gaia.GAIA GAIA}
 * protocol. It uses the {@link GaiaUpgradeManager} and the {@link UpgradeManager} to manage the Upgrade process and
 * messages received and sent by a GAIA Bluetooth Device.</p>
 */
public class OtauBleService extends BLEService implements GaiaUpgradeManager.GaiaManagerListener,
        BondStateReceiver.BondStateListener, RWCPManager.RWCPListener {

    // --------------------------add by ly--------------------------
    private BleConnectHelper bluetoothHelper; //蓝牙操作辅助类
    private BluetoothHelper bluetoothHelper1; //蓝牙操作类
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
    private int cp = 0;
    private int tp = 0;
    private boolean mAuto = true;
    private int everyFrameTotalPacket = 0;
    //------------------------end---------------------------

    // ====== CONSTS FIELDS ========================================================================

    //-------------------------------------------GAIA各种UUID START----------------------------------------------------------
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

    //-----------------------------------------------------END-----------------------------------------------------------
    /**
     * To know if we are using the application in the debug mode.
     */
    private static final boolean DEBUG = Consts.DEBUG;


    // ====== PRIVATE FIELDS =======================================================================

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
    /**
     * <p>To get the time when the file transfer starts.</p>
     */
    private long mTransferStartTime = 0;
    private int totalPackets;


    // ====== PUBLIC METHODS =======================================================================

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
     * protocol, this method returns {@link com.ly.bluetoothhelper.oat.annotation.Support Sopport#DO_NOT_KNOW DO_NOT_KNOW}.</p>
     *
     * @return One of the values of the {@link com.ly.bluetoothhelper.oat.annotation.Support Support} enumeration.
     */
    public @Support
    int isGaiaSupported() {
        return mIsGaiaSupported;
    }

    /**
     * <p>To know if the Upgrade protocol is supported by the connected device.</p>
     * <p>If there is no device connected or if the service does not know yet if a connected device supports that
     * protocol, this method returns {@link com.ly.bluetoothhelper.oat.annotation.Support #DO_NOT_KNOW DO_NOT_KNOW.</p>
     *
     * @return One of the values of the {@link com.ly.bluetoothhelper.oat.annotation.Support Support} enumeration.
     */
    public @Support
    int isUpgradeSupported() {
        return mIsUpgradeSupported;
    }

    /**
     * <p>To start the Upgrade process with the given file.</p>
     *
     * @param file The file to use to upgrade the Device.
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
            if (bleDevice != null) {
                handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_NOTIFY, 200);
            }
        }
    }

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


    // ====== ANDROID SERVICE ======================================================================

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "Service bound");
        this.initialize();
        initHelper();
        onHandleIntent(intent);
        this.showDebugLogs(Consts.DEBUG);
        mRWCPManager.showDebugLogs(Consts.DEBUG);
        registerBondReceiver();
        return mBinder;
    }

    //----------------------------------------------------------------------------------------------

    /**
     * 初始化,升级需要用到的各种uuid
     */
    private void initHelper() {
        EventBus.getDefault().register(this);
        bluetoothHelper = BleConnectHelper.getInstance().init(getApplication(), 0);
        bluetoothHelper1 = new BluetoothHelper(getApplication(), 0, 10000);
        bluetoothHelper1.initUuid(null,
                "00005500-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5");
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void getBinData(TransfirmDataBean dataBean) {
        if (dataBean != null) {
            int what = dataBean.getWhat();
            long delay = dataBean.getDelayTimes();
            handler.sendEmptyMessageDelayed(what, delay);
        }
    }

    private boolean isTest = false;

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
                    getDataBytes(currentBin);
                    if (isTest) {
                        writeBytes(initialTotalBytes);
                    }
//                    openNotify();
                    break;

                case ActionUtils.ACTION_OTA_ORDER_I: //发送ota升级命令(含校验bin的合法性)
//                    Log.e("currentBin----", currentBin + "");
                    byte[] oadOrder = OrderSetUtils.ORDER_OAD;
                    byte[] binCountBytes = new byte[]{(byte) fileNameList.size(), (byte) currentBin};
                    byte[] headByte = TransformUtils.subBytes(initialTotalBytes, 0, 5);
                    byte[] checkBytes = TransformUtils.combineArrays(oadOrder, binCountBytes, headByte);
                    CURRENT_ACTION = ActionUtils.ACTION_OTA_ORDER_I;
                    writeBytes(checkBytes);
                    break;
                case ActionUtils.ACTION_OTA_DATA_HEAD_I: //发送ota数据帧帧头
                    if (currentFrame > totalFrame) {
                        //极端情况:断开连接正在发生当前帧最后一包,先忽略此种情况
                        return;
                    }
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
                    Log.e("frame----", currentFrame + "/" + totalFrame);
                    if (progressCallback != null && currentBin == 1 && currentFrame == 1) {
                        progressCallback.setMax(currentTotalPacket);
                    }
                    if (currentFrame <= totalFrame) {
                        currentFrameBytes = DataPacketUtils.sortEachFrame(initialTotalBytes, currentFrame, totalFrame);
                        currentTotalPacket = currentFrameBytes.length % 20 == 0 ? currentFrameBytes.length / 20 : currentFrameBytes.length / 20 + 1;
                        Log.e("curr---", currentFrameBytes.length + "/" + currentTotalPacket);
                        if (progressCallback != null) {
                            progressCallback.setMax(currentTotalPacket);
                        }
                        if (isReTransTest && isReconnect) {
                            int length = currentFrameBytes.length - 20 * currentPacket;
                            int offSet = currentPacket == 0 ? 0 : currentPacket - 1;
//                            Log.e("reData----", currentFrameBytes.length + "/" + length + "/" + currentPacket);
                            byte[] reTransBytes = TransformUtils.subBytes(currentFrameBytes, 20 * offSet, length);
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
                                SharePreferenceUtils.setValue(OtauBleService.this, "data-" + macAddress, "");
                            }
                            return;
                        }
                        currentFrame++;
                        if (currentFrame <= totalFrame) {
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 0);
                            if (dataCallback != null) {
                                dataCallback.nextFrame(currentFrame, totalFrame);
                            }

                        }
                    } else {
                        if (loseList.length == 1) { //丢包过多,需重发此帧
                            toast("丢包过多,重新传输");
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 100);
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
                    String cacheKey = macAddress.replaceAll(":", "");
                    SharedPreferences preferences = getSharedPreferences("progress_cache", MODE_PRIVATE);
                    String cache = preferences.getString(cacheKey, "");
//                    Object cacheOb = SharePreferenceUtils.getValue(OtauBleService.this, cacheKey, null);
                    Log.e("ob----", cache + "/" + macAddress);
                    if (cache.contains(",")) {
                        String[] cacheInfos = cache.split(",");
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
                            if (currentTotalPacket == cpa || cpa > currentTotalPacket) {
                                currentFrame++;
                                handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 1000);
                            }
                            if (cpa < currentTotalPacket) {
                                handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_DATA_I, 1000);
                            }
                        } else {
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 1000);
                        }
                    }
                    break;
                case ActionUtils.ACTION_DEVICE_RECONNECT:
                    isReconnect = true;
                    connect();
//                    bluetoothHelper.openVirtualLeash(true, macAddress, "");
                    break;
                case ActionUtils.ACTION_OPEN_NOTIFY:
                    openNotify();
                    break;
                case ActionUtils.ACTION_UNPAIR_AND_CONNECT:
                    connectDevice(true);
                    break;
            }
        }
    };

    int testPacket = 0;

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

    private int cc = 0;
    long s = 0;

    private void writeBytes(byte[] datas) {

        bluetoothHelper1.write(bleDevice, Consts.betweenTimes, datas, new BluetoothHelper.WriteListener() {
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                if (isTest) {
                    if (current == 1) {
                        s = System.currentTimeMillis();
                    }
                    if (current == 216) {
                        s = System.currentTimeMillis() - s;
                        Log.e("time-----", s + "");
                        s = 0;
                    }
                    Log.e("justWrite----", TransformUtils.bytesToHexString(justWrite));
                }
                if (writeCallback != null) {
                    writeCallback.writeSuccess(CURRENT_ACTION, current, total, justWrite);
                }

                if (CURRENT_ACTION == ActionUtils.ACTION_OTA_ORDER_I) {
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_VALIFY_OUTTIME, 60000);
                } else if (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_HEAD_I) {
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_DATA_I, 0);
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

    private int transforPacket;

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
                if (isReconnect) {
                    if (notifyCallback != null) {
                        notifyCallback.deviceReconn();
                    }

                } else {
//                    getDataBytes(currentBin);
//                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_ORDER_I, 3000);
                }
                if (!mAuto) {
                    startUpgrade(null);
                    mAuto = true;
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
                            handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_HEAD_I, 20);
                        }
                    } else if (data[6] == (byte) 0x03 && data[5] == (byte) 0x20) { //ota数据包
                        //成功接收完一帧,开始发送下一帧
                        //0xAB 0x00 0x04 0x03  0x01  0x20 0x03  0x03  0x01 0x0A 0x64 收包回复
                        //帧头 数据-长度 总帧 当前帧 MId  EId  丢包数 之后为丢包序号(上限五包,超过填0xFF)
                        //若出现丢包情况,尚未确定是继续下一帧还是先补包
                        //帧回复data异常,回复命令以触发再次回复
                        if (CURRENT_ACTION == ActionUtils.ACTION_OTA_DATA_DATA_I) {
                            if (data.length < 9) {
                                byte[] curFrameBytes = DataPacketUtils.currentPacket(initialTotalBytes, currentFrame, totalFrame);
                                byte[] dataHeadBytes = DataPacketUtils.eachFrameFirstPacket(curFrameBytes.length, totalFrame, currentFrame);
                                byte[] responeOrderBytes = TransformUtils.combineArrays(dataHeadBytes, new byte[]{(byte) 0xFF});
                                writeBytes(responeOrderBytes);
                            }else {
                                byte[] losePacketList = DataPacketUtils.losePackets(currentFrameBytes, data);
                                loseList = losePacketList;
                                if (isTest) {
                                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_LOSE_I, 0);
                                } else {
                                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_DATA_LOSE_I, 0);
                                }
                            }
                        }

                    }
                }



                //bin回复data异常,回复命令以触发再次回复
                if (CURRENT_ACTION == ActionUtils.ACTION_OTA_ORDER_I) {
                    if (data.length < 8) {
                        //待定.....
//                        byte[] curFrameBytes = DataPacketUtils.currentPacket(initialTotalBytes, currentFrame, totalFrame);
//                        byte[] dataHeadBytes = DataPacketUtils.eachFrameFirstPacket(curFrameBytes.length, totalFrame, currentFrame);
//                        byte[] responeOrderBytes = TransformUtils.combineArrays(dataHeadBytes, new byte[]{(byte) 0xFF});
//                        writeBytes(responeOrderBytes);
                    }
                }
            }
        });

    }

    private void initData() {
        int totalBytesLength = 0;//计算总进度使用
        try {
            AssetManager assetManager = getAssets();
            String[] assetsList = assetManager.list("");
            for (int i = assetsList.length - 1; i >= 0; i--) {
                if (assetsList[i].startsWith("high")) {
                    fileNameList.add(assetsList[i]);
                    InputStream inputStream = getResources().getAssets().open(assetsList[i]);
                    byte[] bytes = TransformUtils.streamToByte(inputStream);
                    totalBytesLength += bytes.length;
                }
            }
//            Log.e("totalLength==", totalBytesLength + "");
            int crcLength = totalBytesLength % 4096 == 0 ? totalBytesLength / 4096 : totalBytesLength / 4096 + 1;
            totalBytesLength = totalBytesLength + crcLength;
            int packets = totalBytesLength % 19 == 0 ? totalBytesLength / 19 : totalBytesLength / 19 + 1;
            totalBytesLength = totalBytesLength + packets;
//            Log.e("totalLength1==", totalBytesLength + "");
            totalPackets = totalBytesLength % 20 == 0 ? totalBytesLength / 20 : totalBytesLength / 20 + 1;
//            Log.e("totalPackets==", totalPackets + "");
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
                totalFrame = initialTotalBytes.length % 4096 != 0 ? ((initialTotalBytes.length / 1024 / 4) + 1) : (initialTotalBytes.length / 1024 / 4);
                Log.e("initialLength----", initialTotalBytes.length + "/" + totalFrame);
                if (CURRENT_ACTION == ActionUtils.ACTION_OTA_NEXT_BIN || CURRENT_ACTION == ActionUtils.ACTION_OTA_NOTIFY) {
                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_ORDER_I, 20);
                }
//                else if (CURRENT_ACTION == ActionUtils.ACTION_OTA_NOTIFY){
//                    handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_ORDER_I, 1000);
//                }
            } catch (IOException e) {
                Log.e("ble file data----", "file data can not found");
            }

        }
        return initialTotalBytes;
    }

    /**
     * 与追踪的service结合,设备可能已经连接,直接传入device启动下一步逻辑
     *
     * @param device
     */
    public void connectedNext(BleDevice device) {
        if (device != null) {
            connectSuccess(device);
        }
    }

    public void scanAndConn(String macAddress) {
        this.macAddress = macAddress;
        connect();
    }

    private void connectSuccess(BleDevice bleDevice) {
        this.bleDevice = bleDevice;
        this.macAddress = bleDevice.getMac();
        connectToDevice(bleDevice.getDevice());
//        bluetoothHelper1.setMTU(bleDevice, 216, new BluetoothHelper.MTUSetListener() {
//            @Override
//            public void setFail(String err) {
//                Log.e("mtuFail---", err);
//            }
//
//            @Override
//            public void setSuccess(int mtu) {
//                Log.e("mtuOk---", mtu + "");
//            }
//        });
        handler.sendEmptyMessageDelayed(ActionUtils.ACTION_OPEN_NOTIFY, 50);
        MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_SUCCESS_S, null, bleDevice);
        EventBus.getDefault().post(msgBean);
    }

    public void connectDevice(boolean isAuto) {
        this.mAuto = isAuto;
        bluetoothHelper.openVirtualLeash(true, macAddress, "");
        bluetoothHelper.setConnectSuccessListener((bleDevice, gatt) -> {
            if (bleDevice != null) {
                connectSuccess(bleDevice);
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

        bluetoothHelper.setDisconnectListener((isActive, bleDevice, bluetoothGatt) -> { //第一个参数为是否是手动断开
            //断开连接,记录数据传输情况
            isReconnect = true;
            disconnectDevice();
            if (currentFrame < totalFrame) {
                Log.e("cache---", currentBin + "/" + totalFrame + "/" + currentFrame + "/" + currentPacket + "/" + macAddress);
                String spCache = currentBin + "," + totalFrame + "," + currentFrame + "," + tp + "," + currentPacket + 1;
                String cacheKey = macAddress.replaceAll(":", "");
                SharedPreferences preferences = getSharedPreferences("progress_cache", MODE_PRIVATE);
                preferences.edit().putString(cacheKey, spCache).commit();
//                SharePreferenceUtils.setValue(this, cacheKey, spCache); //记录当前设备传输情况
            }
            MsgBean msgBean = new MsgBean(ActionUtils.ACTION_DISCONNECT_S, bleDevice);
            EventBus.getDefault().post(msgBean);
        });
        bluetoothHelper.setReconnectSuccessListener((bleDevice -> {
            //若重新连接上,尚未确定是继续传输还是???,暂定为关闭service,若下次启动,将从断位开始
//                stopSelf();
            reconnectToDevice();
            MsgBean msgBean = new MsgBean(ActionUtils.ACTION_CONNECT_SUCCESS_S, bleDevice);
            EventBus.getDefault().post(msgBean);
        }));
        //监听characteristic变化
        bluetoothHelper.setCharacteristicChangeListener(((gatt, characteristic) -> {
            Log.e("crtChange---", TransformUtils.bytes2String(characteristic.getValue()));
        }));
    }

    private void connect() {
        boolean isBond = bluetoothHelper1.connectOnBondDevice(macAddress);
        long delayTimes = 0;
        if (isBond) {
            delayTimes = 100;
        }
        handler.sendEmptyMessageDelayed(ActionUtils.ACTION_UNPAIR_AND_CONNECT, delayTimes);
    }

    public void handleConnect(String mac) {
        this.macAddress = mac;
        connect();
    }

    protected void onHandleIntent(Intent intent) {
        initData();
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

    public void startBleUpgrade() {
        if (bleDevice == null) {

        }
    }

    private void toast(String msg) {
        Toast.makeText(this, "show---:" + msg, Toast.LENGTH_LONG).show();
    }

    //---------------------------------------------------------------------------------------------
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


    // ====== IMPLEMENTED GAIA METHODS ===============================================================

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void onVMUpgradeDisconnected() {
    }

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


    // ====== IMPLEMENTED RWCP METHODS ===============================================================

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


    // ====== OTHER IMPLEMENTED METHODS ===============================================================

    @Override // BLEService
    public boolean connectToDevice(BluetoothDevice device) {
        boolean done = super.connectToDevice(device);
        if (done) {
            sendMessageToListener(MessageType.CONNECTION_STATE_HAS_CHANGED, State.CONNECTING);
        }
        return done;
    }

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


    // ====== ACTIVITY COMMUNICATION ===============================================================

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


    // ====== BLE SERVICE ==========================================================================

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

    // extends BLEService

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
    @Override
    protected void onRemoteRssiRead(BluetoothGatt gatt, int rssi, int status) {

    }

    // extends BLEService

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


    // ====== PRIVATE METHODS ======================================================================

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
        public OtauBleService getService() {
            return OtauBleService.this;
        }
    }

}
