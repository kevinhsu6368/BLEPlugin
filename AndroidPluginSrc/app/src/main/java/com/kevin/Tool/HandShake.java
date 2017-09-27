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
        resetReSendPacket_count();
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
    int packetIndex = 0x01;

    // 目前正在傳送的 PacketIndex
    byte sendToBLE_PacketIndex = 0x00;

    // 目前 BLE  回應的 Packet Index
    byte responseFromBLEPacketIndex = 0x00;

    // pooling status
    byte poolingResponseStatus = 0x00;

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
    long sendPacketTimeOutTick = 1000; // 1 秒
    public synchronized void SetSendPacketTimeOutTick(int ms)
    {
        sendPacketTimeOutTick = ms;
    }

    //   間隔時間發送一次 Pooling Packet
    long sendPoolingIntervalTick = 500; //  0.3 秒
    public synchronized void SetSendPoolingIntervalTick(int ms)
    {
        sendPoolingIntervalTick = ms;
    }


    long sendCmdIntervalTick = 300; // 0.3 sec
    public synchronized void setSendCmdIntervalTick(int ms)
    {
        sendCmdIntervalTick = ms;
    }

    public static HandShake Instance() {
        return sInst;
    }

    private HandShake() {

    }

    //  欲找的藍芽靶設備
    public String BLE_Device_Name = "C1";
    public String BLE_Device_Address = "";


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

    int reSendPacket_count_To_Disconnect = 10 ; //  封包重送 n 次後, 將斷線
    int reSendPacket_count = 0; // 己經重發封包 n 次
    private int GetSendPacket_count()
    {
        return reSendPacket_count;
    }
    public synchronized void setReSendPacket_count_To_Disconnect(int count)
    {
        reSendPacket_count_To_Disconnect = count;
    }
    private void resetReSendPacket_count()
    {
        reSendPacket_count = 0;
    }
    private  boolean CheckReSendPacketOverLimit()
    {
        return (reSendPacket_count >= reSendPacket_count_To_Disconnect);
    }
    private synchronized void AddReSendCount()
    {
        reSendPacket_count++;
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
                        resetReSendPacket_count();
                        if(CheckGetServiceTimeOut())
                        {
                            HandShake.Instance().Log2File("CheckGetServiceTimeOut ( ) , .... timoe out  , now  to disconnect ");
                            isGetServiceing = false;
                            DisConnect();

                        }
                        continue;
                    }


                    // 檢查是否 packet  time out -> 重發
                    if ( isSendPacketing)  // 時間未到回 false
                    {
                        if(CheckSendPacketTimeOut())
                        {
                            //  重送次數超過限制次數 , 將斷線
                            if(CheckReSendPacketOverLimit())
                            {
                                HandShake.Instance().Log2File("CheckReSendPacketOverLimit ( Command Packet ) ..  , now  to disconnect ");
                                DisConnect();
                                continue;
                            }

                            // 重送 第 n 次封包
                            AddReSendCount();
                            SendPacket();
                            HandShake.Instance().Log2File("CheckSendPacketTimeOut ( ) , now  to re send packet ( " + GetSendPacket_count() + " )");
                        }
                        continue;
                    }

                    if ( isResponsePacketing ) //  不做逾時檢查及重送
                    {
                        continue;
                    }

                    // 檢查 Pooling 是否逾時 , 沒有逾時繼續 , 逾時則會重發 Pooling Packet
                    if( isSendPooling )
                    {
                        if (CheckPoolingTimeOut())
                        {
                            //  重送次數超過限制次數 , 將斷線
                            if(CheckReSendPacketOverLimit())
                            {
                                isSendPooling = false;
                                HandShake.Instance().Log2File("CheckReSendPacketOverLimit ( Pooling Packet ) ..  , now  to disconnect ");
                                DisConnect();
                                continue;
                            }

                            // 重送
                            AddReSendCount();
                            SendPoolingPacket();
                            HandShake.Instance().Log2File("CheckPoolingTimeOut ( ) , now  to re send packet ( " + GetSendPacket_count() + " )");
                        }
                        continue;
                    }

                    // 在空間後  , 若有封包要發送 , 再發送
                    // 是否有 packet 待送
                    //if (SendPacket()) //  沒有發送一般封包 , 則往下發 pooling
                     //   continue;

                    if(!isSendPacketing &&  CheckGameBoxON() && CheckSendCmdInterval()) //  大於傳送cmd的 interval 時間,才能發送
                    {
                        if (SendPacket()) //  沒有發送一般封包 , 則往下發 pooling
                        {
                            resetReSendPacket_count();
                            continue;
                        }
                    }
                    // 是否發送 polling ?
                    if(CheckPoolingTimeOut())
                    {
                        resetReSendPacket_count();
                        //Log.d(Tag, "CheckPoolingTimeOut ( ) .. part - 2 , .... timoe out  , next  to  send packet ");
                        SendPoolingPacket();
                    }

                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    });

    private boolean CheckGameBoxON()
    {
        return (poolingResponseStatus == 0x01);
    }

    public synchronized void Start() {
        if(isRunning)
            return;

        isRunning = true;
        t.start();
    }

    public synchronized void Close() {
        isRunning = false;
    }

    public void Log2File(String msg)
    {
        Log.d(Tag, msg);
        LogFile.GetInstance().AddLogAndSave(true,msg);
    }

    public synchronized void PostPacket(byte[] data) {
        BLEPacket p = new BLECmdPacket(data);
        lsPacket.add(p);
    }

    public synchronized byte MakeNewPacketIndex() {
        packetIndex++;
        if (packetIndex > 0xff)
            packetIndex = 1;
        return (byte) packetIndex;
    }

    public synchronized void OnWritePacket(boolean isSuccess) {

        //Log.d(Tag,"OnWritePacket( ) ... done");
        Log2File("OnWritePacket( ) ... done");

        if (!isResponseMode)  //  No Response Mode , 發送成功是在 OnRecv 判斷同一個 Packet Index 才表示 發送成功
        {
            if (isSendPooling) // 改至 rec 時才變更狀態
            {
                //isSendPooling = false;
                return;
            }

            if(isResponsePacketing) // 回應 BLE - Pooling - 帶 key 值 的 封包
            {
                isResponsePacketing = false;
                ResetTimeOut();
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

        //Log.d(Tag, "OnRecvPacket ( ) .. ");
        //Log.d(Tag, "Recv Data = " + StringTools.bytesToHex(data));
        HandShake.Instance().Log2File("Recv Data = " +  StringTools.bytesToHex(data));


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

                // 2017/09/20 BLE 主動發送的資料,己合併在 回應Polling 第三bytes以後, 所以APP不需要SendResponsePacket ,但必須抽出Poolling第三bytes為ox50的資料
                if( data[2] != 0x00 && data[2] != 0xFF) //== 0x50)  // 0x50 是 GBX 有接時的第一鍵值
                {
                    // 切換到 發送回應封包給BLE ,只回應一次
                    SendResponsePacket(data);

                    // 將 Key 值送給 unity
                    byte [] recvBS = new byte [data.length-2] ;
                    System.arraycopy(data,2,recvBS,0,recvBS.length);
                    String sData = StringTools.byteToHexString(recvBS," ");
                    // 轉傳給 Unity
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidReceiveData", sData);
                }

            }
            else
            {
                Log.d(Tag,"isSendPooling  -  response , .... but  response index != 00");
            }
        }
        else if (isSendPacketing)
        {

            if(rspIndex == sendToBLE_PacketIndex) // 回應封包的 index 一樣,表示封包已經發送成功
            {
                //  如果最後一個 byte 值 = 0x0F 表示 GBX - OFF , 則暫時不刪資料, 並切回 pooling 狀態
                // 通知 Unity 顯示, 請重新插上 GBX 的訊息
                if(data[19] == 0x0F)
                {
                    Log.d(Tag,"isSendPacketing  : GBX - OFF , 通知 Unity 顯示  GBX 己斷線,請重插上GBX ");
                    LogFile.GetInstance().AddLogAndSave(true,"isSendPacketing  : GBX - OFF , 通知 Unity 顯示  GBX 己斷線,請重插上GBX ");
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnAPP_WritePacket_to_Ble_Fail", "GBX-OFF");
                    isSendPacketing = false;
                    return ;
                }

                isSendPacketing = false;
                Log.d(Tag,"isSendPacketing  -  response , .... Success");
                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnAPP_WritePacket_to_Ble_Success", "isSendPacketing");
                if(lsPacket.size()>0)
                {
                    lsPacket.remove(0);
                    Log.d(Tag,"Remove Packet  Finished ... Packet Index = " + StringTools.byteToHexString(new byte[]{rspIndex}) );
                }
            }
            else
            {
                Log.d(Tag, "isSendPacketing  -  response , .... but  response index error");
            }

        }
        // 2017/09/20 BLE 主動發送的資料,己合併在 回應Polling 第三bytes以後, 所以APP不需要SendResponsePacket ,但必須抽出Poolling第三bytes為ox50的資料
        /*
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
        */

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
        sendToBLE_PacketIndex = data[0];//
        preTime = System.currentTimeMillis();
        BleFramework.mBluetoothLeService.WriteData(data);

        String hex = StringTools.bytesToHex(data);
        Log.d(Tag, "Command , WriteData = " + hex);
        LogFile.GetInstance().AddLogAndSave(true,"Command  , WriteData =  " + hex);

        return true;
    }

    // 這個只會送一次
    // 2017/09/20 BLE 主動發送的資料,己合併在 回應Polling 第三bytes以後, 所以APP不需要SendResponsePacket ,但必須抽出Poolling第三bytes為ox50的資料
    public synchronized boolean SendResponsePacket(byte BLECmdPacketIndex)
    {
        if (BleFramework.mBluetoothLeService == null)
            return false;

        if(!isConnected)
            return false;

        // 2017/09/20 BLE 主動發送的資料,己合併在 回應Polling 第三bytes以後, 所以APP不需要SendResponsePacket ,但必須抽出Poolling第三bytes為ox50的資料
        if(true)
            return false;

        byte[] data = new byte[1];
        data[0] = BLECmdPacketIndex;

        isResponsePacketing = true;
        preTime = System.currentTimeMillis();
        BleFramework.mBluetoothLeService.WriteData(data);
        Log.d(Tag, "SendResponsePacket ( ) .... ");
        Log.d(Tag,"Write Data =  " + StringTools.byteToHexString(data," "));
        return true;
    }

    public synchronized boolean SendResponsePacket(byte [] data)
    {
        if (BleFramework.mBluetoothLeService == null)
            return false;

        if(!isConnected)
            return false;

        isResponsePacketing = true;
        preTime = System.currentTimeMillis();
        BleFramework.mBluetoothLeService.WriteData(data);
        String hex = StringTools.byteToHexString(data);
        Log.d(Tag, "Pooling Resp, WriteData =  " + hex);
        LogFile.GetInstance().AddLogAndSave(true,"Pooling Resp, WriteData =  " + hex);
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

        String hex = StringTools.bytesToHex(data);
        Log.d(Tag, "Pooling  , WriteData =  " + hex);
        LogFile.GetInstance().AddLogAndSave(true,"Pooling  , WriteData =  " + hex);
    }


    // 檢查連線後取得 service 是否逾時 ,  如果 逾時 -->  斷線,重連
    private synchronized boolean CheckGetServiceTimeOut() {
        // 是否逾時
        getServicePassTick = System.currentTimeMillis() -  preTime;

        if (getServicePassTick > 30 * 1000) // 等候 30 秒
        {
            return true; // 已經逾時
        }

        return false;
    }

    private void DisConnect()
    {
        isConnected = false;
        BleFramework.mBluetoothLeService.disconnect();;
        //BleFramework.mBluetoothLeService.connectDevice();
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

    private synchronized boolean CheckSendCmdInterval() {
        sendPacketPassTick = System.currentTimeMillis() - preTime;

        if (sendPacketPassTick > sendCmdIntervalTick) // 超過 interval 才能發送下一個封包
        {
            return true;
        }
        return false;
    }


    public class BLEPacket {
        byte[] bs_data;

        public byte[] getData()
        {
            if(isResponseMode)
                return getNoPacketIndexData();
            return bs_data;
        }

        public byte[] getNoPacketIndexData() {
            byte[] bb = new byte[bs_data.length - 1];
            System.arraycopy(bs_data, 1,bb, 0,bb.length);
            return bb;
        }
    }

    public class BLEResponsePacket extends  BLEPacket
    {
        public BLEResponsePacket() {
        }
        public BLEResponsePacket(byte[] data) {
            int cnt = data.length;
            bs_data = new byte[20];

                if (cnt > 20)
                    cnt = 20;
            System.arraycopy(data, 0, bs_data, 0, cnt);
        }
    }

    public class BLECmdPacket extends BLEPacket {
        public BLECmdPacket() {
        }

        public BLECmdPacket(byte[] data) {
            int cnt = data.length;
            bs_data = new byte[20];
            if(isResponseMode)
            {
                if (cnt > 20)
                    cnt = 20;

                System.arraycopy(data, 0, bs_data, 0, cnt);
            }
            else  // for no response mode
            {
                System.arraycopy(data, 0, bs_data, 1, cnt);
                bs_data[0] = MakeNewPacketIndex();
            }
        }
    }

    public class BLEPoollingPacket extends BLEPacket
    {
        public BLEPoollingPacket()
        {
            this.bs_data = new byte[20];
            this.bs_data[0] = 0x00;
        }
    }

}
