package com.ly.bluetoothhelper.utils;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/19 9:23
 * version: 1.0
 *
 * IOT通讯指令集,内部使用
 */
public class OrderSetUtils {
    public static final byte[] ORDER_HEADER={-85}; //头部指令
//    public static final byte[] ORDER_OAD={-85,0,7,0,0,32,2,1,1}; //进入ota升级指令,最后两个字节表示升级的bin文件(有可能有多个),total和current
    public static final byte[] ORDER_LOCATION={-85,0,0,0,0,16,5}; //获取定位指令
    public static final byte[] ORDER_LOCATION_SUCCESS={-85,0,9,0,0,16,5,0}; //成功获取定位指令
    public static final byte[] ORDER_LOCATION_FAIL={-85,0,1,0,0,16,5,1}; //获取定位失败指令
    public static final byte[] ORDER_VERSION={-85,0,0,0,0,32,1}; //获取设备版本号指令
    public static final byte[] ORDER_OAD={-85,0,7,0,0,32,2}; //进入ota升级指令,最后两个字节表示升级的bin文件(有可能有多个),total和current
    public static final byte[] ORDER_OAD_DATA_SEND={-85,0,0,0,0,32,3}; //准备发送ota升级包指令
    /***************************ESIM对应指令******************************/
    public static final byte[] ESIM_EID={-85,0,0,0,0,48,1}; //获取EID指令
    public static final byte[] ESIM_IMEI={-85,0,0,0,0,48,2}; //获取IMEI指令
    public static final byte[] ESIM_PROFILE_DOWNLOAD={-85,0,0,0,0,48,3}; //下载Profile指令  app->ble
    public static final byte[] ESIM_PROFILE_DOWNLOAD_URL_RESP={-85,0,0,0,0,48,4}; //下载Profile 的URL回复指令  ble->app
    public static final byte[] ESIM_PROFILE_DOWNLOAD_POST_RESP={-85,0,0,0,0,48,5}; //下载Profile 的post请求参数回复指令  ble->app
    public static final byte[] ESIM_ACTIVE_NOTICE_RESP={-85,0,0,0,0,48,6}; //是否激活通知回复指令  ble->app
    public static final byte[] ESIM_INFO={-85,0,0,0,0,48,7}; //获取ESIM信息指令
    public static final byte[] ESIM_ACTIVE={-85,0,0,0,0,48,8}; //激活ESIM指令
    public static final byte[] ESIM_CANCEL={-85,0,0,0,0,48,9}; //去活ESIM指令
    public static final byte[] ESIM_PROFILE_DELETE={-85,0,0,0,0,48,10}; //删除Profile指令
    public static final byte[] ESIM_SET_NICKNAME={-85,0,0,0,0,48,12}; //设置ESIM昵称指令
    public static final byte[] ESIM_SERVER_DOMAIN={-85,0,0,0,0,48,13}; //获取服务器地址指令
    public static final byte[] ESIM_SM_DP={-85,0,0,0,0,48,14}; //设置SM-DP地址指令
    public static final byte[] ESIM_SMDS={-85,0,0,0,0,48,15}; //设置SMDS地址指令
//    public static final byte[] ORDER_NOTICE_DELETE={-85,0,0,0,0,48,13}; //删除通知指令
//    public static final byte[] ORDER_ESIM_RESTORE={-85,0,0,0,0,48,14}; //恢复ESIM出厂设置指令

}
