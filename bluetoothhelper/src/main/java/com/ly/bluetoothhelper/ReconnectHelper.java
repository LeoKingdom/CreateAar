package com.ly.bluetoothhelper;

import android.bluetooth.BluetoothDevice;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Message;
import android.util.ArrayMap;
import android.util.Log;

import com.clj.fastble.BleManager;
import com.clj.fastble.data.BleDevice;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/6/3 17:16
 * version: 1.0
 * 重连
 */
public class ReconnectHelper {
    BluetoothHelper bluetoothHelper;
    BleManager bleManager;
    private static ReconnectHelper reconnectHelper;
    private CountDownTimer timer;
    private List<CountDownTimer> timerList = new ArrayList<>();

    public BleDevice getReConnFailDevice() {
        return reConnFailDevice;
    }

    private BleDevice reConnFailDevice;
    public Map<String, CountDownTimer> getTimerMap() {
        return timerMap;
    }

    public void setTimerMap(Map<String, CountDownTimer> timerMap) {
        this.timerMap = timerMap;
    }

    private Map<String, CountDownTimer> timerMap = new HashMap<>();

    public ReconnHandler getReConnHandler() {
        return reConnHandler;
    }

    private ReconnHandler reConnHandler;

    public static ReconnectHelper getInstance() {
        if (reconnectHelper == null) {
            synchronized (ReconnectHelper.class) {
                if (reconnectHelper == null) {
                    reconnectHelper = new ReconnectHelper();
                }
            }
        }
        return reconnectHelper;
    }

    private ReconnectHelper() {
        bluetoothHelper = BluetoothHelper.getBluetoothHelper();
        bleManager = bluetoothHelper.getBleManager();
        reConnHandler = new ReconnHandler(this);
    }

    public void reconnListening() {
        Map<String, BleDevice> disconnDevice = bluetoothHelper.getDisConnDeviceMap();
        Set<Map.Entry<String, BleDevice>> keys = disconnDevice.entrySet();
//        Log.e("disSize===",disconnDevice.size()+"");
        if (disconnDevice.size() > 0) {
            for (final Map.Entry<String, BleDevice> entry : keys) {
                timer = new CountDownTimer(600000, 10000) {
                    @Override
                    public void onTick(long millisUntilFinished) {
//                        Log.e("reconn----",entry.getKey()+"/"+entry.getValue().getName());
                        bluetoothHelper.scanAndConnect(false, null, entry.getValue().getName());
                    }

                    @Override
                    public void onFinish() {
                        for (Map.Entry<String, CountDownTimer> timer : getTimerMap().entrySet()) {
                            if (timer.getKey().equals(entry.getKey())){
                                reConnFailDevice=entry.getValue();
                            }
                            timer.getValue().cancel();
                        }
                    }
                }.start();
                timerMap.put(entry.getValue().getName(), timer);
            }
        }

    }

    public void cancelListen(String key) {
        Map<String, CountDownTimer> timerMap1 = getTimerMap();
        if (timerMap1.size() > 0) {
            timerMap1.get(key).cancel();
            timerMap1.remove(key);
            setTimerMap(timerMap1);
        }
    }

    public static class ReconnHandler extends Handler {
        private WeakReference<ReconnectHelper> reconnectHelperWeakReference;

        public ReconnHandler(ReconnectHelper reconnectHelper) {
            reconnectHelperWeakReference = new WeakReference<>(reconnectHelper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            ReconnectHelper reconn = reconnectHelperWeakReference.get();
            switch (msg.what) {
                case 0:
                    reconn.reconnListening();
                    break;
                case 1:
                    String key = (String) msg.obj;
                    if (key != null) {
                        reconn.cancelListen(key);
                    }
                    break;
            }
        }
    }

}
