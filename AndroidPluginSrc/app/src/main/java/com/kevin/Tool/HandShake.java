package com.kevin.Tool;

import android.os.Debug;
import android.util.Log;

import com.unity3d.player.UnityPlayer;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import generalplus.com.blespeechplugin.BleFramework;
import generalplus.com.blespeechplugin.BluetoothLeService;

/**
 * Created by Regulud on 2017/9/14.
 */

// 處理 Polling  , Packet quece
public class HandShake
{
    static HandShake sInst = new HandShake();

    public BluetoothLeService BLEService ;//= BleFramework.mBluetoothLeService;

    boolean isConnected = false;

    public synchronized void SetConnected(boolean flag)
    {
        isConnected = flag;
    }

    boolean isResponseMode = true;

    boolean isRunning = false;

    // 正在連線取得 getService 中
    boolean isGetServiceing = false;

    // 正在發送 pooling 封包中
    boolean isSendPooling = false;

    // 正在發送一般封包中?
    boolean isSendPacketing = false;

    // 回應 BLE 的 Command Packet 中
    boolean isResponsePacketing = false;

    // debug tag name
    String Tag = "HandShake";

    // 存放 要發送的封包
    List<BLEPacket> lsPacket = new ArrayList<BLEPacket>();

    // 最新的 packetIndex
    byte packetIndex = 0x01;

    // 目前正在傳送的 PacketIndex
    byte sendToBLE_PacketIndex = 0x00;

    // 目前 BLE  回應的 Packet Index
    byte responseFromBLEPacketIndex = 0x00;

    // pooling status
    byte poolingResponseStatus = 0x00;

    Date time = new Date();
    long preTime = time.getTime();

    // 連線中等候 getService 已過的時間
    long getServicePassTick = 0;

    // 連線後取得service是否成功的檢查, 當逾時此時間 , 則要斷線 , 重新連線
    long getServiceTimeOutTick = 30 * 1000; // 30 秒

    // 發送封包後 , 巳經過的時間
    long sendPacketPassTick = 0;

    // 傳送封包逾時檢查, 當逾時此時間 , 則重新發送封包
    long sendPacketTimeOutTick = 30 * 1000; // 30 秒

    //   間隔時間發送一次 Pooling Packet
    long sendPoolingIntervalTick = 300; // 0.3 秒


    public static HandShake Instance() {
        return sInst;
    }

    private HandShake() {

    }

