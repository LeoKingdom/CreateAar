package com.ly.bluetoothhelper.callbacks;

import android.support.annotation.IntDef;
import android.support.annotation.StringDef;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/11/27 11:26
 * version: 1.0
 */

@IntDef(flag = true, value = {BleStatus.BLE_DISABLE, BleStatus.BLE_GPS_FORCE_OPEN, BleStatus.BLE_LOCATION_DISABLE, BleStatus.BLE_NOT_SURPPORT
        , BleStatus.BLE_PERMISSION_GRANT, BleStatus.BLE_REMOTE_DISABLE, BleStatus.BLE_SCAN_AND_CONNECT, BleStatus.BLE_TRACKER_IN,
        BleStatus.BLE_TRACKER_NOT_FOUND, BleStatus.BLE_TRACKER_OUT, BleStatus.BLE_VIRTUAL_LEASH_OPEN_DIALOG, BleStatus.BLE_CONNECT_FAIL_I,
        BleStatus.BLE_CONNECT_SUCCESS_I, BleStatus.BLE_DISCONNECT_I, BleStatus.BLE_NOT_FOUND_I, BleStatus.BLE_SCAN_FINISH_I, BleStatus.BLE_SCAN_START_I,
BleStatus.BISINESS_TRACKER,BleStatus.BISINESS_TRACKER_ESIM,BleStatus.BISINESS_TRACKER_LOCATION,BleStatus.BISINESS_TRACKER_UPGRADE})
@StringDef(value = {BleStatus.BLE_CONNECT_FAIL, BleStatus.BLE_CONNECT_SUCCESS, BleStatus.BLE_DISCONNECT, BleStatus.BLE_NOT_FOUND, BleStatus.BLE_SCAN_FINISH, BleStatus.BLE_SCAN_START})
public @interface BleStatus {
    int BLE_DISABLE = 0x2001; //手机端蓝牙未打开
    int BLE_REMOTE_DISABLE = 0x2002; //设备端蓝牙未打开
    int BLE_NOT_SURPPORT = 0x2003; //手机端不支持BLE
    int BLE_LOCATION_DISABLE = 0x2004; //手机端定位信息不可用,这里强制直接打开
    int BLE_TRACKER_NOT_FOUND = 0x2005; //设备未找到
    int BLE_TRACKER_OUT = 0x2006; //tracker走出范围
    int BLE_VIRTUAL_LEASH_OPEN_DIALOG = 0x2007; //打开virtual leash弹出dialog
    int BLE_SCAN_AND_CONNECT = 0x2008; //扫描连接蓝牙
    int BLE_PERMISSION_GRANT = 0x2009; //授予蓝牙权限
    int BLE_GPS_FORCE_OPEN = 0x2010; //强制打开gps
    int BLE_TRACKER_IN = 0x2011; //tracker 进圈
    int BLE_SCAN_START_I = 0x1001;
    int BLE_SCAN_FINISH_I = 0x1002;
    int BLE_CONNECT_SUCCESS_I = 0x1003;
    int BLE_CONNECT_FAIL_I = 0x1004;
    int BLE_DISCONNECT_I = 0x1005;
    int BLE_NOT_FOUND_I = 0x1006;
    int BISINESS_TRACKER = 0x1007;
    int BISINESS_TRACKER_LOCATION = 0x1008;
    int BISINESS_TRACKER_UPGRADE = 0x1009;
    int BISINESS_TRACKER_ESIM = 0x1010;
    String BLE_SCAN_START = "ble.scan.start";
    String BLE_SCAN_FINISH = "ble.scan.finish";
    String BLE_CONNECT_SUCCESS = "ble.conn.success";
    String BLE_CONNECT_FAIL = "ble.conn.fail";
    String BLE_DISCONNECT = "ble.disconnect";
    String BLE_NOT_FOUND = "ble.not.found";
}
