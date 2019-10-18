package com.ly.createaar;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.ly.bluetoothhelper.beans.MsgBean;
import com.ly.bluetoothhelper.callbacks.DataCallback;
import com.ly.bluetoothhelper.callbacks.NotifyCallback;
import com.ly.bluetoothhelper.callbacks.ProgressCallback;
import com.ly.bluetoothhelper.callbacks.WriteCallback;
import com.ly.bluetoothhelper.helper.BluetoothHelper;
import com.ly.bluetoothhelper.service.OTAUpgradeService;
import com.ly.bluetoothhelper.utils.ActionUtils;
import com.ly.bluetoothhelper.utils.DataPacketUtils;
import com.ly.bluetoothhelper.utils.OrderSetUtils;
import com.ly.bluetoothhelper.utils.SharePreferenceUtils;
import com.ly.bluetoothhelper.utils.TransformUtils;
import com.ly.bluetoothhelper.widget.LoadingWidget;
import com.ly.bluetoothhelper.widget.ProgressDialogWidget;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class MainActivity extends Activity {

    private VirtualLeashHelper bluetoothHelper;
    private BluetoothHelper bluetoothHelper1;
    private BleDevice bleDevice;
    private EditText editText;
    private TextView scanAndConnTv;
    private ProgressDialogWidget progressDialogWidget;
    private LoadingWidget loadingWidget;
    private TextView textView;
    private boolean ota_ready = false;
    private boolean ota_order_send = false;
    private Queue<byte[]> packetQueue = new LinkedList<>();
    private int totalFrame; //总帧数
    int currentFrame = 1; //当前帧
    private int currentBin;//当前bin文件
    //文件字节流
    private byte[] initialTotalBytes = null;
    private byte[] currentFrameBytes = null;
    private int CURRENT_WHAT = -1;
    private OTAUpgradeService otaUpgradeService;
    private int currentPacket = 0;
    private int cPacket = 0;
    private boolean isBin = false;
    private int totalBin = 0;//总的bin文件数
    private DecimalFormat decimalFormat;
    private List<String> fileNameList=new ArrayList<>();
    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            OTAUpgradeService.LocalBinder binder = (OTAUpgradeService.LocalBinder) service;
            otaUpgradeService = binder.getService();
            setCallback();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }
    };

    private void setCallback() {
        otaUpgradeService.setNotifyCallback(new NotifyCallback() {
            @Override
            public void charactoristicChange(int action,byte[] backBytes) {

            }

            @Override
            public void deviceReconn() {
                //重新连接
                progressDialogWidget.getCurrentPacket().setText("连接成功");
                progressDialogWidget.showCloseBtnWithText("继续");
                progressDialogWidget.getCloseBtn().setOnClickListener(v -> {
                    otaUpgradeService.getHandler().sendEmptyMessageDelayed(ActionUtils.ACTION_OTA_RECONNECT_SEND, 3000);
                });

            }

            @Override
            public void success() {

            }

            @Override
            public void fail(Object o) {

            }

            @Override
            public void noDevice() {

            }
        });

        otaUpgradeService.setWriteCallback(new WriteCallback() {
            @Override
            public void writeSuccess(int actionType, int current, int total, byte[] justWrite) {
                if (actionType==ActionUtils.ACTION_OTA_ORDER_I){

                }else if (actionType==ActionUtils.ACTION_OTA_DATA_HEAD_I){

                }else {

                }
            }

            @Override
            public void success() {

            }

            @Override
            public void fail(Object o) {

            }

            @Override
            public void noDevice() {

            }
        });

        otaUpgradeService.setProgressCallback(new ProgressCallback() {
            @Override
            public void setMax(int max) {
                progressDialogWidget.setProgressMax(currentPacket);
                progressDialogWidget.setProgressNumTvText(0 + "%");
                progressDialogWidget.show();
            }

            @Override
            public void setProgress(float percent,int current,int currentFrame,int currentBin) {
                progressDialogWidget.getCurrentPacket().setText("当前传送: 第"+currentBin+"个文件,第" + currentFrame + "帧,第" + current + "包");
                progressDialogWidget.getProgressBar().setProgress((int) percent);
                progressDialogWidget.getProgressNumTv().setText(decimalFormat.format(percent) + "%");
            }

            @Override
            public void success() {

            }

            @Override
            public void fail(Object o) {

            }

            @Override
            public void noDevice() {

            }
        });

        otaUpgradeService.setDataCallback(new DataCallback() {
            @Override
            public void nextFrame(int currentFrame, int totalFrame) {

            }

            @Override
            public void reSend() {

            }

            @Override
            public void done() {
                //传输完成
                progressDialogWidget.setProgressNumTvText("传输完成!");
                progressDialogWidget.showCloseBtnWithText("Done");
            }

            @Override
            public void checkOutTime() {
                loadingWidget.hide();
                toast("data loading fail,try again");
            }

            @Override
            public void binChecking() {
                loadingWidget.setLoadingText("data loading...");
//                loadingWidget.show();
            }

            @Override
            public void binCheckDone(boolean isBin) {
                loadingWidget.hide();
                if (!isBin){
                    toast("It is not a bin file ");
                }else {

                }
            }

            @Override
            public void fileNotFound(String msg) {

            }
        });
    }

    private byte[] loseList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        EventBus.getDefault().register(this);
        editText = findViewById(R.id.edittext);
        scanAndConnTv = findViewById(R.id.scan_and_conn);
        progressDialogWidget = findViewById(R.id.progress_dialog);
        loadingWidget = findViewById(R.id.main_loading_widget);
        textView = findViewById(R.id.write_tv);
        TextView txtTagProgress = progressDialogWidget.getProgressNumTv();
        decimalFormat = new DecimalFormat("#.00");