    Thread t = new Thread(new Runnable() {
        @Override
        public void run() {
            while (isRunning) {
                try {
                    Thread.sleep(50);

                    if(!isConnected)
                        continue;;

                    // 檢查是否連線取得 service -  time out
                    if (isGetServiceing && !CheckGetServiceTimeOut()) // 時間未到回 false　--->  測試是否發生在連線後要service失敗,若失敗則要重新連線
                        continue;

                    // 檢查是否 packet  time out -> 重發
                    if (isSendPacketing && !CheckSendPacketTimeOut())  // 時間未到回 false
                    {
                        continue;
                    }

                    // 檢查 Pooling 是否逾時 , 沒有逾時繼續
                    if(isSendPooling && !CheckPoolingTimeOut())
                    {
                        continue;
                    }

                    // 是否有 packet 待送
                    if (SendPacket()) //  沒有發送一般封包 , 則往下發 pooling
                        continue;

                    // 是否發送 polling ?
                    SendPoolingPacket();


                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    });

    public synchronized void Start() {
        if(isRunning)
            return;

        isRunning = true;
        t.start();
    }

    public synchronized void Close() {
        isRunning = false;
    }

    public synchronized void PostPacket(byte[] data) {
        BLEPacket p = new BLECmdPacket(data);
        MakeNewPacketIndex();
        lsPacket.add(p);
    }

    public synchronized byte MakeNewPacketIndex() {
        int index = packetIndex + 1;
        if (index >= 0xff)
            index = 1;
        return (byte) index;
    }

    public synchronized void OnWritePacket(boolean isSuccess) {

        Log.d(Tag,"OnWritePacket( ) ... ");

        if (!isResponseMode)  //  No Response Mode , 發送成功是在 OnRecv 判斷同一個 Packet Index 才表示 發送成功
        {
            if (isSendPooling) // 改至 rec 時才變更狀態
            {
                //isSendPooling = false;
                return;
            }

            if(isResponsePacketing) // 回應 BLE CMD 封包
            {
                isResponsePacketing = false;
                return;
            }


            return;
        }

        // 以下為 Response Mode , 發送成功是在 OnWritePacket 判斷 回傳成功即表示成功.
        // 當收到系統通知成功寫入 , 將 flag 改為 沒有在發送封包中
        isSendPacketing = false;

        // 寫入成功 , 則刪除最近一個 Packet
        if (lsPacket.size() > 0)
            lsPacket.remove(0);
    }

    // 收到封包
    public synchronized void OnRecvPacket(boolean isSuccess, byte[] data) {
        Log.d("HandShake", "OnRecvPacket ( )");
        if (!isSuccess) {
            Log.d(Tag, "OnRecvPacket ( ) .... is Success = fail");
            return;
        }

        if (isResponseMode) {
            return;
        }

        // 下面為 No Response Mode
        //  No Response Mode , 發送成功是在 OnRecv 判斷同一個 Packet Index 才表示 發送成功
        // 檢查 Packet Inde 是否一樣

        if (data == null || data.length < 2) {
            Log.d(Tag, "OnRecvPacket ( ) ....  data fail !");
            return;
        }

        byte rspIndex = data[0];
        if (isSendPooling && rspIndex == 0x00) // pooling 的封包
        {
            isSendPooling = false; // 下一個 interval 再發送 pooling
            byte gbxStatus = data[1];
            if (gbxStatus != poolingResponseStatus) {
                poolingResponseStatus = gbxStatus;
                //  當 GB 狀態變更時 , 通知 Unity
                if (poolingResponseStatus % 2 == 1) {
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleGameBoxState", "Connected");
                } else {
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleGameBoxState", "DisConnected");
                }
            }
        }
        else if (isSendPacketing && rspIndex == sendToBLE_PacketIndex) // 回應封包的 index 一樣,表示封包已經發送成功
        {
            isSendPacketing = false;
        } else  // 遇到 Packet Index 不一樣
        {
            //
            if (isSendPacketing) // 收到 BLE Response Packet :  當 APP ---> BLE  時 , 收到 Recv ,  為  BLE 回應 App Packet Index
            {
                Log.d(Tag, "OnRecvPacket ( ) ....  isSendPacketing .... Index no Match  , APP = " + sendToBLE_PacketIndex + " , BLE = " + rspIndex);
            }
            else               //  收到 BLE Command Packet :  APP 要回應
            {
                //  nothing ,  方向不一樣 , Packet Index 應該是不一樣的
            }
        }


    }

    public synchronized void OnGetServiceStart(boolean isSuccess) {
        isGetServiceing = true;
    }

    public synchronized void OnGetServiceFinished(boolean isSuccess) {
        isGetServiceing = false;
    }

    public synchronized boolean SendPacket() {
        if (BleFramework.mBluetoothLeService == null)
            return false;

        if(isSendPooling)
            return false;

        if(isResponsePacketing)
            return false;

        if (lsPacket.size() == 0)
            return false;

        BLEPacket p = lsPacket.get(0);

        //  No Response 使用
        byte[] data = p.getNoPacketIndexData();

        isSendPacketing = true;
        BleFramework.mBluetoothLeService.WriteData(data);

        return true;
    }

    // 這個只會送一次
    public synchronized boolean SendResponsePacket(byte BLECmdPacketIndex)
    {
        if (BleFramework.mBluetoothLeService == null)
            return false;

        byte[] data = new byte[1];
        data[0] = BLECmdPacketIndex;

        isResponsePacketing = true;
        BleFramework.mBluetoothLeService.WriteData(data);

        return true;
    }

    private synchronized void SendPoolingPacket() {

        if (BleFramework.mBluetoothLeService == null)
            return ;

        if(isSendPooling)
            return;

        isSendPooling = true;

        BLEPoollingPacket p = new BLEPoollingPacket();

        BleFramework.mBluetoothLeService.WriteData(p.getData());


    }


    // 檢查連線後取得 service 是否逾時 ,  如果 逾時 -->  斷線,重連
    private synchronized boolean CheckGetServiceTimeOut() {
        // 是否逾時
        getServicePassTick = time.getTime() - preTime;

        if (getServicePassTick > 30 * 1000) // 等候 30 秒
        {
            BleFramework.mBluetoothLeService.disconnect();
            return true; // 已經逾時
        }

        return false;
    }

    private synchronized boolean CheckSendPacketTimeOut() {
        sendPacketPassTick = time.getTime() - preTime;

        if (sendPacketPassTick > 1 * 1000) // 等候 1 秒 沒收到回應 , 那麼就發 最近一個封包
        {
            // 重送
            Log.d(Tag, "CheckSendPacketTimeOut ( ) , .... timoe out  , next  to re send packet ");
            SendPacket();
            return false;
        }

        return true;
    }

    private synchronized boolean CheckPoolingTimeOut() {
        sendPacketPassTick = time.getTime() - preTime;

        if (sendPacketPassTick > sendPoolingIntervalTick) //  超過最近一次發送的封包 ,  若沒有 cmd 包要發送 , 則發出  Pooling  封包
        {
            // 重送
            Log.d(Tag, "CheckSendPacketTimeOut ( ) , .... timoe out  , next  to re send packet ");
            SendPacket();
            return false;
        }
        return true;
    }




    public class BLEPacket {
        byte[] bs_data;

        public byte[] getData() {
            return bs_data;
        }

        public byte[] getNoPacketIndexData() {
            byte[] bb = new byte[bs_data.length - 1];
            System.arraycopy(bb, 0, bs_data, 1, bb.length);
            return bb;
        }
    }

    public class BLECmdPacket extends BLEPacket {
        public BLECmdPacket() {
        }

        public BLECmdPacket(byte[] data) {
            int cnt = data.length;
            if (cnt > 19)
                cnt = 19;
            bs_data = new byte[cnt + 1];
            System.arraycopy(bs_data, 1, data, 0, cnt);
            bs_data[0] = packetIndex;
        }

        byte[] bs_data;
    }

    public class BLEPoollingPacket extends BLEPacket
    {
        public BLEPoollingPacket()
        {
            this.bs_data = new byte[1];
            this.bs_data[0] = 0x00;
        }
    }

}
