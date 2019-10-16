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
    public static final byte[] ORDER_OAD={-85,0,7,0,0,32,2}; //进入ota升级指令,最后两个字节表示升级的bin文件(有可能有多个),total和current
    public static final byte[] ORDER_OAD_DATA_SEND={-85,0,0,0,0,32,3}; //准备发送ota升级包指令
    public static final byte[] ORDER_LOCATION={-85,0,0,0,0,16,5}; //获取定位指令
    public static final byte[] ORDER_VERSION={-85,0,0,0,0,32,1}; //获取设备版本号指令
    public static final byte[] ORDER_EID={-85,0,0,0,0,48,1}; //获取EID指令
    public static final byte[] ORDER_IMEI={-85,0,0,0,0,48,2}; //获取IMEI指令
    public static final byte[] ORDER_TRANSFIRM_PROFILE={-85,0,0,0,0,48,3}; //传输Profile指令
    public static final byte[] ORDER_ESIM={-85,0,0,0,0,48,4}; //获取ESIM信息指令
    public static final byte[] ORDER_ESIM_ACTIVE={-85,0,0,0,0,48,5}; //激活ESIM指令
    public static final byte[] ORDER_ESIM_CANCEL={-85,0,0,0,0,48,6}; //去活ESIM指令
    public static final byte[] ORDER_PROFILE_DELETE={-85,0,0,0,0,48,7}; //删除Profile指令
    public static final byte[] ORDER_ESIM_NICKNAME={-85,0,0,0,0,48,8}; //设置ESIM昵称指令
    public static final byte[] ORDER_SERVER_DOMAIN={-85,0,0,0,0,48,9}; //获取服务器地址指令
    public static final byte[] ORDER_SM_DP={-85,0,0,0,0,48,10}; //设置SM-DP地址指令
    public static final byte[] ORDER_SMDS={-85,0,0,0,0,48,11}; //设置SMDS地址指令
    public static final byte[] ORDER_NOTICE_LIST={-85,0,0,0,0,48,12}; //获取通知列表指令
    public static final byte[] ORDER_NOTICE_DELETE={-85,0,0,0,0,48,13}; //删除通知指令
    public static final byte[] ORDER_ESIM_RESTORE={-85,0,0,0,0,48,14}; //恢复ESIM出厂设置指令

}
