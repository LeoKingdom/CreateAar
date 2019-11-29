package com.ly.bluetoothhelper.beans;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/11/29 15:17
 * version: 1.0
 * 蓝牙数据传输model
 */
public class BleDataBean {
    private byte frame_head = -85; //帧头
    private byte total_length_1; //总长度1
    private byte total_length_2;
    private byte module_id;
    private byte event_id;
    private byte total_frame;
    private byte current_frame;
    private byte frame_end;
}
