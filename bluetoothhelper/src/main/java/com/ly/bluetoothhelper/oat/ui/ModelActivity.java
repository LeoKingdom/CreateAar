/**************************************************************************************************
 * Copyright 2016 Qualcomm Technologies International, Ltd.                                       *
 **************************************************************************************************/

package com.ly.bluetoothhelper.oat.ui;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.ly.bluetoothhelper.oat.receiver.BluetoothStateReceiver;
import com.ly.bluetoothhelper.oat.service.OtauBleService;
import com.ly.bluetoothhelper.utils.Consts;

import java.lang.ref.WeakReference;

/**
 * <p>This class is the abstract activity to extend for each activity on this application which needs the service
 * {@link OtauBleService OtauBleService}.
 * and manages the {@link OtauBleService} for the user application.</p>
 */

public abstract class ModelActivity extends FragmentActivity
        implements BluetoothStateReceiver.BroadcastReceiverListener {

    /**
     * The tag to use for the logs.
     */
    private static final String TAG = "ModelActivity";

    /**
     * The Broadcast receiver we used to have information about the Bluetooth state on the device.
     */
    private BroadcastReceiver mBluetoothStateReceiver;

    /**
     * To know if we are using the application in the debug mode.
     */
    static final boolean DEBUG = true;
    /**
     * The BLE service to communicate with any device.
     */
    OtauBleService mService;
    /**
     * The instance of the Bluetooth adapter used to retrieve paired Bluetooth devices.
     */
    BluetoothAdapter mBtAdapter;
    /**
     * The service connection object to manage the service bond and unbound.
     */
    private final ServiceConnection mServiceConnection = new ActivityServiceConnection(this);
    /**
     * The handler used by the service to be linked to this activity.
     */
    private ActivityHandler mHandler;
    /**
     * To know if this activity is in the pause state.
     */
    private boolean mIsPaused;


    // ====== ACTIVITY METHODS =====================================================================

    // When the activity is created.
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.init();
    }

    // Callback activated after the user responds to the enable Bluetooth dialogue.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case Consts.ACTION_REQUEST_ENABLE_BLUETOOTH: {
                if (resultCode == RESULT_OK) {
                    onBluetoothEnabled();
                }
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // When the activity is resumed.
    @Override
    protected void onResume() {
        super.onResume();

        mIsPaused = false;
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        this.registerReceiver(mBluetoothStateReceiver, filter);

        if (mService != null) {
            initService();
        }
        else {
            Log.d(TAG, "BluetoothLEService not bound yet.");
        }

        checkEnableBt();
    }

    // When the activity is paused.
    @Override
    protected void onPause() {
        super.onPause();
        mIsPaused = true;
        unregisterReceiver(mBluetoothStateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mService != null) {
            mService.removeHandler(mHandler);
            mService = null;
            unbindService(mServiceConnection);
        }

    }


    // ====== PROTECTED METHODS ====================================================================

    /**
     * To start the Smart Watch service which allows to communicate with Bluetooth devices and to manage the GAIA protocol.
     */
    boolean startService() {
        Intent gattServiceIntent = new Intent(this, OtauBleService.class);
        return bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }

    /**
     * To display a toast inside this activity
     *
     * @param textID
     *              The ID of the text to display from the strings file.
     * @param duration
     *              How long the message should be displayed.
     */
    @SuppressWarnings("SameParameterValue")
    void displayToast(int textID, int duration) {
        Toast.makeText(this, textID, duration).show();
    }

    /**
     * <p>This method checks if the Bluetooth is enabled on the phone.</p>
     *
     * @return true is the Bluetooth is enabled, false otherwise.
     */
//    boolean isBluetoothEnabled() {
//        return mBtAdapter == null || !mBtAdapter.isEnabled();
//    }


    // ====== PUBLIC METHODS =======================================================================

    @Override
    public void onBluetoothDisabled() {
        checkEnableBt();
    }

    @Override
    public void onBluetoothEnabled() {
        if (mService == null) {
            startService();
        }
    }


    // ====== PRIVATE METHODS ======================================================================

    /**
     * <p>This method allows to init the bound service by defining this activity as a handler listening its messages.</p>
     */
    private void initService() {
        mService.addHandler(mHandler);
    }

    /**
     * To initialise objects used in this activity.
     */
    private void init() {
        // Bluetooth adapter
        mBtAdapter = ((BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();

        // the Handler to receive messages from the OtauBleService once attached
        mHandler = new ActivityHandler(this);

        // Register for broadcasts on BluetoothAdapter state change so that we can tell if it has been turned off.
        mBluetoothStateReceiver = new BluetoothStateReceiver(this);
    }

    /**
     * Display a dialog requesting Bluetooth to be enabled if it isn't already. Otherwise this method updates the
     * list to
     * the list view. The list view needs to be ready when this method is called.
     */
    private void checkEnableBt() {
//        if (isBluetoothEnabled()) {
//            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            startActivityForResult(enableBtIntent, Consts.ACTION_REQUEST_ENABLE_BLUETOOTH);
//        }
//        else {
//            onBluetoothEnabled();
//        }
    }


    // ====== ABSTRACT METHODS =====================================================================

    /**
     * <p>This method is called when the connected service is sending a message to the activity.</p>
     *
     * @param msg
     *          The message received from the service.
     */
    protected abstract void handleMessageFromService(Message msg);

    /**
     * <p>This method is called when the service has been bound to this activity.</p>
     */
    protected abstract void onServiceConnected();

    /**
     * <p>This method is called when the service has been unbound to this activity.</p>
     */
    protected abstract void onServiceDisconnected();


    // ====== INNER CLASS ==========================================================================

    /**
     * <p>This class is used to be informed of the connection state about the BLE service.</p>
     */
    private static class ActivityServiceConnection implements ServiceConnection {

        /**
         * The reference to this activity.
         */
        final WeakReference<ModelActivity> mActivity;

        /**
         * The constructor for this activity service connection.
         *
         * @param activity
         *            this activity.
         */
        public ActivityServiceConnection(ModelActivity activity) {
            super();
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            ModelActivity parentActivity = mActivity.get();
            parentActivity.mService = ((OtauBleService.LocalBinder) service).getService();
            parentActivity.initService();
            parentActivity.onServiceConnected(); // to inform subclass
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            ModelActivity parentActivity = mActivity.get();
            parentActivity.mService = null;
            parentActivity.onServiceDisconnected(); // to inform subclass
        }
    }

    /**
     * <p>This class allows to receive and manage messages from a {@link OtauBleService}.</p>
     */
    private static class ActivityHandler extends Handler {

        /**
         * The reference to this activity.
         */
        final WeakReference<ModelActivity> mReference;

        /**
         * The constructor for this activity handler.
         *
         * @param activity
         *            this activity.
         */
        public ActivityHandler(ModelActivity activity) {
            super();
            mReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            ModelActivity activity = mReference.get();
            if (!activity.mIsPaused) {
                activity.handleMessageFromService(msg);
            }
        }
    }
}
