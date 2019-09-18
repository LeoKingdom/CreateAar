package com.ly.createaar;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.ly.bluetoothhelper.helper.BluetoothHelper;
import com.ly.bluetoothhelper.utils.TransformUtils;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private VirtualLeashHelper bluetoothHelper;
    private BluetoothHelper bluetoothHelper1;
    private BleDevice bleDevice;
    private EditText editText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText=findViewById(R.id.edittext);
        bluetoothHelper = VirtualLeashHelper.getInstance().init(getApplication());
        bluetoothHelper1=new BluetoothHelper(getApplication());
        bluetoothHelper1.initUuid(null,
                "00005500-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5",
                "00005501-d102-11e1-9b23-00025b00a5a5");
//        myHandler = new MyHandler(this);
        checkLocation();
    }

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

    private void toast(String msg){
        Toast.makeText(this,"show---:"+msg,Toast.LENGTH_LONG).show();
    }

    public void read(View view){

        bluetoothHelper1.read(bleDevice, new BluetoothHelper.ReadListener() {
            @Override
            public void onReadSuccess(byte[] data) {
                toast(TransformUtils.bytesToHexString(data));
            }

            @Override
            public void onReadFailure(BleException exception) {

            }
        });
    }

    public void write(View view){
        String msg=editText.getText().toString().trim();
//        if (TextUtils.isEmpty(msg)) return;
        byte[] datas= TransformUtils.getHexBytes(msg);
        bluetoothHelper1.write(bleDevice,new byte[]{-85,0,10},new BluetoothHelper.WriteListener(){
            @Override
            public void onWriteSuccess(int current, int total, byte[] justWrite) {
                toast("current----"+current+"/total---"+total+"/each---"+TransformUtils.bytesToHexString(justWrite));
            }

            @Override
            public void onWriteFailure(BleException exception) {

            }
        });
    }

    public void notify(View view){
        toast(TransformUtils.string2HexString("0x20")+"");
        Log.e("string2HexString---",TransformUtils.hex2int("AB")+"");
        Log.e("hexString2String---",TransformUtils.hexString2String("30783230"));
        Log.e("hexString2bytes---", Arrays.toString(TransformUtils.hexToByteArray("Ab000A")));
        Log.e("bytes2hexString---", TransformUtils.bytesToHexString(new byte[]{-85,0,10}));
//        byte[] bytes=new byte[]{'0xAB',};
    }

    public void scan(View view) {
        bluetoothHelper.openVirtualLeash(true, "01:02:04:05:06:07", "");
        bluetoothHelper.openReconnectListen();
        bluetoothHelper.setScanStartListener(() -> {
            Log.e("scanStart---", "run");
        });
        bluetoothHelper.setScanFinishListener((bleDevice) -> {
            if (bleDevice != null) {
                Log.e("scanFinish---", bleDevice.getName() + "");
            }
        });

        bluetoothHelper.setConnectSuccessListener((bleDevice, gatt) -> {
            if (bleDevice != null) {
                this.bleDevice = bleDevice;
                toast(bleDevice.getName()+"/"+bleDevice.getMac());
                Log.e("connectSuccess---", bleDevice.getName() + "");
            }
        });

        bluetoothHelper.setConnectFailListener((bleDevice, description) -> {
            if (bleDevice != null) {
                Log.e("connectFail---", bleDevice.getName() + "");
            }
        });

        bluetoothHelper.setReconnectSuccessListener((bleDevice) -> {
            if (bleDevice != null) {
                Log.e("reconnectSuccess---", bleDevice.getName() + "");
            }
        });

        bluetoothHelper.setReconnectFailListener((bleDevice -> {
            if (bleDevice != null) {
                Log.e("reconnectFail---", bleDevice.getName() + "");
            }else {
                Log.e("reconnectFail---",  "ensure  your device and tracker's bluetooth is open ,and tracker is around you");
            }
        }));
        bluetoothHelper.setDeviceSelfDisableListener(() -> {
            Log.e("bluetooth---", "is close");
        });
        bluetoothHelper.setDeviceAwayListener((bleDevice) -> {
            if (bleDevice != null) {
                Log.e("tracker---", bleDevice.getName() + "is run away");
            }
        });
    }


}
