/**************************************************************************************************
 * Copyright 2017 Qualcomm Technologies International, Ltd.                                       *
 **************************************************************************************************/

package com.ly.bluetoothhelper.oat.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.ly.bluetoothhelper.R;
import com.ly.bluetoothhelper.oat.rwcp.RWCP;
import com.ly.bluetoothhelper.oat.service.BLEService;
import com.ly.bluetoothhelper.oat.service.OtauBleService;
import com.ly.bluetoothhelper.oat.upgrade.UpgradeError;
import com.ly.bluetoothhelper.oat.upgrade.UpgradeManager;
import com.ly.bluetoothhelper.oat.upgrade.UploadProgress;
import com.ly.bluetoothhelper.oat.upgrade.codes.ResumePoints;
import com.ly.bluetoothhelper.oat.upgrade.codes.ReturnCodes;
import com.ly.bluetoothhelper.utils.Consts;
import com.ly.bluetoothhelper.utils.Utils;
import com.ly.bluetoothhelper.widget.VMUpgradeDialog;

import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * <p>This activity is the activity to control the BLE device selected on the Connection Activity.</p>
 */
public class MainActivity extends ModelActivity implements View.OnClickListener, VMUpgradeDialog.UpgradeDialogListener {

    // ====== CONSTS ===============================================================================

    /**
     * For the debug mode, the tag to display for logs.
     */
    private final static String TAG = "MainActivity";


    // ====== PRIVATE FIELDS =======================================================================

    /**
     * The dialog to display during the upgrade.
     */
    private VMUpgradeDialog mUpgradeDialog;
    private File mFile = null;
    /**
     * The dialog which is used during the device reconnection.
     */
    private AlertDialog mDialogReconnection;
    /**
     * To know if the user has already change the MTU size to know if the Warning dialog needs to be displayed.
     */
    private boolean hasUserChangedMTU = false;


    // ====== PUBLIC METHODS =======================================================================

