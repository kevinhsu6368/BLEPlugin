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

    boolean isSimulateMode = false;
    public void SetSimulate(boolean isSimulate)
    {
        isSimulateMode = isSimulate;
    }

    boolean isConnected = false;

    public synchronized void SetConnected(boolean flag)
    {
        isConnected = flag;
    }

    boolean isResponseMode = false;
    public synchronized void SetResponseMode(boolean ResponseMode)
    {
        isResponseMode = ResponseMode;
        if(isResponseMode)
             Log.d(Tag,"Set  .... is Response Mode");
        else
            Log.d(Tag,"Set  .... no Response Mode");
    }

    public boolean GetIsResponseMode()
    {
        return isResponseMode;
    }

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
    public String Tag = "HandShake";

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
    long preTime = System.currentTimeMillis();

    // 連線中等候 getService 已過的時間
    long getServicePassTick = 0;

    // 連線後取得service是否成功的檢查, 當逾時此時間 , 則要斷線 , 重新連線
    long getServiceTimeOutTick = 30 * 1000; // 30 秒
    public synchronized void SetGetServiceTimeOutTick(int ms)
    {
        getServiceTimeOutTick = ms;
    }

    // 發送封包後 , 巳經過的時間
    long sendPacketPassTick = 0;

    // 傳送封包逾時檢查, 當逾時此時間 , 則重新發送封包
    long sendPacketTimeOutTick = 5000; // 0.3 秒
    public synchronized void SetSendPacketTimeOutTick(int ms)
    {
        sendPacketTimeOutTick = ms;
    }

    //   間隔時間發送一次 Pooling Packet
    long sendPoolingIntervalTick = 5000; //  0.3 秒
    public synchronized void SetSendPoolingIntervalTick(int ms)
    {
        sendPoolingIntervalTick = ms;
    }

    public static HandShake Instance() {
        return sInst;
    }

    private HandShake() {

    }



    public synchronized void Simulator_Recv_BLE_Pooling(boolean bGBX_ON)
    {
        if(!isSendPooling)
            return;

        isSendPooling = false;
        ResetTimeOut();
        if(bGBX_ON)
        {
            Log.d(Tag,"is SendPooling response  , state changed as :  Connected");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleGameBoxState", "Connected");
        }
        else
        {
            Log.d(Tag,"is SendPooling response  , state changed as :  DisConnected");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleGameBoxState", "DisConnected");
        }
    }


    public synchronized void Simulator_Recv_BLE_ResponseCmdIndex(boolean bPacketIndexCurrent)
        {
            if(!isSendPacketing)
                return;

            isSendPacketing = false;
            ResetTimeOut();
            lsPacket.remove(0);
            if(bPacketIndexCurrent)
                Log.d(Tag, "BLE_Response_Cmd_Index , Index error");
            Log.d(Tag,"Send Packet .. BLE response Finished");
    }

    public synchronized  void Simulator_Recv_BLE_SendCmdPacket()
    {
        isResponsePacketing = true;
        isSendPooling = false;
        isSendPacketing = false;
        ResetTimeOut();
        Log.d(Tag, "Simulator_Recv_BLE_SendCmdPacket");
        isResponsePacketing = false;
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
                    if (isGetServiceing) // 時間未到回 false　--->  測試是否發生在連線後要service失敗,若失敗則要重新連線
                    {
                        if(CheckGetServiceTimeOut())
                        {
                            isConnected = false;
                            isGetServiceing = false;
                            BleFramework.mBluetoothLeService.disconnect();
                        }
                        continue;
                    }

                    // 檢查是否 packet  time out -> 重發
                    if ( isSendPacketing )  // 時間未到回 false
                    {
                        if(CheckSendPacketTimeOut())
                        {
                            // 重送
                            Log.d(Tag, "CheckSendPacketTimeOut ( ) , .... timoe out  , next  to re send packet ");
                            SendPacket();
                        }
                        continue;
                    }

                    if ( isResponsePacketing ) //  不做逾時檢查及重送
                    {
                        continue;
                    }

                    // 檢查 Pooling 是否逾時 , 沒有逾時繼續 , 逾時則會重發 Pooling Packet
                    if(isSendPooling )
                    {
                        if (CheckPoolingTimeOut())
                        {
                            // 重送
                            Log.d(Tag, "CheckPoolingTimeOut ( ) .. part - 1 , .... timoe out  , next  to re send packet ");
                            SendPoolingPacket();
                        }
                        continue;
                    }

                    // 在空間後  , 若有封包要發送 , 再發送
                    // 是否有 packet 待送
                    if (SendPacket()) //  沒有發送一般封包 , 則往下發 pooling
                        continue;

                    // 是否發送 polling ?
                    if(!isSendPooling && CheckPoolingTimeOut())
                    {
                        Log.d(Tag, "CheckPoolingTimeOut ( ) .. part - 2 , .... timoe out  , next  to  send packet ");
                        SendPoolingPacket();
                    }

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

        Log.d(Tag,"OnWritePacket( ) ... done");

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

        // 寫入成功 , 則刪除最近一個 Packet
        if(!isGetServiceing)
            ResetTimeOut();

        if (isSendPacketing)
        {
            isSendPacketing = false;
            if(lsPacket.size() > 0)
                lsPacket.remove(0);
        }
        else if(isResponsePacketing)
        {
            isResponsePacketing = false;
            if(lsPacket.size() > 0)
                lsPacket.remove(0);
        }
        else if(isSendPooling)
        {
            isSendPooling = false;
        }

    }

    //  當有收到 BLE - response cmd , response pool , cmd 時皆要新時間
    public void ResetTimeOut()
    {
        preTime = System.currentTimeMillis();
    }

    // 收到封包
    public synchronized void OnRecvPacket(boolean isSuccess, byte[] data) {

        Log.d(Tag, "OnRecvPacket ( ) .. ");
        Log.d(Tag, "Recv Data = " + StringTools.bytesToHex(data));


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

        if(!isGetServiceing)
            ResetTimeOut();

        byte rspIndex = data[0];
        if (isSendPooling ) // pooling 的封包
        {
            isSendPooling = false; // 下一個 interval 再發送 pooling
            if(rspIndex == 0x00)
            {
                byte gbxStatus = data[1];
                if (gbxStatus != poolingResponseStatus)
                {
                    poolingResponseStatus = gbxStatus;

                    //  當 GB 狀態變更時 , 通知 Unity
                    if (poolingResponseStatus % 2 == 1)
                    {
                        Log.d(Tag,"is SendPooling response  , state changed as :  Connected");
                        UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleGameBoxState", "Connected");
                    }
                    else
                    {
                        Log.d(Tag,"is SendPooling response  , state changed as :  DisConnected");
                        UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleGameBoxState", "DisConnected");
                    }
                }
                Log.d(Tag,"isSendPooling  -  response , .... Success");
            }
            else
            {
                Log.d(Tag,"isSendPooling  -  response , .... but  response index != 00");
            }
        }
        else if (isSendPacketing)
        {
            isSendPacketing = false;
            if(rspIndex == sendToBLE_PacketIndex) // 回應封包的 index 一樣,表示封包已經發送成功
            {
                Log.d(Tag,"isSendPacketing  -  response , .... Success");
                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnAPP_WritePacket_to_Ble_Success", "isSendPacketing");
            }
            else
            {
                Log.d(Tag, "isSendPacketing  -  response , .... but  response index error");
            }

        }
        else if(isResponsePacketing) // 收到 發送至 BLE 的 Response Packet , 發送完成
        {
            isResponsePacketing = false;
            Log.d(Tag,"isResponsePacketing  -  response , .... Success");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnAPP_WritePacket_to_Ble_Success", "isResponsePacketing");
        }
        else   // 收到 BLE 的主動封包 , 回應 Packet Index 的封包
        {
            if(rspIndex!=0x00) {
                SendResponsePacket(rspIndex);
                Log.d(Tag,"Send Response Packet , .... Start");
                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnAPP_WritePacket_to_Ble_Success", "StartSendResponsePacket");

            }
        }
        /*
        else  // 遇到 Packet Index 不一樣
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
        */

    }

    public synchronized void OnGetServiceStart() {
        isGetServiceing = true;
    }

    public synchronized void OnGetServiceFinished(boolean isSuccess) {
        isGetServiceing = false;
    }

    // 發送 Command 封包
    public synchronized boolean SendPacket() {
        if (BleFramework.mBluetoothLeService == null)
            return false;

        if(!isConnected)
            return false;

        if(isGetServiceing)
            return false;

        if(isSendPooling)
            return false;

        if(isResponsePacketing)
            return false;

        if (lsPacket.size() == 0)
            return false;

        BLEPacket p = lsPacket.get(0);

        //  No Response 使用

        byte[] data = isResponseMode ? p.getNoPacketIndexData() : p.getData();

        isSendPacketing = true;
        preTime = System.currentTimeMillis();
        BleFramework.mBluetoothLeService.WriteData(data);
        Log.d(Tag, "SendPacket ( ) , .... WriteData ");
        Log.d(Tag,"WriteData =  " + StringTools.bytesToHex(data));
        return true;
    }

    // 這個只會送一次
    public synchronized boolean SendResponsePacket(byte BLECmdPacketIndex)
    {
        if (BleFramework.mBluetoothLeService == null)
            return false;

        if(!isConnected)
            return false;

        byte[] data = new byte[1];
        data[0] = BLECmdPacketIndex;

        isResponsePacketing = true;
        preTime = System.currentTimeMillis();
        BleFramework.mBluetoothLeService.WriteData(data);
        Log.d(Tag, "SendResponsePacket ( ) , .... WriteData ");
        Log.d(Tag,"WriteData =  " + StringTools.bytesToHex(data));
        return true;
    }

    private synchronized void SendPoolingPacket() {

        if (BleFramework.mBluetoothLeService == null)
            return ;

        if(!isConnected)
            return ;

//        if(isSendPooling)
//            return;

        isSendPooling = true;
        preTime = System.currentTimeMillis();

        BLEPoollingPacket p = new BLEPoollingPacket();
        byte [] data = p.getData();
        BleFramework.mBluetoothLeService.WriteData(data);
        Log.d(Tag, "SendPoolingPacket ( ) , .... WriteData ");
        Log.d(Tag,"WriteData =  " + StringTools.bytesToHex(data));
    }


    // 檢查連線後取得 service 是否逾時 ,  如果 逾時 -->  斷線,重連
    private synchronized boolean CheckGetServiceTimeOut() {
        // 是否逾時
        getServicePassTick = System.currentTimeMillis() -  preTime;

        if (getServicePassTick > 30 * 1000) // 等候 30 秒
        {
            Log.d(Tag, "CheckGetServiceTimeOut ( ) , .... timoe out  , next  to disconnect ");

            return true; // 已經逾時
        }

        return false;
    }

    private synchronized boolean CheckSendPacketTimeOut() {
        sendPacketPassTick = System.currentTimeMillis() - preTime;

        if (sendPacketPassTick > sendPacketTimeOutTick)
        {
            return true;
        }

        return false;
    }

    private synchronized boolean CheckPoolingTimeOut() {
        sendPacketPassTick = System.currentTimeMillis() - preTime;

        if (sendPacketPassTick > sendPoolingIntervalTick) //  超過最近一次發送的封包 ,  若沒有 cmd 包要發送 , 則發出  Pooling  封包
        {
            return true;
        }
        return false;
    }




    public class BLEPacket {
        byte[] bs_data;

        public byte[] getData() {
            return bs_data;
        }

        public byte[] getNoPacketIndexData() {
            byte[] bb = new byte[bs_data.length - 1];
            System.arraycopy(bs_data, 1,bb, 0,bb.length);
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
            System.arraycopy( data, 0, bs_data, 1,cnt);
            bs_data[0] = packetIndex;
        }
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
