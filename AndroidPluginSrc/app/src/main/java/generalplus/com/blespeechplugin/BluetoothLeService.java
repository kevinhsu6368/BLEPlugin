/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package generalplus.com.blespeechplugin;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.kevin.Tool.HandShake;
import com.kevin.Tool.LogFile;
import com.unity3d.player.UnityPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String m_strOldAddress = null;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    public boolean m_bAck = false;
    private String m_strVersion = "";

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    boolean Write_Characteristic_Status = false;

    public final static String ACTION_GATT_RSSI =
            "com.example.bluetooth.le.ACTION_GATT_RSSI";

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";

    public final static String READ_DATA =
            "com.example.bluetooth.le.READ_DATA";

    public final static String WRITE_DATA =
            "com.example.bluetooth.le.WRITE_DATA";

    public final static String AUTO_CONNECT =
            "AUTO_CONNECT";
    public final static String GET_VERSION =
            "GET_VERSION";
    public final static String GET_ACK =
            "GET_ACK";

    public final static String NEXT_RECONNECT =
            "NEXT_RECONNECT";

    public final static UUID UUID_HEART_RATE_MEASUREMENT =
            UUID.fromString(SampleGattAttributes.HEART_RATE_MEASUREMENT);

    public final static UUID UUID_FFF0_CHARACTERISTIC = UUID.fromString(SampleGattAttributes.FFF0_CHARACTERISTIC);
    public final static UUID UUID_FFF1_CHARACTERISTIC = UUID.fromString(SampleGattAttributes.FFF1_CHARACTERISTIC);
    public final static UUID UUID_FFF2_CHARACTERISTIC = UUID.fromString(SampleGattAttributes.FFF2_CHARACTERISTIC);
    public final static UUID UUID_FFF4_CHARACTERISTIC = UUID.fromString(SampleGattAttributes.FFF4_CHARACTERISTIC);
    public final static UUID UUID_FFF5_CHARACTERISTIC = UUID.fromString(SampleGattAttributes.FFF5_CHARACTERISTIC);

    public void EnableCharReadNotify(boolean bEnable)
    {
        //setCharacteristicNotification(mBluetoothGatt,char,);
        if(mBluetoothGatt == null)
            return;
        BluetoothGattService Service = mBluetoothGatt.getService(UUID_FFF0_CHARACTERISTIC);
        if (Service == null) {
            return ;
        }
        BluetoothGattCharacteristic characteristic = Service.getCharacteristic(getUuid_ReadCharacteristic());
        if (characteristic == null) {
            return ;
        }

        final int charaProp = characteristic.getProperties();

        setCharacteristicNotification(mBluetoothGatt, characteristic, bEnable);


    }

    public enum SDB_BLE_TYPE
    {
        C1 ,
        USB_DONGLE,
        DB2 ,
        SDB_BT ,
        DB2_Pro
    }

    // 設定 連接藍芽靶種類
    public SDB_BLE_TYPE sdb_ble_type = SDB_BLE_TYPE.C1;

    public UUID getUuid_ReadCharacteristic()
    {
        /*
        if (this.sdb_ble_type == SDB_BLE_TYPE.C1 || this.sdb_ble_type == SDB_BLE_TYPE.USB_DONGLE ) {
            return UUID_FFF1_CHARACTERISTIC;
        }
        */


        if (this.sdb_ble_type == SDB_BLE_TYPE.DB2) {
            return UUID_FFF4_CHARACTERISTIC;
        }
        return UUID_FFF1_CHARACTERISTIC;
    }

    public UUID getUuid_WriteCharacteristic()
    {
        /*
        if (this.sdb_ble_type == SDB_BLE_TYPE.C1 || this.sdb_ble_type == SDB_BLE_TYPE.USB_DONGLE )
            return UUID_FFF2_CHARACTERISTIC;
*/
        if (this.sdb_ble_type == SDB_BLE_TYPE.DB2)
            return UUID_FFF5_CHARACTERISTIC;

        return UUID_FFF2_CHARACTERISTIC;
    }


    public Handler mHandler = new Handler();
    // Stops scanning after 20 seconds.
    private static final long SCAN_PERIOD = 20000;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    public ArrayList<BLEObj> listBTDevice = new ArrayList<BLEObj>();
    public String deviceName = "";
    private String m_strAckType = "";
    private boolean m_bActiveDiscoonnect = false;
    private int m_iIntervalTime = -1;
    private int iCountOfConnectStateChange = 0;
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            iCountOfConnectStateChange++;
            HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  start");
            String intentAction;
            Log.e(TAG, "onConnectionStateChange received: " + status + " newState = " + newState);
            switch (status) {
                case BluetoothGatt.GATT_SUCCESS:
                    if (newState == BluetoothProfile.STATE_CONNECTED)
                    {
                        intentAction = ACTION_GATT_CONNECTED;
                        broadcastUpdate(intentAction);
                        HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  broadcastUpdate  ....  ACTION_GATT_CONNECTED");
                        //Log.i(TAG, "Connected to GATT server.");
                        // Attempts to discover services after successful connection.
                        //Log.e(TAG, "Attempting to start service discovery:" +
                        //        gatt.discoverServices());

                        boolean bGetService = gatt.discoverServices();
                        HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  discoverServices( )  : " + bGetService + "   .... end");
                        return;
                        // 設置中等連接速率 30 - 60ms)
                        // gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        // 处理断开连接

                        return;
                    }
                    else if (newState == BluetoothProfile.STATE_DISCONNECTED)
                    {
                        HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ... newState == BluetoothProfile.STATE_DISCONNECTED ... start");

                        // [2017/10/18] . adj   . disable doReConnect( ) 不主動做重連, 只通知 unity 斷線
                        /*
                        if (false == m_bActiveDiscoonnect)
                        {
                            doReConnect();
                            HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ... doReConnect");
                        }
                        else
                        {
                            close();
                            m_bActiveDiscoonnect = false;
                            intentAction = ACTION_GATT_DISCONNECTED;
                            mConnectionState = STATE_DISCONNECTED;
                            Log.e(TAG, "Disconnected from GATT server.");
                            broadcastUpdate(intentAction);
                            HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ... broadcastUpdate  .... ACTION_GATT_DISCONNECTED");
                        }
                        */
                        intentAction = ACTION_GATT_DISCONNECTED;
                        mConnectionState = STATE_DISCONNECTED;
                        // Log.e(TAG, "Disconnected from GATT server.");
                        broadcastUpdate(intentAction);


                        HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ... newState == BluetoothProfile.STATE_DISCONNECTED ... end");
                        return;
                    }
                    break;
                case 0x01:
                    Log.e(TAG, "GATT CONN L2C FAILURE");
                    HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ... 0x01: GATT CONN L2C FAILURE");
                    break;
                case 0x08:
                    Log.e(TAG, "GATT CONN TIMEOUT");
                    HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  0x08: GATT CONN TIMEOUT");
                    break;
                case 0x13:
                    Log.e(TAG, "GATT CONN TERMINATE PEER USER");
                    HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  0x13: GATT CONN TERMINATE PEER USER ");
                    break;
                case 0x16:
                    Log.e(TAG, "GATT CONN TERMINATE LOCAL HOST");
                    HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...   0x16: GATT CONN TERMINATE LOCAL HOST");
                    break;
                case 0x3E:
                    Log.e(TAG, "GATT CONN FAIL ESTABLISH");
                    HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  0x3E: GATT CONN FAIL ESTABLISH");
                    break;
                case 0x22:
                    Log.e(TAG, "GATT CONN LMP TIMEOUT");
                    HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  0x22: GATT CONN LMP TIMEOUT");
                    break;
                case 0x0100:
                    Log.e(TAG, "GATT CONN CANCEL ");
                    HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  0x0100: GATT CONN CANCEL ");
                    break;
                case 0x0085:
                    Log.e(TAG, "GATT ERROR"); // Device not reachable
                    HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  0x0085: GATT ERROR ");
                    break;
                default:
                    Log.e(TAG, "UNKNOWN (" + status + ")");
                    HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  UNKNOWN (" + status + ")");
                    break;
            }

            if(status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED)
            {
                return;
            }
            else
            {
                intentAction = ACTION_GATT_DISCONNECTED;
                mConnectionState = STATE_DISCONNECTED;
                Log.e(TAG, "Disconnected from GATT server.");
                broadcastUpdate(intentAction);
                HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  broadcastUpdate  ....  ACTION_GATT_DISCONNECTED");
                try
                {
                    Thread.sleep(100);
                }
                catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }



            // [2017/10/18] . Kevin.Hsu ... adj . disable NEXT_CONNECT
            /*
            intentAction = NEXT_RECONNECT;
            broadcastUpdate(intentAction);
            HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  broadcastUpdate  ....  NEXT_RECONNECT");

            //Log.e(TAG, "NEXT_RECONNECT");
            HandShake.Instance().Log2File("BluetoothLeService.BluetoothGattCallback.onConnectionStateChange( ) ...  end");
            */
        }

        private void doReConnect()
        {
            HandShake.Instance().Log2File("BluetoothLeService.doReConnect( ) ...  start ");
            String intentAction = ACTION_GATT_DISCONNECTED;
            mConnectionState = STATE_DISCONNECTED;
            Log.e(TAG, "Disconnected from GATT server.");
            broadcastUpdate(intentAction);
            HandShake.Instance().Log2File("BluetoothLeService.doReConnect( ) ...  broadcastUpdate ...  ACTION_GATT_DISCONNECTED  .... end");

            if (null != m_strOldAddress) {
                for (int i = 0; i < listBTDevice.size(); i++) {
                    if (listBTDevice.get(i).m_BluetoothDevice.getAddress().equalsIgnoreCase(m_strOldAddress)) {
                        Log.e(TAG, "Auto connecting.");
                        intentAction = AUTO_CONNECT;
                        broadcastUpdate(intentAction);
                        HandShake.Instance().Log2File("BluetoothLeService.doReConnect( ) ...  broadcastUpdate ...  AUTO_CONNECT  .... end");
                        //connectDevice(listBTDevice.get(i).m_BluetoothDevice);
                        return;
                    }
                }
            } else {
                Log.e(TAG, "Not Auto connecting.");
            }
            HandShake.Instance().Log2File("BluetoothLeService.doReConnect( ) ...  end ");
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            HandShake.Instance().Log2File("BluetoothLeService.onServicesDiscovered( ) ... start ");
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                Log.e("Minwen", "Service discovered " + status);
                BluetoothGattService Service = gatt.getService(UUID_FFF0_CHARACTERISTIC);
                if (null == Service) {
                    Log.e(TAG, "service not found!");
                    HandShake.Instance().OnGetServiceFinished(false);
                    HandShake.Instance().Log2File("BluetoothLeService.onServicesDiscovered( ) ... service not found!  ...  end ");
                    return;
                }
                BluetoothGattCharacteristic characteristic = Service.getCharacteristic(getUuid_WriteCharacteristic());
                if (null == characteristic)
                {
                    Log.e(TAG, "char not found!");
                    HandShake.Instance().OnGetServiceFinished(false);
                    HandShake.Instance().Log2File("BluetoothLeService.onServicesDiscovered( ) ... char not found!  ...  end ");
                    return;
                }
                else
                {
                    // 2024/08/15.調整
                    // 強制 : 設備名稱 等於 : "C1       " 的話 , 則  SetResponseMode(true);
                    // 強制 : 設備名稱 不是 : "C1       " 的話 , 則  SetResponseMode(false);
                    if(deviceName.startsWith("C1   "))
                    {
                        HandShake.Instance().SetResponseMode(false);
                        HandShake.Instance().Log2File("BluetoothLeService.onServicesDiscovered( ) ...  is C1 ... No Response Mode ");
                    }
                    else
                    {
                        HandShake.Instance().SetResponseMode(true);
                        HandShake.Instance().Log2File("BluetoothLeService.onServicesDiscovered( ) ...  is SDB-BT ... Response Mode ");
                    }
                    /*
                    int iType = characteristic.getProperties();

                    String intentAction = GET_ACK;
                    if (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == iType) {
                        m_bAck = false;
                        //Log.d(HandShake.Instance().Tag, "GetService ... is  ... no Response Mode");
                        HandShake.Instance().SetResponseMode(false);
                        HandShake.Instance().Log2File("BluetoothLeService.onServicesDiscovered( ) ... no Response Mode ");
                        //m_strAckType = "No Ack";
                    } else if (BluetoothGattCharacteristic.PROPERTY_WRITE == iType) {
                        m_bAck = true;
                        //m_strAckType = "Ack";
                        //Log.d(HandShake.Instance().Tag, "GetService ... is  ... Response Mode");
                        HandShake.Instance().SetResponseMode(true);
                        HandShake.Instance().Log2File("BluetoothLeService.onServicesDiscovered( ) ...  Response Mode ");
                    } else {
                        m_bAck = true;
                        m_strAckType = "error ack:" + iType;
                        //Log.d(HandShake.Instance().Tag, "GetService ... is  ... Response Mode ( error ack:)");
                        HandShake.Instance().SetResponseMode(true);
                        HandShake.Instance().Log2File("BluetoothLeService.onServicesDiscovered( ) ...  Response Mode ( error ack:) ");
                    }
                    //broadcastUpdate(intentAction);

*/
                }


                try
                {
                    //Thread.sleep(500);
                    Thread.sleep(1000);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }



                if (ReadData(gatt))
                {
                    boolean isOK = false;
                    for (int i = 0; i < listBTDevice.size(); i++) {
                        if (gatt.getDevice() == listBTDevice.get(i).m_BluetoothDevice) {
                            BLEObj obj = listBTDevice.get(i);
                            //obj.m_WriteCharacteristic = characteristic;
                            //obj.m_BluetoothGatt = gatt;
                            mBluetoothGatt = gatt;
                            m_strOldAddress = obj.m_BluetoothDevice.getAddress();
                            broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                            mConnectionState = STATE_CONNECTED;
                            HandShake.Instance().SetConnected(true);
                            HandShake.Instance().OnGetServiceFinished(true);
                            HandShake.Instance().Log2File("BluetoothLeService.onServicesDiscovered( ) ... end ");
                            isOK = true;
                            break;
                            //return;
                        }
                    }

 //                   if(isOK)
 //                   {
                        // 設定 封包傳送速度 - 1
//                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED); // CONNECTION_PRIORITY_HIGH

//                        // 設定 封包傳送速度 - 2
//                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                            gatt.setPreferredPhy(BluetoothDevice.PHY_LE_2M,BluetoothDevice.PHY_LE_2M,BluetoothDevice.PHY_OPTION_NO_PREFERRED);
//                        }

   //                     return;
   //                 }
                }
                else
                {
                    HandShake.Instance().OnGetServiceFinished(false);
                    Log.e(TAG, "ReadData failed.");
                }


            }
            else
            {
                HandShake.Instance().OnGetServiceFinished(false);
                Log.e(TAG, "onServicesDiscovered received: " + status);
            }

            HandShake.Instance().Log2File("BluetoothLeService.onServicesDiscovered( ) ... end ");
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                HandShake.Instance().OnRecvPacket(true, characteristic.getValue());
            } else {
                HandShake.Instance().OnRecvPacket(false, characteristic.getValue());
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                //if(!LogFile.GetInstance().bStopSave)
                Log.e(TAG, "onCharacteristicWrite ... ok");

                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);

                HandShake.Instance().OnWritePacket(true);

                //OnWritePacket
            } else {
                HandShake.Instance().OnWritePacket(false);
                Log.e(TAG, "onCharacteristicWrite ... fail");
            }

        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            Log.e(TAG, "onDescriptorWrite ... ");
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//            	Log.e("minwen",  "Callback: Wrote GATT Descriptor successfully.");           
//            }           
//            else{
//            	Log.e("minwen", "Callback: Error writing GATT Descriptor: " + status);
//            }
//
//            if (UUID_FFF2_CHARACTERISTIC.equals(descriptor.getCharacteristic().getUuid())) {            	
//                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
//            	mBluetoothGatt.writeDescriptor(descriptor);
//            }
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.e(TAG, "onCharacteristicChanged ... ");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);

            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                broadcastUpdate(ACTION_GATT_RSSI,rssi);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            Log.d(TAG,String.format("onMtuChanged --> mtu = %d , status = %d",mtu,status));
            super.onMtuChanged(gatt, mtu, status);
        }

        @Override
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // PHY更新成功
                Log.d("BLE", "PHY updated: TX = " + txPhy + ", RX = " + rxPhy);
                HandShake.Instance().Log2File("BLE PHY updated: Success , TX = " + txPhy + ", RX = " + rxPhy);
            } else {
                // PHY更新失败
                Log.e("BLE", "PHY update failed with status: " + status);
                HandShake.Instance().Log2File("BLE PHY updated: Fail , TX = " + txPhy + ", RX = " + rxPhy);
            }
        }

    };

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void broadcastUpdate(final String action,int value) {
        final Intent intent = new Intent(action);
        intent.putExtra("value",value);
        sendBroadcast(intent);

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        final Intent intent = new Intent(action);

        String strOutput = "";

        // === For all other profiles, writes the data formatted in HEX. ===
        final byte[] data = characteristic.getValue();
        if (data != null && data.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(data.length);
            for (byte byteChar : data)
                stringBuilder.append(String.format("%02X ", byteChar));
            //intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
            //intent.putExtra(EXTRA_DATA, stringBuilder.toString());

            strOutput = stringBuilder.toString();
        }

        // === For Read Characteristic ===
        if (getUuid_ReadCharacteristic().equals(characteristic.getUuid())) {
            intent.putExtra(READ_DATA, strOutput);
            //LogDataManager.getInstance().AddLogText("Read Data : " + strOutput);
        }

        // === For Write Characteristic ===
        if (getUuid_WriteCharacteristic().equals(characteristic.getUuid())) {
            intent.putExtra(WRITE_DATA, strOutput);
            //LogDataManager.getInstance().AddLogText("Write Data : " + strOutput);
        }

        sendBroadcast(intent);
    }


    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // After using a given device, you should make sure that BluetoothGatt.close() is called
        // such that resources are cleaned up properly.  In this particular example, close() is
        // invoked when the UI is disconnected from the Service.
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    public boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        HandShake.Instance().Log2File("BluetoothLeService.initialize( ) ... start ");
        if (mBluetoothManager == null)
        {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null)
            {
                //Log.e(TAG, "Unable to initialize BluetoothManager.");
                HandShake.Instance().Log2File("BluetoothLeService.initialize( ) ... Unable to obtain a BluetoothManager ******* ... end ");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null)
        {
            //Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            HandShake.Instance().Log2File("BluetoothLeService.initialize( ) ... Unable to obtain a BluetoothAdapter *******  ... end");
            return false;
        }
        HandShake.Instance().Log2File("BluetoothLeService.initialize( ) ...  success  ... end ");

        return true;
    }

    // 設定封包速度 : 0=預設(慢 100-200ms) , 1=一般(30-60ms) , 2=最快(5-7ms)
    public void SetPacketSpeed(int speed)
    {
        if(speed == 1) //  1=一般(30-60ms)
        {
            this.mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
        }
        else if(speed == 2) // 2=最快(5-7ms)
        {
            this.mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
        }
        else // 0=預設(慢 100-200ms)
        {
            if(this.deviceName.contains("C1 ")) // A1 藍牙靶收到的話,藍牙晶片會斷線,故不處理
                return;
            this.mBluetoothGatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER);
        }

    }

    public boolean setMTU(int len)
    {
        if(len > 244 )
            len = 244;
        Log.d("BLE" ,  "設定 BLE-MTU = " + len);
        return this.mBluetoothGatt.requestMtu(len);
    }

    public void cleanAddress() {
        m_strOldAddress = null;
    }

    /**
     * Disconnects an existing connection or cancel a pending connection. The disconnection result
     * is reported asynchronously through the
     * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
     * callback.
     */
    public synchronized void disconnect() {
        //Log.d(HandShake.Instance().Tag,"BluetoothLeService.disconnect( )");
        //LogFile.GetInstance().AddLogAndSave(true,"BluetoothLeService.disconnect( )");
        HandShake.Instance().Log2File("BluetoothLeService.disconnect( ) ... start");
        HandShake.Instance().SetConnected(false);

        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            //Log.e(TAG, "disconnect fail mBluetoothGatt not initialized");
            HandShake.Instance().Log2File("BluetoothLeService.disconnect( ) ... disconnect fail mBluetoothGatt not initialized  and return ... end");
            return;
        }
        Log.e(TAG, "Try Disconnecting");

        StopReadData();
        mBluetoothGatt.disconnect();

        HandShake.Instance().Log2File("BluetoothLeService.disconnect( ) ... mBluetoothGatt.disconnect()");
        mNotifyCharacteristic = null;
        HandShake.Instance().Log2File("BluetoothLeService.disconnect( ) ... end");
        //通知 unity 已經段線

    }

    public synchronized void ActiveDisconnect() {
        HandShake.Instance().Log2File("BluetoothLeService.ActiveDisconnect( ) ... start");
        if (mBluetoothAdapter == null || mBluetoothGatt == null)
        {
            //Log.e(TAG, "disconnect fail mBluetoothGatt not initialized");
            HandShake.Instance().Log2File("BluetoothLeService.ActiveDisconnect( ) ... disconnect fail mBluetoothGatt not initialized  and return ... end");
            return;
        }
        m_bActiveDiscoonnect = true;
        Log.e(TAG, "Try Disconnecting");
        StopReadData();
        mBluetoothGatt.disconnect();
        mNotifyCharacteristic = null;
        HandShake.Instance().Log2File("BluetoothLeService.ActiveDisconnect( ) ... end");
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public synchronized void close()
    {
        HandShake.Instance().Log2File("BluetoothLeService.close( ) ... start");
        if (mBluetoothGatt == null) {
            return;
        }

        disconnect();

        try {
            Thread.sleep(1000); // [2017/10/18]. Kevin.Hsu 由 100ms 改為 1000 ms
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;

        HandShake.Instance().Log2File("BluetoothLeService.close( ) ... end");
    }

    /**
     * Request a read on a given {@code BluetoothGattCharacteristic}. The read result is reported
     * asynchronously through the {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
     * callback.
     *
     * @param characteristic The characteristic to read from.
     */
    public void readCharacteristic(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic) {
        if (mBluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "readCharacteristic BluetoothAdapter not initialized");
            return;
        }
        bluetoothGatt.readCharacteristic(characteristic);
    }


    public BluetoothGattCharacteristic writeCharacteristic(BluetoothGattCharacteristic characteristic, byte byData[]) {

        if (mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return null;
        }

        BluetoothGattService Service = mBluetoothGatt.getService(UUID_FFF0_CHARACTERISTIC);
        if (Service == null) {
            Log.e(TAG, "service not found!");
            return null;
        }
        BluetoothGattCharacteristic characteristic1 = Service.getCharacteristic(getUuid_WriteCharacteristic());
        if (characteristic1 == null) {
            Log.e(TAG, "char not found!");
            return null;
        }

        if (getUuid_WriteCharacteristic().equals(characteristic.getUuid())) {

//        	byte[] value1 = {(byte)0xF0, (byte)0x80, (byte)0xA8, (byte)0x00, (byte)0x00,
//        			(byte)0x38, (byte)0x18    			};
//        	byte[] value2 = {(byte)0x00, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF, (byte)0xFF,
//        			};
//        	byte[] value3 = {(byte)0xF0, (byte)0x81, (byte)0x00, (byte)0x00, (byte)0x80};
//        	byte[][]value4 = {value1, value1, value2, value3};        	
//        	characteristic1.setValue(value4[i32ByteCounter]);        	
//        	
//        	if(i32ByteCounter<=255)
//        		i32ByteCounter++;


            characteristic1.setValue(byData);

            /*
            m_bAck = HandShake.Instance().GetIsResponseMode();
            if (m_bAck) {
                characteristic1.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                characteristic1.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            }

             */
            // [kevin.hsu/2020/08/04] 全部強制使用 Write_No_Response
            //characteristic1.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

            Write_Characteristic_Status = mBluetoothGatt.writeCharacteristic(characteristic1);


            //Log.e("minwen", "Write_Characteristic_Status " + Write_Characteristic_Status);
 /*
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }


            // 送出後,要通知 BLE 己送達
            // === 2. Get Return data after Write data ===

            BluetoothGattCharacteristic Response_characteristic1 = Service.getCharacteristic(UUID_FFF1_CHARACTERISTIC);
            if (Response_characteristic1 == null) {
                Log.e(TAG, "char not found!");
                return null;
            }            
            
            setCharacteristicNotification(mBluetoothGatt,Response_characteristic1, true);

            //setCharacteristicNotification(Response_characteristic1, true);
            */
            return characteristic1;
        }
        return null;

    }

    public BluetoothGattCharacteristic writeReadCharacteristic(BluetoothGattCharacteristic characteristic, byte byData[]) {

        if (mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized");
            return null;
        }

        BluetoothGattService Service = mBluetoothGatt.getService(UUID_FFF0_CHARACTERISTIC);
        if (Service == null) {
            Log.e(TAG, "service not found!");
            return null;
        }
        BluetoothGattCharacteristic characteristic1 = Service.getCharacteristic(getUuid_WriteCharacteristic());
        if (characteristic1 == null) {
            Log.e(TAG, "char not found!");
            return null;
        }

        if (getUuid_WriteCharacteristic().equals(characteristic.getUuid())) {
            // === 1. Write data to Characteristic ===
            characteristic1.setValue(byData);

            //characteristic1.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);

            m_bAck = HandShake.Instance().GetIsResponseMode();

            if (m_bAck) {
                characteristic1.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                characteristic1.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
            }
            Write_Characteristic_Status = mBluetoothGatt.writeCharacteristic(characteristic1);

            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }


            // === 2. Get Return data after Write data ===                   
            BluetoothGattCharacteristic Response_characteristic1 = Service.getCharacteristic(getUuid_ReadCharacteristic());
            if (Response_characteristic1 == null) {
                Log.e(TAG, "char not found!");
                return null;
            }

            //setCharacteristicNotification(Response_characteristic1, true); 

            return characteristic1;
        }
        return null;

    }

    public  boolean isNotifySuccess = false;

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled        If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "setCharacteristicNotification BluetoothAdapter not initialized");
            return;
        }

        boolean bNotify = bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // Enable Notification for FFF1, added by Ryan        
        if (getUuid_ReadCharacteristic().equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
           // descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            isNotifySuccess = bluetoothGatt.writeDescriptor(descriptor); //  設定 "BLE - 通知 " 成功 ?
        }

        // This is specific to Heart Rate Measurement.
        if (UUID_HEART_RATE_MEASUREMENT.equals(characteristic.getUuid())) {
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
                    UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
        }
    }