    // override from View.OnClickListener
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
        }
    }

    /**
     * @see VMUpgradeDialog.UpgradeDialogListener#abortUpgrade()
     * This implementation does not check if the VMUpgradeDialog has been dismissed or is still displayed.
     */
    @Override // override from VMUpgradeDialog.UpgradeDialogListener
    public void abortUpgrade() {
        mService.abortUpgrade();
    }

    // override from VMUpgradeDialog.UpgradeDialogListener
    @Override
    public @ResumePoints.Enum int getResumePoint() {
        return (mService != null) ? mService.getResumePoint() : ResumePoints.Enum.DATA_TRANSFER;
    }

    @Override
    public void onBluetoothEnabled() {
        super.onBluetoothEnabled();
        if (mService != null && mService.isUpdating()) {
            mService.reconnectToDevice();
        }
    }


    // ====== ACTIVITY METHODS =====================================================================

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        setContentView(R.layout.activity_main);
        init();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mService != null) mService.disconnectDevice();
        showUpgradeDialog(false);
    }



    // ====== PROTECTED METHODS ====================================================================

    /**
     * OtauBleService 发送的handler消息,在其702-735行两个方法
     * @param msg
     */
    @Override
    protected void handleMessageFromService(Message msg) {
        StringBuilder handleMessage = new StringBuilder("Handle a message from BLE service: ");

        switch (msg.what) {
            case OtauBleService.MessageType.CONNECTION_STATE_HAS_CHANGED:
                @BLEService.State int connectionState = (int) msg.obj;
                onConnectionStateChanged(connectionState);
                String stateLabel = connectionState == BLEService.State.CONNECTED ? "CONNECTED"
                        : connectionState == BLEService.State.CONNECTING ? "CONNECTING"
                        : connectionState == BLEService.State.DISCONNECTING ? "DISCONNECTING"
                        : connectionState == BLEService.State.DISCONNECTED ? "DISCONNECTED"
                        : "UNKNOWN";
                handleMessage.append("CONNECTION_STATE_HAS_CHANGED: ").append(stateLabel);
                break;

            case OtauBleService.MessageType.GAIA_SUPPORT:
                @OtauBleService.Support int gaiaSupport = (int) msg.obj;
                boolean gaiaIsSupported = gaiaSupport == OtauBleService.Support.SUPPORTED;
                if (!gaiaIsSupported) {
//                    Toast.makeText(this, getString(R.string.toast_gaia_not_supported), Toast.LENGTH_SHORT).show();
                }
                handleMessage.append("GAIA_SUPPORT: ").append(gaiaIsSupported);
                break;

            case OtauBleService.MessageType.UPGRADE_SUPPORT:
                @OtauBleService.Support int upgradeSupport = (int) msg.obj;
                boolean upgradeIsSupported = upgradeSupport == OtauBleService.Support.SUPPORTED;
                if (!upgradeIsSupported) {
//                    Toast.makeText(this, getString(R.string.toast_upgrade_not_supported), Toast.LENGTH_SHORT).show();
                }
                handleMessage.append("UPGRADE_SUPPORT: ").append(upgradeIsSupported);
                break;

            case OtauBleService.MessageType.UPGRADE_FINISHED:
                displayUpgradeComplete();
                handleMessage.append("UPGRADE_FINISHED");
                break;

            case OtauBleService.MessageType.UPGRADE_REQUEST_CONFIRMATION:
                @UpgradeManager.ConfirmationType int confirmation = (int) msg.obj;
                askForConfirmation(confirmation);
                handleMessage.append("UPGRADE_REQUEST_CONFIRMATION: type is ").append(confirmation);
                break;

            case OtauBleService.MessageType.UPGRADE_STEP_HAS_CHANGED:
                @ResumePoints.Enum int step = (int) msg.obj;
                mUpgradeDialog.updateStep(step);
                handleMessage.append("UPGRADE_STEP_HAS_CHANGED: ").append(ResumePoints.getLabel(step));
                break;

            case OtauBleService.MessageType.UPGRADE_ERROR:
                UpgradeError error = (UpgradeError) msg.obj;
                manageError(error);
                handleMessage.append("UPGRADE_ERROR");
                break;

            case OtauBleService.MessageType.UPGRADE_UPLOAD_PROGRESS:
                UploadProgress progress = (UploadProgress) msg.obj;
                mUpgradeDialog.displayTransferProgress(progress);
                handleMessage.append("UPGRADE_UPLOAD_PROGRESS");
                break;

            case OtauBleService.MessageType.DEVICE_BOND_STATE_HAS_CHANGED:
                int state = (int) msg.obj;
                handleMessage.append("DEVICE_BOND_STATE_HAS_CHANGED");
                break;

            case OtauBleService.MessageType.RWCP_SUPPORTED:
                boolean supported = (boolean) msg.obj;
                handleMessage.append("RWCP_SUPPORTED: ").append(supported);
                break;

            case OtauBleService.MessageType.RWCP_ENABLED:
                boolean enabled = (boolean) msg.obj;
                handleMessage.append("RWCP_ENABLED: ").append(enabled);
                break;

            case OtauBleService.MessageType.TRANSFER_FAILED:
                // The transport layer has failed to transmit bytes to the device using RWCP
                if (mUpgradeDialog.isAdded()) {
                    mUpgradeDialog.displayError(getString(R.string.dialog_upgrade_transfer_failed));
                }
                handleMessage.append("TRANSFER_FAILED");
                break;

            case OtauBleService.MessageType.MTU_SUPPORTED:
                boolean mtuSupported = (boolean) msg.obj;
                handleMessage.append("MTU_SUPPORTED: ").append(mtuSupported);
                break;

            case OtauBleService.MessageType.MTU_UPDATED:
                int mtu = (int) msg.obj;
                handleMessage.append("MTU_UPDATED: size=").append(mtu);
                break;

            default:
                handleMessage.append("UNKNOWN MESSAGE: ").append(msg);
                break;
        }

        if (DEBUG && msg.what != OtauBleService.MessageType.UPGRADE_UPLOAD_PROGRESS) {
            // The upgrade upload messages are not displayed to avoid too many logs.
            Log.d(TAG, handleMessage.toString());
        }
    }

    @Override
    protected void onServiceConnected() {
        initDeviceInformation();
    }

    @Override
    protected void onServiceDisconnected() {
        Log.d(TAG, "Service disconnected");
    }


    // ====== PRIVATE METHODS ======================================================================

    /**
     * To initialise all components used in this activity.
     */
    private void init() {
        mUpgradeDialog = VMUpgradeDialog.newInstance(this);
        initReconnectionDialog();
    }


    /**
     * To initialise the device information on the screen.
     */
    @SuppressLint("MissingPermission")
    private void initDeviceInformation() {
        // retrieving data from the device
        String deviceName = "";
        String deviceAddress = "";

        BluetoothDevice device = mService != null ? mService.getDevice() : null;

        if (device != null) {
            deviceName = device.getName();
            deviceAddress = device.getAddress();

            // update connection state
            @BLEService.State int state = mService == null ? BLEService.State.DISCONNECTED
                    : mService.getConnectionState();
        }

    }

    /**
     * This method is used to initialize all the dialogs which will be used in this application.
     */
    private void initReconnectionDialog() {
        // build the dialog to show a progress bar when we try to reconnect.
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(MainActivity.this);
        dialogBuilder.setTitle(getString(R.string.alert_reconnection_title));

        LayoutInflater inflater = getLayoutInflater();
        @SuppressLint("InflateParams") // the root can be null as "attachToRoot" is false
        View dialogLayout = inflater.inflate(R.layout.dialog_progress_bar, null, false);
        dialogBuilder.setView(dialogLayout);
        dialogBuilder.setCancelable(false);
        mDialogReconnection = dialogBuilder.create();

    }


    /**
     * <p>This method is called when the user taps on a button in order to connect with the selected Bluetooth
     * device.</p>
     *
     * @param view
     *          The view with which the user interacts.
     */
    private void onButtonConnectClicked (View view) {
        view.setEnabled(false);
        if (mService != null) {
            mService.connectToDevice(mService.getDevice());
        }
        else {
            if (!startService()) {
                view.setEnabled(true);
//                displayToast(R.string.toast_connection_not_possible, Toast.LENGTH_SHORT);
            }
        }
    }

    /**
     * <p>This method allows the Upgrade process to be able to ask the user any confirmation to be able to carry on
     * the upgrade process.</p>
     *
     * @param confirmation
     *        The type of confirmation which has to be asked.
     */
    private void askForConfirmation(@UpgradeManager.ConfirmationType final int confirmation) {
        switch (confirmation) {
            case UpgradeManager.ConfirmationType.COMMIT:
                displayConfirmationDialog(confirmation, R.string.alert_upgrade_commit_title, R.string.alert_upgrade_commit_message);
                break;
            case UpgradeManager.ConfirmationType.IN_PROGRESS:
                // no obligation to ask for confirmation as the commit confirmation will happen next
                mService.sendConfirmation(confirmation, true);
                break;
            case UpgradeManager.ConfirmationType.TRANSFER_COMPLETE:
                displayConfirmationDialog(confirmation, R.string.alert_upgrade_transfer_complete_title,
                        R.string.alert_upgrade_transfer_complete_message);
                break;
            case UpgradeManager.ConfirmationType.BATTERY_LOW_ON_DEVICE:
                displayConfirmationDialog(confirmation, R.string.alert_upgrade_low_battery_title,
                        R.string.alert_upgrade_low_battery_message);
                break;
            case UpgradeManager.ConfirmationType.WARNING_FILE_IS_DIFFERENT:
                displayConfirmationDialog(confirmation, R.string.alert_upgrade_sync_id_different_title,
                        R.string.alert_upgrade_sync_id_different_message);
                break;
        }
    }

    /**
     * <p>To display the confirmation dialog to allow the user to pick a choice when the Upgrade process needs to
     * know if it should carry on.</p>
     *
     * @param confirmation
     *        The type of confirmation which has been asked by the Upgrade process.
     * @param title
     *        The tile of the dialog.
     * @param message
     *        The message which should be displayed in the dialog.
     */
    private void displayConfirmationDialog (@UpgradeManager.ConfirmationType final int confirmation, int title, int
            message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setTitle(title)
                .setPositiveButton(R.string.button_continue, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mService.sendConfirmation(confirmation, true);
                    }
                })
                .setNegativeButton(R.string.button_abort, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mService.sendConfirmation(confirmation, false);
                        showUpgradeDialog(false);
                    }
                });
        builder.show();
    }

    /**
     * <p>When an error occurs during the upgrade, this method allows to display an error information to the user
     * depending on the error type contained on the {@link UpgradeError UpgradeError} parameter.</p>
     *
     * @param error
     *              The information related to the error which occurred during the upgrade process.
     */
    private void manageError(UpgradeError error) {
        switch (error.getError()) {
            case UpgradeError.ErrorTypes.AN_UPGRADE_IS_ALREADY_PROCESSING:
                // nothing should happen as there is already an upgrade processing.
                // in case it's not already displayed, we display the Upgrade dialog
                showUpgradeDialog(true);
                break;

            case UpgradeError.ErrorTypes.ERROR_BOARD_NOT_READY:
                // display error message + "please try again later"
                if (mUpgradeDialog.isAdded()) {
                    mUpgradeDialog.displayError(getString(R.string.dialog_upgrade_error_board_not_ready));
                }
                break;

            case UpgradeError.ErrorTypes.EXCEPTION:
                // display that an error has occurred?
                if (mUpgradeDialog.isAdded()) {
                    mUpgradeDialog.displayError(getString(R.string.dialog_upgrade_error_exception));
                }
                break;

            case UpgradeError.ErrorTypes.NO_FILE:
                displayFileError();
                break;

            case UpgradeError.ErrorTypes.RECEIVED_ERROR_FROM_BOARD:
                if (mUpgradeDialog.isAdded()) {
                    mUpgradeDialog.displayError(ReturnCodes.getReturnCodesMessage(error.getReturnCode()),
                            Utils.getIntToHexadecimal(error.getReturnCode()));
                }
                break;

            case UpgradeError.ErrorTypes.WRONG_DATA_PARAMETER:
                if (mUpgradeDialog.isAdded()) {
                    mUpgradeDialog.displayError(getString(R.string.dialog_upgrade_error_protocol_exception));
                }
                break;
        }
    }


    /**
     * <p>Displays an alert dialog with an "error file" message.</p>
     */
    private void displayFileError() {
        showUpgradeDialog(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_file_error_message)
                .setTitle(R.string.alert_file_error_title)
                .setPositiveButton(R.string.button_ok, null);
        builder.show();
    }

    /**
     * To display an alert when the upgrade process successfully completed.
     */
    private void displayUpgradeComplete() {
        showUpgradeDialog(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.alert_upgrade_complete_message).setTitle(R.string.alert_upgrade_complete_title)
                .setPositiveButton(R.string.button_ok, null);
        builder.show();
    }

    private void showReconnectionDialog (boolean display) {
        if (display) {
            if (!mDialogReconnection.isShowing()) mDialogReconnection.show();
        }
        else {
            if (mDialogReconnection.isShowing()) mDialogReconnection.dismiss();
        }
    }

    /**
     * To display the upgrade dialog when the upgrade starts.
     */
    private void showUpgradeDialog(boolean show) {
        if (show && !mUpgradeDialog.isAdded()) {
            mUpgradeDialog.show(getSupportFragmentManager(), getResources().getString(R.string.dialog_upgrade_title));
        }
        else
            if (!show && mUpgradeDialog.isAdded()) {
                mUpgradeDialog.dismiss();
            }
    }

    /**
     * <p>This method is called when the service informs the activity that the device connection state has changed.</p>
     *
     * @param state
     *        The new connection state sent by the service.
     */
    private void onConnectionStateChanged(@BLEService.State int state) {
        showUpgradingDialogs(state);
    }

    /**
     * <p>If an update is processing, this method displays the reconnection dialog or the update dialog depending on
     * the given state. If no update is processing, this method will hide all the updating dialogs.</p>
     *
     * @param state
     *
     *          dialog and hide the reconnection dialog. Any other state will display the reconnection one and
     *          hide the upgrade one.
     */
    private void showUpgradingDialogs(@BLEService.State int state) {
        if (mService != null && mService.isUpdating()) {
            if (state == BLEService.State.CONNECTED) {
                showReconnectionDialog(false);
                showUpgradeDialog(true);
            }
            else {
                showUpgradeDialog(false);
                showReconnectionDialog(true);
            }
        }
        else {
            showUpgradeDialog(false);
            showReconnectionDialog(false);
        }
    }

    /**
     * This method allows to start the upgrade process as asked by the user.
     */
    private void startUpgrade() {
        if (mFile != null) {
            mService.startUpgrade(mFile);
            showUpgradeDialog(true);
        }
        else {
            displayFileError();
        }
    }
}
