package com.ly.bluetoothhelper.utils;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/24 13:42
 * version: 1.0
 */
public class ActionUtils {
    public static final String ACTION_DEVICE_SCAN = "ACTION_DEVICE_SCAN";
    public static final String ACTION_OTA_ORDER_S = "ACTION_OTA_ORDER";
    public static final int ACTION_OTA_ORDER_I = 0;
    public static final int ACTION_OTA_DATA_HEAD_I = 1; //数据帧帧头命令
    public static final int ACTION_OTA_DATA_DATA_I = 2; //数据帧
    public static final int ACTION_OTA_DATA_LOSE_I = 3; //数据帧丢包
    public static final int ACTION_OTA_DATA_RESEND_I = 4; //重发数据帧,丢包过多
    public static final String ACTION_OTA_DATA_S = "ACTION_OTA_DATA";
    public static final String ACTION_SCAN_SUCCESS_S = "ACTION_SCAN_SUCCESS";
    public static final int ACTION_SCAN_SUCCESS_I = 5;
    public static final String ACTION_SCAN_FAIL_S = "ACTION_SCAN_FAIL";
    public static final int ACTION_SCAN_FAIL_I = 6;
    public static final String ACTION_CONNECT_SUCCESS_S = "ACTION_CONNECT_SUCCESS";
    public static final int ACTION_CONNECT_SUCCESS_I = 7;
    public static final String ACTION_CONNECT_FAIL_S = "ACTION_CONNECT_FAIL";
    public static final int ACTION_CONNECT_FAIL_I = 8;

}