//        combinePacket();
        checkLocation();
    }

    // todo
    private boolean checkLocation() {
        if (Build.VERSION.SDK_INT >= 23) {
            int permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION,
                        },
                        0x0010
                );
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    // todo
    private void toast(String msg) {
        Toast.makeText(this, "" + msg, Toast.LENGTH_LONG).show();
    }

    // todo
    public void clear(View view) {
        editText.setText("");
    }

    // --------------------------------- 2019/10/18 ---------------------------------==
//    // toat
//    public void startUpgrade(File file) {
//        //设置数据包直接的时间间隔,降低数据包丢失的可能性;CONNECTION_PRIORITY_HIGH:  30-40ms
//        super.getBluetoothGatt().requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
//        mGaiaManager.startUpgrade(file);
//        mProgressQueue.clear();
//        mTransferStartTime = 0;
//    }
//
//    // toat
//// --------------------------------- 2019/10/18 ---------------------------------==


    // todo
    private byte[] combinePacket() {
        //合并后的字节数组
        byte[] lastBytes = null;

//        assetManager.
        try {
            AssetManager assetManager=getAssets();
            String[] assetsList = assetManager.list("");
            for (String name:assetsList){
                if (name.endsWith(".patch")){
                    fileNameList.add(name);
                }
                Log.e("name---",name);
            }

            InputStream inputStream = getResources().getAssets().open("ap");
            lastBytes = DataPacketUtils.combinePacket(inputStream);
            InputStream inputStream1 = getResources().getAssets().open("ap");
            initialTotalBytes = TransformUtils.streamToByte(inputStream1);
            totalFrame = (initialTotalBytes.length / 1024) % 4 != 0 ? ((initialTotalBytes.length / 1024 / 4) + 1) : (initialTotalBytes.length / 1024 / 4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return lastBytes;
    }

    // todo
    public void write(View view) {
        loadingWidget.setLoadingText("device connecting...");
        loadingWidget.show();
        Intent intent = new Intent(this, OTAUpgradeService.class);
        intent.setAction(ActionUtils.ACTION_DEVICE_SCAN);
        intent.putExtra("mac_address", "01:02:04:05:06:09");
//        intent.putExtra("mac_address", "01:02:04:05:A6:14");
        intent.putExtra("dataByte", initialTotalBytes);
        bindService(intent,connection, Context.BIND_AUTO_CREATE);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void deverse(MsgBean msgBean) {
        if (msgBean != null) {
            Object o = msgBean.getObject();
            String msg = msgBean.getMsg();
            if (o instanceof BleDevice) {
                BleDevice device = (BleDevice) o;
                if (msg.equals(ActionUtils.ACTION_CONNECT_SUCCESS_S)) {
                    connectSuccess(device);
                } else if (msg.equals(ActionUtils.ACTION_CONNECT_FAIL_S)) {
                    connetFail(device);
                } else if (msg.equals(ActionUtils.ACTION_SCAN_SUCCESS_S)) {
                    scanSuccess(device);
                } else if (msg.equals(ActionUtils.ACTION_DISCONNECT_S)) {
                    disconnect(device);
                }
            } else if (o == null) {
                if (msg.equals(ActionUtils.ACTION_SCAN_FAIL_S)) {
                    scanFail();
                }
            }
        }

    }

    // todo
    private void disconnect(BleDevice device) {
        this.bleDevice = null;
        loadingWidget.hide();
        progressDialogWidget.setProgressNumTvText("");
        progressDialogWidget.getCurrentPacket().setText("设备已断开");
        progressDialogWidget.showCloseBtnWithText("重试");
        progressDialogWidget.getCloseBtn().setOnClickListener((v -> {
            otaUpgradeService.getHandler().sendEmptyMessageDelayed(ActionUtils.ACTION_DEVICE_RECONNECT,300);
        }));
        toast("设备已断开");
        SharePreferenceUtils.setValue(this, "data-frame", cPacket + "," + currentFrame + "," + totalFrame + "," + currentBin);
    }
    // todo
    public void scanFail() {
        toast("未发现蓝牙设备,请重启设备再试");
        loadingWidget.hide();
    }


    public void scanSuccess(BleDevice bleDevice) {

    }


    public void connetFail(BleDevice b) {
        toast("连接失败");
    }


    public void connectSuccess(BleDevice bleDevice) {
        this.bleDevice = bleDevice;
        Log.e("connect-success---",bleDevice.getMac());
        scanAndConnTv.setText("扫描与连接(已连接)");
    }

}
