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
import generalplus.com.blespeechplugin.BuildConfig;

/**
 * Created by Kevin Hsu  on 2017/9/14.
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

    // 發現設備服務後經過的時間
    long timeTicks_OnServicesDiscovered = 0;

    public synchronized void SetConnected(boolean flag)
    {

        isConnected = flag;

        if(isConnected)
        {
            timeTicks_OnServicesDiscovered = System.currentTimeMillis();
            isDelayPoolingOnConnected = true;
            Log2File("isDelayPoolingOnConnected = true" );
        }
        else
        {
            //timeTicks_OnServicesDiscovered = 0;
            //isEnableReadNotify = false;
            //isSetMTU = false;
        }

        resetReSendPacket_count();

        Log2File("SetConnected ( " + Boolean.toString(flag) + " )");
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

    // 是否為使用 nRF52832 晶片
    boolean isNRF52832 = true;

    // 設定是否為 NRF52832 晶片
    public void SetNRF52832(boolean _isNRF52832)
    {
        this.isNRF52832 = _isNRF52832;
    }

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
    long sendPoolingIntervalTick = 300; //  0.3 秒
    public synchronized void SetSendPoolingIntervalTick(int ms)
    {
        sendPoolingIntervalTick = ms;
    }


    long sendCmdIntervalTick = 100; // 0.3 sec
    public synchronized void setSendCmdIntervalTick(int ms)
    {
        sendCmdIntervalTick = ms;
    }

    public static HandShake Instance() {
        return sInst;
    }

    boolean bNotifyUnityConnected = false;
    public boolean GetNotifyUnityConnected()
    {
        return bNotifyUnityConnected;
    }

    // 設定 Unity 是否被通知已連線 , 如果未被通知,但 HandShake的 pooling 已經可以讀寫,則要 主動通知已連線成功(原因是某些情況,不會觸發 GetService , 但已連線成功)
    public  synchronized void SetNotifyUnityConnected(boolean state)
    {
        bNotifyUnityConnected = state;
    }


    private HandShake() {
        //String [] lsSDB_Ble_DeviceName = new String[] {"C1       ","C2       ","SDB-BT","DB-2-Pro","DB-2","sdb Bt dongle"};
        lsSDB_Ble_DeviceName.add("C1       ");
        lsSDB_Ble_DeviceName.add("C2       ");
        lsSDB_Ble_DeviceName.add("SDB-BT");
        lsSDB_Ble_DeviceName.add("DB-2-Pro");
        lsSDB_Ble_DeviceName.add("DB-2");
        lsSDB_Ble_DeviceName.add("sdb Bt dongle");
    }

    public void OnStartScan()
    {
        timeTicks_OnServicesDiscovered = 0;
        isEnableReadNotify = false;
        isSetMTU = false;
    }

    public static String bytesToHexString(byte [] src)
    {
        if (src == null || src.length <= 0) {
            return null;
        }

        StringBuilder stringBuilder = new StringBuilder();
        for(int i=0;i<src.length;i++)
        {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }

            stringBuilder.append(hv);
        }

        return stringBuilder.toString();
    }

    //  欲找的藍芽靶設備
    //public String BLE_Device_Name = "C1";
    //public String BLE_Device_Address = "";

    //  SDB BLE 設備
    //public List<String> lsSDB_Ble_DeviceName = new ArrayList<String>();
    //public  String [] lsSDB_Ble_DeviceName = new String[] {"C1       ","C2       ","SDB-BT","DB-2-Pro","DB-2","sdb Bt dongle"};
    public List<String> lsSDB_Ble_DeviceName = new ArrayList<String>();// {"C1       ","C2       ","SDB-BT","DB-2-Pro","DB-2","sdb Bt dongle"};

    // 是否有可用的 SDB BLE 設備
    public boolean CheckSDBBleDevice(String deviceName)
    {
        for(String s : lsSDB_Ble_DeviceName)
        {
            if(deviceName.equals(s))
            {
                return true;
            }
        }

        return false;
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

    int reSendPacket_count_To_Disconnect = 50 * 2 ;//* 10 ; //  封包重送 50 次 * 0.3sec = 15 sec  * 4 * 10  = 10 分鐘 後, 將斷線
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

    public boolean AllowPolling = true;
    public  boolean isEnableReadNotify = false;
    public  boolean isSetMTU = false;
    public  int threed_sleep_interval = 20; // 20ms
    public  long thread_pass_time = 0;
    public  int iThreadLoopIndex = 0;

    Thread t = new Thread(new Runnable() {
        @Override
        public void run() {
            while (isRunning) {
                try {
                    Thread.sleep(threed_sleep_interval);
                    thread_pass_time += threed_sleep_interval;
                    iThreadLoopIndex++;


                    if(!isConnected && !isGetServiceing)
                        continue;;


                    if(isSetMTU == false && timeTicks_OnServicesDiscovered > 0 )
                    {
                        long passTimeAfter_OnServicesDiscovered = System.currentTimeMillis() - timeTicks_OnServicesDiscovered;
                        if(passTimeAfter_OnServicesDiscovered > ( 1 * 1000) )
                        {
                            Log2File("----------->  to setMTU ... 244");
                            isSetMTU = true;
                            BleFramework.getInstance().setMTU(244);
                        }
                    }

                    //if(iThreadLoopIndex % 50 == 0 )
                    // BleFramework.getInstance().EnableCharReadNotify(true);
                    // 發現藍牙服務後, 約 二秒後再啟用讀取的通知
                    if(isEnableReadNotify == false && timeTicks_OnServicesDiscovered > 0 )
                    {
                        long passTimeAfter_OnServicesDiscovered = System.currentTimeMillis() - timeTicks_OnServicesDiscovered;
                        if(passTimeAfter_OnServicesDiscovered > ( 2 * 1000) )
                        {
                            Log2File("----------->  EnableCharReadNotify ... true");
                            isEnableReadNotify = true;
                            BleFramework.getInstance().EnableCharReadNotify(true);
                        }
                    }

                    // 檢查是否連線取得 service -  time out
                    /*
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

                     */

                    //---------------------------------------------- for SDB_BT  [kevin/2020/07/17] add
                    if(isResponseMode) //  C1 是使用 NoResposeMode , 而此處的 SDB_BT , DB-Pro  ResponseMode  = false , 此處不做重發機制, 重發由 unity 決定
                    { // 是否考處判段藍牙設備名稱比較保險
                        threed_sleep_interval = 10; // 每 10 ms 處理一次

                        //AddReSendCount();
                        SendPacket();
                        //HandShake.Instance().Log2File("CheckSendPacketTimeOut ( ) , now  to re send packet ( " + GetSendPacket_count() + " )");

                        continue;
                    }
                    // ------------------------------------------ for c2 . end


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

                    if (AllowPolling == false)
                        continue;

                    // 檢查 Pooling 是否逾時 , 沒有逾時繼續 , 逾時則會重發 Pooling Packet
                    if( isSendPooling )
                    {
                        if (CheckPoolingTimeOut())
                        {
                            //  重送次數超過限制次數 , 將斷線 --- > 改送超過 10 秒 就會 disconnect
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
        //if(BuildConfig.DEBUG)
        //    Log.d(Tag, msg);

        LogFile.GetInstance().AddLogAndSave(true,msg);
    }

    public synchronized void PostPacket(byte[] data) {

        if(isNRF52832)
        {
            BLE_NRF52832_Packet p = new BLE_NRF52832_Packet(data);
            lsPacket.add(p);
            return;
        }
        else
        {
            BLEPacket p = new BLECmdPacket(data);
            lsPacket.add(p);
        }
    }

    public synchronized void PostPacket(byte[] data,int len) {

        //if(isNRF52832)
        if(len != 20)
        {
            BLE_NRF52832_Packet p = new BLE_NRF52832_Packet(data,len);
            lsPacket.add(p);
            return;
        }
        else
        {
            BLEPacket p = new BLECmdPacket(data);
            lsPacket.add(p);
        }
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

        // [2017/10/03] Kevin. 如果已連線但未觸發GetService,但又已經正常 pooling 了, 那麼則要補通知 Unity 已經連線成功
        if(!GetNotifyUnityConnected())
        {
            SetNotifyUnityConnected(true);
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidConnect", "Success");
        }

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

        // nrf 晶片的話,要記錄 由AndroidSystem回應己送出的 packed_index
        if(isNRF52832)
        {
            nrf52832_app2ble_packet_index_OnWritedBySystem = nrf52832_app2ble_packet_index;
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
    public synchronized void ResetTimeOut()
    {
        preTime = System.currentTimeMillis();
    }

    // 收到封包
    public synchronized void OnRecvPacket(boolean isSuccess, byte[] data) {

        //Log.d(Tag, "OnRecvPacket ( ) .. ");
        //Log.d(Tag, "Recv Data = " + StringTools.bytesToHex(data));
        if(data == null || data.length <4) // 一開始取得服務時,會收到長度為 0 的封包
        {
            Log.d(Tag, "OnRecvPacket ( ) ... data == null || data.length <4");
            return;
        }

        String xData = StringTools.byteToHexString(data," ");//bytesToHex(data); // full-data
        String xBle4Data = xData.substring(6);        // BLE-4.0 資料(最大長度 20 bytes - 2 bytes) ex: "01 34 67 90 23 .... "  <----  位元組字串之間需要一個空白符,不然 unity 會解析失敗
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
        else
            isGetServiceing = false; // [2017/10/16].adj. by kevin


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

                if(!LogFile.GetInstance().bStopSave)
                    Log.d(Tag,"isSendPooling  -  response , .... Success");

                // 2017/09/20 BLE 主動發送的資料,己合併在 回應Polling 第三bytes以後, 所以APP不需要SendResponsePacket ,但必須抽出Poolling第三bytes為ox50的資料
                if( data[2] != 0x00 && data[2] != 0xFF) //== 0x50)  // 0x50 是 GBX 有接時的第一鍵值
                {
                    // 切換到 發送回應封包給BLE ,只回應一次
                    SendResponsePacket(data,xData);

                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidReceiveData", xBle4Data); // NoResponseMode : 只傳後面 18 bytes 給 unity

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

    // 記錄最近一次 app 2 ble 的 packet index
    int nrf52832_app2ble_packet_index = 0;

    // 記錄最近一次 app 2 ble 的 packet index 已收到系統回應送出
    int nrf52832_app2ble_packet_index_OnWritedBySystem = 0;

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

        //  No Response 使用 , C1 ==> No Respon0 = 114se Mode ==> 使用 Data 第一個 byte 當做是 Packet Index
        //   當連接設備是 C2 , DB-2-Pro 時, 則使用 不固定封包長度 發送
        if(isNRF52832)
        {
            // 檢查是否重覆發送
            // 如果前一個封包送出後 , 需滿足下面二個條件之一才能,再次發送封包
            // 1.送出回應事件(OnWritePacket) 逾時
            // 2.已收到送出回應事件(OnWritePacket)
            long deltaMs = System.currentTimeMillis() - preTime;
            if( isSendPacketing && deltaMs < 1000)
            {
                LogFile.GetInstance().Log2File("等待前一個封包送出或逾時 " + deltaMs);
                return false;
            }

            isSendPacketing = true;
            preTime = System.currentTimeMillis();
            byte[] data = p.getRawData();

            int packetIndex = (int)data[0];
            if(packetIndex == nrf52832_app2ble_packet_index ) //  不重覆發送同 packet index 的 封包
            {
                 //return false; // kevin.hsu 卡封包了... 先暫時拿掉
            }
            nrf52832_app2ble_packet_index = packetIndex;

            BleFramework.mBluetoothLeService.WriteData(data);
            String hex = StringTools.bytesToHex(data);
            //Log.d(Tag, "Command , WriteData = " + hex);
            LogFile.GetInstance().Log2File("Command  , WriteData =  " + hex);
            //LogFile.GetInstance().AddLogAndSave(true,"Command  , WriteData =  " + hex);
        }
        else
        {
            byte[] data = isResponseMode ? p.getNoPacketIndexData() : p.getData();

            isSendPacketing = true;
            sendToBLE_PacketIndex = data[0];//
            preTime = System.currentTimeMillis();
            BleFramework.mBluetoothLeService.WriteData(data);

            String hex = StringTools.bytesToHex(data);
            //Log.d(Tag, "Command , WriteData = " + hex);
            LogFile.GetInstance().Log2File("Command  , WriteData =  " + hex);
            //LogFile.GetInstance().AddLogAndSave(true,"Command  , WriteData =  " + hex);
        }


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

    public synchronized boolean SendResponsePacket(byte [] data,String hex)
    {
        if (BleFramework.mBluetoothLeService == null)
            return false;

        if(!isConnected)
            return false;

        isResponsePacketing = true;
        preTime = System.currentTimeMillis();
        BleFramework.mBluetoothLeService.WriteData(data);
        //String hex = StringTools.byteToHexString(data);
        //Log.d(Tag, "Pooling Resp, WriteData =  " + hex);
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

        // [kevin.hsu/2018/06/22].adj . willson 說連線成功後要 delay 1 秒後才態開始發送 pooling]
        if(isDelayPoolingOnConnected)
        {
            isDelayPoolingOnConnected = false;
            Log2File("isDelayPoolingOnConnected = false , and sleep 1 sec " );
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        isSendPooling = true;
        preTime = System.currentTimeMillis();

        BLEPoollingPacket p = new BLEPoollingPacket();
        byte [] data = p.getData();
        BleFramework.mBluetoothLeService.WriteData(data);

        if(LogFile.GetInstance().bStopSave)
            return;

        String hex = StringTools.bytesToHex(data);
        //Log.d(Tag, "Pooling  , WriteData =  " + hex);
        LogFile.GetInstance().AddLogAndSave(true,"Pooling  , WriteData =  " + hex);
    }

    // 連線成功後要等待1秒延遲時間
    private boolean isDelayPoolingOnConnected = true;

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
        SetConnected(false);
        BleFramework.mBluetoothLeService.disconnect();;
        //BleFramework.mBluetoothLeService.connectDevice();
        UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidDisconnect", "Success");
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

    public void SetDevices(String devices)
    {
        // devices = "C1       ,C2       ,SDB-BT"
        this.lsSDB_Ble_DeviceName.clear();
        String [] ss = devices.split(",");
        for(String s : ss)
        {
            this.lsSDB_Ble_DeviceName.add(s);
        }
    }


    public class BLEPacket {
        byte[] bs_data;


        // 取得 原始資料
        public  byte[] getRawData()
        {
            return bs_data;
        }

        // 取得 資料 , 如果是  response mode , 則會 取得沒有 packe index 部份的資料, 即第二個byte以後的內容(第一個 byte為 C1-packet index)
        public byte[] getData()
        {
            if(isNRF52832)
                return bs_data;


            if(isResponseMode)
                return getNoPacketIndexData();
            return bs_data;
        }

        // 取得沒有 packe index 部份的資料, 即第二個byte以後的內容(第一個 byte為 C1-packet index)
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

    // C2 , DB-2-Pro 使用的封包
    public class BLE_NRF52832_Packet extends BLEPacket
    {
        public BLE_NRF52832_Packet() {
        }

        public BLE_NRF52832_Packet(byte [] data)
        {
            int cnt = data.length;
            bs_data = new byte[cnt];
            System.arraycopy(data, 0, bs_data, 0, cnt);
        }
        public BLE_NRF52832_Packet(byte [] data,int len)
        {
            //int cnt = data.length;
            bs_data = new byte[len];
            System.arraycopy(data, 0, bs_data, 0, len);
        }
    }

}
