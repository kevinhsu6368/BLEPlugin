package com.kevin.Tool;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiInfo;


import com.unity3d.player.UnityPlayer;

import static android.content.Context.WIFI_SERVICE;

/**
 * Created by Regulud on 2017/9/29.
 */

public class NetTools
{
    static Context context = null;
    private NetTools()
    {

    }

    static boolean isCheckNetworkState = false;
    static Thread t_checkNetworkState = null;
    static Runnable run_CheckNetworkState = new Runnable() {
        @Override
        public void run()
        {
            while (isCheckNetworkState)
            {
                // to do.
                ConnectivityManager CM = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo ni =  CM.getActiveNetworkInfo();
                if(ni!=null && ni.isConnected())
                {
                    //Log.d("NetState","On Line");
                    onNetState(true);
                }
                else
                {
                    //Log.d("NetState","Off Line");
                    onNetState(false);
                }


                // sleep
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        }
    };

    private static boolean currentConnectState = false;
    private static void onNetState(boolean isConnected)
    {
        if(currentConnectState != isConnected)
        {
            currentConnectState = isConnected;
            Log.d("NetState","Network state ======= > " + Boolean.toString(isConnected));

            String netState = isConnected ? "On Line" : "Off Line";
            UnityPlayer.UnitySendMessage("NetworkStateController","onNetState",netState);
        }

    }



    public static void Start_CheckNetworkState(Context c)
    {
        context = c;

        if(t_checkNetworkState!=null)
            return;;

        isCheckNetworkState = true;
        t_checkNetworkState = new Thread(run_CheckNetworkState);
        t_checkNetworkState.start();
    }

    public static void Stop_CheckNetworkState()
    {
        isCheckNetworkState = false;
    }

    public static String GetWifiIP()
    {
        WifiManager wifi_service = (WifiManager)context.getSystemService(WIFI_SERVICE);
        WifiInfo wifiInfo = wifi_service.getConnectionInfo();
        int ipAddress = wifiInfo.getIpAddress();
        String ip = String.format("%d.%d.%d.%d",(ipAddress & 0xff),(ipAddress >> 8 & 0xff),(ipAddress >> 16 & 0xff),(ipAddress >> 24 & 0xff));
        return ip;
    }
}
