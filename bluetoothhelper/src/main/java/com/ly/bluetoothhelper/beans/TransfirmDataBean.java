package com.ly.bluetoothhelper.beans;

import java.io.Serializable;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/10/15 13:44
 * version: 1.0
 *
 * 传输实体类
 */
public class TransfirmDataBean implements Serializable {
    int currentBin; //当前bin文件
    byte[] datas; //当前bin文件的数据流(字节流)
    boolean isBreak;//是否是断点续传

    public int getCurrentBin() {
        return currentBin;
    }

    public void setCurrentBin(int currentBin) {
        this.currentBin = currentBin;
    }

    public byte[] getDatas() {
        return datas;
    }

    public void setDatas(byte[] datas) {
        this.datas = datas;
    }

    public boolean isBreak() {
        return isBreak;
    }

    public void setBreak(boolean aBreak) {
        isBreak = aBreak;
    }
}
