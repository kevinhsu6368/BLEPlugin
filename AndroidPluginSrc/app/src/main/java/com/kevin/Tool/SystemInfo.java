package com.kevin.Tool;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * Created by rita on 2017/10/22.
 */

public class SystemInfo
{
    public static void Init(Context c)
    {
        con = c;
    }
    public static Context con;

    public static String GetPhoneModle()
    {
        if(con == null)
            return "No Init Context at System Info !!!";

        return (Build.MANUFACTURER +" ( "+Build.MODEL+" ) ");
    }

    public static String ToString()
    {
        if(con==null)
            return "No Init Context at System Info !!!";

        String str = "";
        str += "CPU  =  " + Build.CPU_ABI +"\r\n";
        str += "DEVICE  =  " + Build.DEVICE +"\r\n";

        str += "MANUFACTURER  =  " + Build.MANUFACTURER +"\r\n";
        str += "MODEL  =  " + Build.MODEL +"\r\n";
        str += "PRODUCT  =  " + Build.PRODUCT +"\r\n";

        str += "RELEASE  =  " + Build.VERSION.RELEASE +"\r\n";

        PackageManager manager = con.getPackageManager();
        String AppVersion = "";
        try {
            PackageInfo info = con.getPackageManager().getPackageInfo(con.getPackageName(),0);
            AppVersion = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        str += "APP Version  =  " + AppVersion +"\r\n";
        str += "\r\n";

        return str;
    }
}
