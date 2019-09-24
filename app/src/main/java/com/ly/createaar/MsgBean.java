package com.ly.createaar;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/24 14:56
 * version: 1.0
 */
public class MsgBean {
    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }

    String msg;
    Object object;

    public MsgBean(){}
    public MsgBean(String msg, Object object) {
        this.msg = msg;
        this.object = object;
    }
}
