package com.kevin.Tool;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

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
            UnityPlayer.UnitySendMessage("NetworkStatus","onNetState",netState);
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

}
