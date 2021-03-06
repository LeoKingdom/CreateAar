/**************************************************************************************************
 * Copyright 2016 Qualcomm Technologies International, Ltd.                                       *
 **************************************************************************************************/

package com.ly.bluetoothhelper.oat.gaia.request;

import android.support.annotation.IntDef;


import com.ly.bluetoothhelper.oat.gaia.packets.GaiaPacket;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * The data structure to define a GAIA request.
 */
public class GaiaRequest {

    /**
     * All types of GAIA requests which can be sent to a device.
     */
    @IntDef(flag = true, value = { Type.SINGLE_REQUEST, Type.ACKNOWLEDGEMENT })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Type {
        int SINGLE_REQUEST = 1;
        int ACKNOWLEDGEMENT = 2;
    }

    /**
     * The type of the request.
     */
    @Type public final int type;

    /**
     * If this request is about a characteristic, the Bluetooth characteristic for this request.
     */
    public GaiaPacket packet;

    /**
     * To build a new object of the type request.
     */
    public GaiaRequest(@Type int type) {
        this.type = type;
    }

}
