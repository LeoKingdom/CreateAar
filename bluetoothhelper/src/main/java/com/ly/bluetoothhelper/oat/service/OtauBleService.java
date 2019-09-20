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
import android.support.annotation.IntDef;
import android.util.Log;

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
import com.ly.bluetoothhelper.utils.Consts;
import com.ly.bluetoothhelper.utils.Utils;

import java.io.File;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

/**
 * <p>This {@link android.app.Service Service} allows to manage the Bluetooth communication with a device and can
 * work as a Start/Stop service.</p>
 * <p>This Service also manages the Upgrade process of a Device over the {@link com.ly.bluetoothhelper.oat.gaia.GAIA GAIA}
 * protocol. It uses the {@link GaiaUpgradeManager} and the {@link UpgradeManager} to manage the Upgrade process and
 * messages received and sent by a GAIA Bluetooth Device.</p>
 */
public class OtauBleService extends BLEService implements GaiaUpgradeManager.GaiaManagerListener,
        BondStateReceiver.BondStateListener, RWCPManager.RWCPListener {


    // ====== CONSTS FIELDS ========================================================================

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
     * onResumePointChanged(@ResumePoints.Enum int point);  当升级过程中的步骤有进展时，将调用此方法。
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
    private @Support int mIsGaiaSupported = Support.DO_NOT_KNOW;
    /**
     * To keep the information that the upgrade protocol is supported by the connected device.
     */
    private @Support int mIsUpgradeSupported = Support.DO_NOT_KNOW;
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


    // ====== ENUM =================================================================================

    /**
     * <p>All types of messages which can be sent to an app linked to this service.</p>
     */
    @IntDef(flag = true, value = {MessageType.CONNECTION_STATE_HAS_CHANGED, MessageType.GAIA_SUPPORT,
            MessageType.UPGRADE_FINISHED, MessageType.UPGRADE_REQUEST_CONFIRMATION, MessageType.UPGRADE_SUPPORT,
            MessageType.UPGRADE_STEP_HAS_CHANGED, MessageType.UPGRADE_ERROR, MessageType.UPGRADE_UPLOAD_PROGRESS,
            MessageType.DEVICE_BOND_STATE_HAS_CHANGED, MessageType.RWCP_ENABLED, MessageType.TRANSFER_FAILED,
            MessageType.RWCP_SUPPORTED, MessageType.MTU_SUPPORTED, MessageType.MTU_UPDATED })
    @Retention(RetentionPolicy.SOURCE)
    @SuppressLint("ShiftFlags") // values are more readable this way
    public @interface MessageType {
        /**
         * <p>To inform that the connection state with the selected device has changed.</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>The connection state of the device as: {@link BLEService.State#CONNECTED
         *     CONNECTED}, {@link BLEService.State#CONNECTING CONNECTING},
         *     {@link BLEService.State#DISCONNECTING DISCONNECTING} or
         *     {@link BLEService.State#DISCONNECTED DISCONNECTED}. This information is contained
         *     in <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int CONNECTION_STATE_HAS_CHANGED = 0;
        /**
         * <p>To inform if the GAIA Service and required characteristics are supported by the device.</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>The support of the feature as a value of {@link Support Support}.<br/>
         *     This information is contained in <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int GAIA_SUPPORT = 1;
        /**
         * <p>To inform if the GAIA commands for upgrading a Device are supported by the device.</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>The support of the feature as a value of {@link Support Support}.<br/>
         *     This information is contained in <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int UPGRADE_SUPPORT = 2;
        /**
         * To inform that the upgrade process has successfully ended.
         */
        int UPGRADE_FINISHED = 3;
        /**
         * <p>To inform that the upgrade process needs he user to confirm the process to continue.</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>{@link UpgradeManager.ConfirmationType ConfirmationType}
         *     information contained in <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int UPGRADE_REQUEST_CONFIRMATION = 4;
        /**
         * <p>To inform that a new step has been reached during the process.</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>{@link ResumePoints ResumePoints}
         *     information contained in <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int UPGRADE_STEP_HAS_CHANGED = 5;
        /**
         * <p>To inform that an error occurs during the upgrade process.</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>{@link UpgradeError UpgradeError}
         *     information contained in <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int UPGRADE_ERROR = 6;
        /**
         * <p>To inform on the progress of the file upload.</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>{@link UploadProgress UploadProgress}
         *     information contained in <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int UPGRADE_UPLOAD_PROGRESS = 7;
        /**
         * <p>To inform that the device bond state has changed.</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>the bond state value which can be one of the following:
         *     {@link BluetoothDevice#BOND_BONDED BOND_BONDED}, {@link BluetoothDevice#BOND_BONDING BOND_BONDING} and
         *     {@link BluetoothDevice#BOND_NONE BOND_NONE}.<br/>This information is contained in
         *     <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int DEVICE_BOND_STATE_HAS_CHANGED = 8;
        /**
         * <p>To inform if RWCP mode is supported by the device.</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>The support of the feature as a boolean: true if the feature is supported, false otherwise.
         *     This information is contained in <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int RWCP_SUPPORTED = 9;
        /**
         * <p>To inform if RWCP mode is enabled by the device.</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>The support of the feature as a boolean: true if the feature is enabled, false otherwise.
         *     This information is contained in <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int RWCP_ENABLED = 10;
        /**
         * <p>To inform that the transfer of bytes during the transfer has failed.</p>
         * <p>The upgrade is aborted.</p>
         */
        int TRANSFER_FAILED = 11;
        /**
         * <p>To inform if MTU size is supported by the device.</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>The support of the feature as a boolean: true if the feature is supported, false otherwise.
         *     This information is contained in <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int MTU_SUPPORTED = 12;
        /**
         * <p>To inform about the new MTU size which had been negotiated with the device..</p>
         * <p>This type of {@link android.os.Message Message} also contains:</p>
         * <ul>
         *     <li>The value of the new size as an <code>int</code>.
         *     This information is contained in <code>{@link android.os.Message#obj obj}</code>.</li>
         * </ul>
         */
        int MTU_UPDATED = 13;
    }

    /**
     * <p>All types of support the service can know for a the qualified element such as: a feature, a protocol, etc.</p>
     */
    @IntDef(flag = true, value = { Support.NOT_SUPPORTED, Support.SUPPORTED, Support.DO_NOT_KNOW })
    @Retention(RetentionPolicy.SOURCE)
    @SuppressLint("ShiftFlags") // values are more readable this way
    public @interface Support {
        /**
         * <p>The element is not supported.</p>
         */
        int NOT_SUPPORTED = 0;
        /**
         * <p>The element us supported.</p>
         */
        int SUPPORTED = 1;
        /**
         * <p>The service does not know if the element is supported.</p>
         */
        int DO_NOT_KNOW = 2;
    }


    // ====== PUBLIC METHODS =======================================================================

    /**
     * <p>Adds the given handler to the targets list for messages from this service.</p>
     * <p>This method is synchronized to avoid to a call on the list of listeners while modifying it.</p>
     *
     * @param handler
     *         The Handler for messages.
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
     * @param handler
     *         The Handler to remove.
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
    public @ResumePoints.Enum int getResumePoint() {
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
     * @param type
     *        The type of confirmation the Upgrade process is waiting for.
     * @param confirmation
     *        True if the Upgrade process should continue, false to abort it.
     */
    public void sendConfirmation(@UpgradeManager.ConfirmationType int type, boolean confirmation) {
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
        }
        else {
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
     * protocol, this method returns {@link Support#DO_NOT_KNOW DO_NOT_KNOW}.</p>
     *
     * @return One of the values of the {@link Support Support} enumeration.
     */
    public @Support int isGaiaSupported () {
        return mIsGaiaSupported;
    }

    /**
     * <p>To know if the Upgrade protocol is supported by the connected device.</p>
     * <p>If there is no device connected or if the service does not know yet if a connected device supports that
     * protocol, this method returns {@link Support#DO_NOT_KNOW DO_NOT_KNOW.</p>
     *
     * @return One of the values of the {@link Support Support} enumeration.
     */
    public @Support int isUpgradeSupported () {
        return mIsUpgradeSupported;
    }

    /**
     * <p>To start the Upgrade process with the given file.</p>
     *
     * @param file
     *        The file to use to upgrade the Device.
     */
    @SuppressLint("NewApi")
    public void startUpgrade(File file) {
        //设置数据包直接的时间间隔,降低数据包丢失的可能性;CONNECTION_PRIORITY_HIGH:  30-40ms
        super.getBluetoothGatt().requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        mGaiaManager.startUpgrade(file);
        mProgressQueue.clear();
        mTransferStartTime = 0;
    }

    /**
     * <p>To get the bond state of the selected device.</p>
     *
     * @return Any of the bond states used by the {@link BluetoothDevice BluetoothDevice} class:
     *         {@link BluetoothDevice#BOND_BONDED BOND_BONDED}, {@link BluetoothDevice#BOND_BONDING BOND_BONDING} or
     *         {@link BluetoothDevice#BOND_NONE BOND_NONE}. If there is no device defined for this service, this method
     *         returns {@link BluetoothDevice#BOND_NONE BOND_NONE}.
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
    public @State int getConnectionState() {
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
     * @param enabled
     *          True to activate the maximum possible MTU size, false to set up the MTU size to default.
     *
     * @return True if the request could be queued.
     */
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
     * @param enabled
     *          True to set the transfer mode to RWCP, false otherwise.
     *
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


    // ====== IMPLEMENTED GAIA METHODS ===============================================================

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void onVMUpgradeDisconnected() {
    }

    @Override // GaiaUpgradeManager.GaiaManagerListener
    public void onResumePointChanged(@ResumePoints.Enum int point) {
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
        }
        else {
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
        }
        else {
            boolean done = requestWriteCharacteristic(mCharacteristicGaiaCommand, data);
            if (done && DEBUG) {
                Log.i(TAG, "Attempt to send GAIA packet on COMMAND characteristic: " + Utils.getStringFromBytes(data));
            }
            else if (!done) {
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
    public void askConfirmationFor(@UpgradeManager.ConfirmationType int type) {
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
        }

        else if (!done) {
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
     * @param message
     *         The message type to send.
     */
    @SuppressWarnings("UnusedReturnValue") // the return value is used for some implementations
    private boolean sendMessageToListener(@MessageType int message) {
        if (!mAppListeners.isEmpty()) {
            for (int i=0; i<mAppListeners.size(); i++) {
                mAppListeners.get(i).obtainMessage(message).sendToTarget();
            }
        }
        return !mAppListeners.isEmpty();
    }

    /**
     * <p>To inform the listener by sending it a message.</p>
     *
     * @param message
     *         The message type to send.
     * @param object
     *         Any object to send to the listener.
     */
    private boolean sendMessageToListener(@MessageType int message, Object object) {
        if (!mAppListeners.isEmpty()) {
            for (int i=0; i<mAppListeners.size(); i++) {
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
        }
        else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
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
                }
                else {
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
            }
            else if (uuid.equals(GAIA_CHARACTERISTIC_DATA_ENDPOINT_UUID)) {
                byte[] data = characteristic.getValue();
                if (data != null) {
                    mRWCPManager.onReceiveRWCPSegment(data);
                }
            }
            else {
                Log.i(TAG, "Received notification over characteristic: " + characteristic.getUuid());
            }
        }
    }

    // extends BLEService

    /**
     * 写特征描述,在请求描述符写入操作时调用此方法。
     * @param gatt
     *              The Bluetooth gatt which requested the descriptor write operation.
     * @param descriptor
     *              The Bluetooth Characteristic Descriptor for which a request has been made.
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
        }
        else if (characteristicUuid.equals(GAIA_CHARACTERISTIC_DATA_ENDPOINT_UUID)) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                boolean enabled = Arrays.equals(descriptor.getValue(),
                        BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                sendMessageToListener(MessageType.RWCP_ENABLED, enabled);
            }
            else {
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
     * @param gatt
     *              The Bluetooth gatt which requested the remote rssi.
     * @param mtu
     *              The mtu value chosen with the remote device.
     * @param status
     */
    @Override
    protected void onMTUChanged(BluetoothGatt gatt, int mtu, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.i(TAG, "MTU size had been updated to " + mtu);
            sendMessageToListener(MessageType.MTU_UPDATED, mtu);
        }
        else {
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
     *     <li>Device bonded: the process for notification registration starts.</li>
     *     <li>Device not bonded: the process to induce pairing if needed starts.</li>
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
        }
        else {
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