//    public BluetoothGattService getBLEGattService()
//    {
//    	return null;
//    }
//        

    /**
     * Retrieves a list of supported GATT services on the connected device. This should be
     * invoked only after {@code BluetoothGatt#discoverServices()} completes successfully.
     *
     * @return A {@code List} of supported services.
     */
    public List<BluetoothGattService> getSupportedGattServices() {
        if (mBluetoothGatt == null) return null;

        return mBluetoothGatt.getServices();
    }

    public void setWriteStatus(boolean status) {
        Write_Characteristic_Status = status;
    }

    public boolean getWriteStatus() {
        return Write_Characteristic_Status;
    }

    // ======================================== Adding ............ ============================================
    public boolean WriteData(byte[] byVRCommand) {
        if (this == null || mBluetoothGatt == null)
            return false;

        BluetoothGattService Service = mBluetoothGatt.getService(UUID_FFF0_CHARACTERISTIC);
        if (Service == null) {
            return false;
        }
        BluetoothGattCharacteristic characteristic = Service.getCharacteristic(getUuid_WriteCharacteristic());
        if (characteristic == null) {
            return false;
        }

        writeCharacteristic(characteristic, byVRCommand);
        // Sleep for a while to call onCharacteristicWrite
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return true;
    }

    public boolean WriteReadData(byte[] byVRCommand) {
        if (this == null || mBluetoothGatt == null)
            return false;

        BluetoothGattService Service = mBluetoothGatt.getService(UUID_FFF0_CHARACTERISTIC);
        if (Service == null) {
            return false;
        }
        BluetoothGattCharacteristic characteristic = Service.getCharacteristic(getUuid_WriteCharacteristic());
        if (characteristic == null) {
            return false;
        }

        writeReadCharacteristic(characteristic, byVRCommand);
        // Sleep for a while to call onCharacteristicWrite
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return true;
    }

    /*
        @brief 取得藍芽信號強度
        */
    public  boolean ReadRSSI()
    {
        return mBluetoothGatt.readRemoteRssi();
    }

    public boolean ReadData(BluetoothGatt bluetoothGatt) {

        BluetoothGattService Service = bluetoothGatt.getService(UUID_FFF0_CHARACTERISTIC);
        if (Service == null) {
            return false;
        }
        BluetoothGattCharacteristic characteristic = Service.getCharacteristic(getUuid_ReadCharacteristic());
        if (characteristic == null) {
            return false;
        }

        // === Start Listen Notify and Read Data === 	        
        final int charaProp = characteristic.getProperties();

        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
            // If there is an active notification on a characteristic, clear
            // it first so it doesn't update the data field on the user interface.
            if (mNotifyCharacteristic != null) {
                setCharacteristicNotification(bluetoothGatt, mNotifyCharacteristic, false);
                mNotifyCharacteristic = null;
            }
            readCharacteristic(bluetoothGatt, characteristic);
        }
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            mNotifyCharacteristic = characteristic;
            // [kevin.hsu/2018/06/22].adj. Willson 說在這之 Notify Enable 之前要 delay 1 sec . <---- [kevin.hsu/2020/08/02].經測試即,史在此停一秒仍然無法把指令送出給 BLE
           /*
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                 */

            // 當讀取服務的時候,不馬上去變更 讀取的通知 , 而改為延遲 2 秒再通知
            //setCharacteristicNotification(bluetoothGatt, characteristic, true);
        }
        return true;
    }

    public boolean StopReadData() {
        BluetoothGattService Service = mBluetoothGatt.getService(UUID_FFF0_CHARACTERISTIC);
        if (Service == null) {
            return false;
        }
        BluetoothGattCharacteristic characteristic = Service.getCharacteristic(getUuid_ReadCharacteristic());
        if (characteristic == null) {
            return false;
        }

        // === Start Listen Notify and Read Data === 	        
        final int charaProp = characteristic.getProperties();

        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
            // If there is an active notification on a characteristic, clear
            // it first so it doesn't update the data field on the user interface.
            if (mNotifyCharacteristic != null) {
                setCharacteristicNotification(mBluetoothGatt, mNotifyCharacteristic, false);
                mNotifyCharacteristic = null;
            }
            readCharacteristic(mBluetoothGatt, characteristic);
        }
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            mNotifyCharacteristic = characteristic;
            setCharacteristicNotification(mBluetoothGatt, characteristic, true);
        }

        setCharacteristicNotification(mBluetoothGatt, characteristic, false);
        return true;

    }

    private long iScanForPeripherals_Count = 0;

    public synchronized void AddScanForPeripheralsCount() {
        iScanForPeripherals_Count++;
    }

    public synchronized void ResetScanForPeripheralsCount() {
        iScanForPeripherals_Count = 0;
    }

    public long GetScanForPeripheralsCount() {
        return iScanForPeripherals_Count;
    }

    public long GetSCAN_PERIOD()
    {
        int period = 15*1000;
        if(iScanForPeripherals_Count == 0)
        {
            return period;
        }
        else if(iScanForPeripherals_Count == 1)
        {
            return period;
        }

        return period*2;

    }

    private long pre_ScanForPeripherals = 0;
    public long Get_pre_ScanForPeripherals()
    {
        return pre_ScanForPeripherals;
    }

    private synchronized boolean CanScanForPeripherals()
    {
        long delta = (System.currentTimeMillis() - pre_ScanForPeripherals);
        return (delta > GetSCAN_PERIOD());
    }
    
    public synchronized void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.e(TAG, "scanLeDevice stopLeScan");
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);                    
                }
            }, GetSCAN_PERIOD());

            Log.e(TAG, "scanLeDevice startLeScan");
            pre_ScanForPeripherals = System.currentTimeMillis();
            mBluetoothAdapter.startLeScan(mLeScanCallback);


        } else {
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
    }    
    
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

				@Override
				public void onLeScan(BluetoothDevice device, int rssi,
						byte[] scanRecord) {
					// TODO Auto-generated method stub	
					// Add Device
                    String devName = device.getName();
					if (null == devName) {
						return;
					}

					// 如果 address 存在則不用往下處理
					for (int i = 0; i < listBTDevice.size(); i++) {
						if (listBTDevice.get(i).m_BluetoothDevice.getAddress().equalsIgnoreCase(device.getAddress())) {
                            //listBTDevice.get(i).rssi = rssi; // 更新 rssi

                            // 如果 rssi 變更 , 通知 unity 設備
                            BLEObj obj = listBTDevice.get(i);
                            int delta = Math.abs(obj.rssi - rssi);
                            if(delta > 10) // 當前設備的 rssi 變更時,再通知 unity
                            {
                                obj.rssi = rssi;
                                String msg = String.format("%s@%s@%d",obj.m_BluetoothDevice.getName(),obj.m_BluetoothDevice.getAddress(),obj.rssi);
                                HandShake.Instance().Log2File("BluetoothLeService.mLeScanCallback.onLeScan( ) ... update device : " + msg);
                                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidReadDevice", msg);
                            }

							return;
						}
					}

                    // 符合 C1 或 SDB-BT 晶片再加入清單裡
                    if(HandShake.Instance().CheckSDBBleDevice(device.getName()))
                    {
                        BLEObj obj = new BLEObj();
                        obj.m_BluetoothDevice = device;
                        obj.rssi = rssi;
                        listBTDevice.add(obj);

                        byte [] bs = devName.getBytes();
                        String hexDevName = HandShake.bytesToHexString(bs);
                        HandShake.Instance().Log2File( String.format("find device = %s , mac = %s , rssi = %d .... and add to list",devName,hexDevName,rssi));

                        // Send Message for updating UI List
                        Bundle mUpdateDeviceBundle = new Bundle();
                        mUpdateDeviceBundle.putInt("count", rssi);

                        Message msg = new Message();
                        msg.setData(mUpdateDeviceBundle);
                    }

                    //ConnectActivity.mUpdateDeviceHandler.sendMessage(msg);

                    // [2017/10/03]. Kevin ... Willson 說去掉下面這段,看能不能改善連線問題.
                    /*
                    if (null != m_strOldAddress) {
                    	if (m_strOldAddress.equalsIgnoreCase(device.getAddress())) {
                    		connectDevice(device);
                    		return;
						}
					}
					*/
				}
    };
    

    
    public void ClearBTDeviceList()
    {
    	listBTDevice.clear();	
    }
    
    public ArrayList<BLEObj> GetBTDeviceList()
    {
    	return listBTDevice;
    }
    
    public boolean connectDevice(BluetoothDevice device) {
    	Log.e(TAG, "Before connecting device");


    	close();
    	try {
            Thread.sleep(1000); // [2017/10/18]. Kevin.Hsu , ajd 由 100 ms 調成 1000 ms
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    	//mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback , BluetoothDevice.TRANSPORT_LE );
        if (mBluetoothGatt == null) {
            Log.e(TAG, "BluetoothGatt not found!");
            return false;
        }

        return true;
    }
    
    public String getVersion() {
    	return m_strVersion;
    }
    
    public String getAckString() {
    	return m_strAckType;
    }

    public void setIntervalTime(int iIntervalTime) {
        m_iIntervalTime = iIntervalTime;
    }
}
