package com.ly.bluetoothhelper.utils;

import android.util.Log;

import java.io.UnsupportedEncodingException;

/**
 * author: LingYun
 * email: SWD-yun.ling@jrdcom.com
 * date: 2019/9/16 10:21
 * version: 1.0
 * <p>
 * 字节  字符串等转换工具
 */
public class TransformUtils {

    /**
     * 字节数组转换为16进制字符串
     *
     * @param b 转换的字节数组
     */
    public static String bytesToHexString(byte[] b) {
        StringBuffer stringBuffer = new StringBuffer();
        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xFF);
            if (hex.length() < 2) {
                stringBuffer.append(0);
            }
            stringBuffer.append(hex.toUpperCase());
            if (i != b.length - 1) {
                stringBuffer.append(" ");
            }
        }
        return stringBuffer.toString();
    }

    public static String bytes2String(byte[] bytes) {
        String str = null;
        try {
            str = new String(bytes, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            Log.e("byte2String--", e.getMessage());
        }
        return str;
    }

    /**
     * 字符串转为16进制字节数组
     *
     * @param string 转换的字符串
     * @return
     */

    public static byte[] getHexBytes(String string) {
        int len = string.length() / 2;
        char[] chars = string.toCharArray();
        String[] hexStr = new String[len];
        byte[] bytes = new byte[len];
        for (int i = 0, j = 0; j < len; i += 2, j++) {
            hexStr[j] = "" + chars[i] + chars[i + 1];
            bytes[j] = (byte) Integer.parseInt(hexStr[j], 16);
        }
        return bytes;
    }


    /**
     * Hex字符串转byte
     *
     * @param inHex 待转换的Hex字符串
     * @return 转换后的byte
     */
    public static byte hexToByte(String inHex) {
        return (byte) Integer.parseInt(inHex, 16);
    }

    /**
     * hex字符串转byte数组
     *
     * @param inHex 待转换的Hex字符串
     * @return 转换后的byte数组结果
     */
    public static byte[] hexToByteArray(String inHex) {
        int hexlen = inHex.length();
        byte[] result;
        if (hexlen % 2 == 1) {
            //奇数
            hexlen++;
            result = new byte[(hexlen / 2)];
            inHex = "0" + inHex;
        } else {
            //偶数
            result = new byte[(hexlen / 2)];
        }
        int j = 0;
        for (int i = 0; i < hexlen; i += 2) {
            result[j] = hexToByte(inHex.substring(i, i + 2));
            j++;
        }
        return result;
    }

    /**
     * 十六进制字符串转字符串
     *
     * @param hexString
     * @return
     */
    public static String hexString2String(String hexString) {
        return bytes2String(hexToByteArray(hexString));
    }

    public static String string2HexString(String string) {
        char[] chars = "0123456789ABCDEF".toCharArray();
        StringBuilder sb = new StringBuilder("");
        byte[] bs = string.getBytes();
        int bit;
        for (int i = 0; i < bs.length; i++) {
            bit = (bs[i] & 0x0f0) >> 4;
            sb.append(chars[bit]);
            bit = bs[i] & 0x0f;
            sb.append(chars[bit]);
            // sb.append(' ');
        }
        return sb.toString().trim();
    }

    public static String hexInt2String(int hexInt) {
        String string = null;
        string = String.valueOf(Integer.parseInt(String.valueOf(hexInt), 16));
        return string;
    }

    public static String int2hex(int num) {
        return Integer.toHexString(num);
    }

    public static int hex2int(String hexString) {
        return Integer.parseInt(hexString, 16);
    }
}
