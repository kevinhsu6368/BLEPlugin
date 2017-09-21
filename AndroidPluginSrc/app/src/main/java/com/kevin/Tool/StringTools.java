package com.kevin.Tool;

/**
 * Created by rita on 2017/9/17.
 */
public class StringTools
{
    public static String byteToHexString(byte[] data)
    {
        String str = "";
        for (byte b : data)
        {
            str += String.format("%02X",b);
        }
        return str;
    }
    public static String byteToHexString(byte[] data,String intervalString)
    {
        String str = "";
        for (byte b : data)
        {
            str += String.format("%02X",b);
            if(null != intervalString  && !intervalString.isEmpty())
                str += intervalString;
        }
        return str;
    }

    public static byte [] HexToBytes(String hex)
    {
        hex = hex.replaceAll("\\s+","");
        byte [] bb = new byte[hex.length()/2];
        for(int i=0;i<bb.length;i++)
            bb[i] = (byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        return bb;
    }




    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
