package com.kevin.Tool;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v7.app.AlertDialog;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

/**
 * Created by Kevin Hsu on 2017/10/17.
 */

public class BatteryTools
{
    private BatteryTools()
    {

    }

    static BatteryTools mgr = new BatteryTools();
    public static BatteryTools Instance()
    {
        return mgr;
    }
    Context con = null;

    boolean isInitOK = false;
    public void Init(Context con)
    {
        if(isInitOK)
            return;

        this.con = con;
        Intent intent = con.registerReceiver(batteryReceiver,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(null == intent)
        {
            HandShake.Instance().Log2File("registerReceiver(batteryReceiver) ... fail");
        }
        isInitOK = true;
    }

    public void Close()
    {
        if(null == con)
            return;
        con.unregisterReceiver(batteryReceiver);
        isInitOK = false;
    }

    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {

        @Override

        public void onReceive(Context context, Intent intent)
        {
            int level = intent.getIntExtra("level", 0); // now
            int scale = intent.getIntExtra("scale", 100); // total
            String sValue =   (level * 100) / scale + "%";

            HandShake.Instance().Log2File("BatteryState : " + sValue);
            UnityPlayer.UnitySendMessage("BatteryStateController","onBatteryState",sValue);
        }
    };
}
