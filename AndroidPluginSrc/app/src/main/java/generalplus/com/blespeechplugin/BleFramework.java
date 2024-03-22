package generalplus.com.blespeechplugin;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.provider.Settings;
//import android.support.v4.content.ContextCompat;
//import androidx.core.content.ContextCompat;
//import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;

import com.kevin.Tool.BatteryTools;//.BatteryTools;
import com.kevin.Tool.FTPTools;
import com.kevin.Tool.HandShake;
import com.kevin.Tool.LogFile;
import com.kevin.Tool.NetTools;
import com.kevin.Tool.StringTools;
import com.kevin.Tool.SystemInfo;
import com.unity3d.player.UnityPlayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static android.content.Context.BLUETOOTH_SERVICE;
import static android.provider.Settings.Secure.LOCATION_MODE_BATTERY_SAVING;
import static android.provider.Settings.Secure.LOCATION_MODE_HIGH_ACCURACY;
import static android.provider.Settings.Secure.LOCATION_MODE_SENSORS_ONLY;
import static generalplus.com.blespeechplugin.BluetoothLeService.ACTION_DATA_AVAILABLE;
import static generalplus.com.blespeechplugin.BluetoothLeService.ACTION_GATT_CONNECTED;
import static generalplus.com.blespeechplugin.BluetoothLeService.ACTION_GATT_DISCONNECTED;
import static generalplus.com.blespeechplugin.BluetoothLeService.ACTION_GATT_RSSI;
import static generalplus.com.blespeechplugin.BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED;
import static generalplus.com.blespeechplugin.BluetoothLeService.AUTO_CONNECT;
import static generalplus.com.blespeechplugin.BluetoothLeService.NEXT_RECONNECT;

public class BleFramework{

    private Activity _unityActivity;
    private static volatile BleFramework _instance;
    public static final String BLEUnityMessageName_OnBleDidInitialize = "OnBleDidInitialize";
    public static final String BLEUnityMessageName_OnBleDidConnect = "OnBleDidConnect";
    public static final String BLEUnityMessageName_OnBleDidCompletePeripheralScan = "OnBleDidCompletePeripheralScan";
    public static final String BLEUnityMessageName_OnBleDidDisconnect = "OnBleDidDisconnect";
    public static final String BLEUnityMessageName_OnBleDidReceiveData = "OnBleDidReceiveData";
    private static final long SCAN_PERIOD = 10000;
	private static final String TAG;
    //private List<BluetoothDevice> listBTDevice = new ArrayList<BluetoothDevice>();
    private byte[] _dataRx = new byte[3];
    public static BluetoothLeService mBluetoothLeService;
    private Map<UUID, BluetoothGattCharacteristic> _map = new HashMap<UUID, BluetoothGattCharacteristic>();
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeScanner mScanner ;
    private BluetoothDevice _device;
    private String _mDeviceAddress;
    private String _mDeviceName;
    private boolean bConnectState = false;
    private boolean searchingDevice = false;
    private ScanSettings mScannerSetting = null;
    private ScanSettings mScannerSetting_mode_b = null;
    private ScanSettings mScannerSetting_mode_c = null;
    private ArrayList<ScanFilter> mScannerFilters = null;
    private ArrayList<ScanFilter> mScannerFilters_mode_b = null;
    private ArrayList<ScanFilter> mScannerFilters_mode_c = null;

    private ArrayList<String> mOldScanMode_SpecialMobilePhones = null;
    public synchronized boolean GetSearchingDevice()
    {
        return searchingDevice;
    }

    public  synchronized void SetSearchingDevice(boolean bSearching)
    {
        searchingDevice = bSearching;
        HandShake.Instance().Log2File("SetSearchingDevice ( " + Boolean.toString(bSearching) + " ) ");
    }

    private final ServiceConnection mServiceConnection;
    private final BroadcastReceiver mBleStateReceiver;
	private final BroadcastReceiver mGattUpdateReceiver;
    private BluetoothAdapter.LeScanCallback mLeScanCallback;
	public Handler mHandler = new Handler();
    private int RECONNECT_INTERVAL_TIME = 10000; // (10000 / 10000 ms)
	// -------------------------------Device Control Activity-------------------------------------------
	
	//private final static String TAG = "gpble";
    private String mDeviceName = "";
    private String mDeviceAddress = "";
    //private ExpandableListView mGattServicesList;
    //private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

	BluetoothDevice gCurBluetoothDevice = null;

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_GATT_CONNECTED);
        intentFilter.addAction(ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(ACTION_GATT_RSSI);
        intentFilter.addAction(ACTION_DATA_AVAILABLE);
        intentFilter.addAction(AUTO_CONNECT);
        intentFilter.addAction(NEXT_RECONNECT);
        return intentFilter;
    }

    private static IntentFilter makeBleStateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        return intentFilter;
    }

    int MAX_RECONNECT_NUM = 2;
    int i32ReconnectCounter = 0;
    Handler connectHandler = new Handler();
    Runnable runnableReconnect = new Runnable() {

        @Override
        public void run() {
            if(null == gCurBluetoothDevice) {
                //Log.e("tag", "gCurBluetoothDevice = null");
                HandShake.Instance().Log2File("BleFramework - runnableReconnect : gCurBluetoothDevice = null ");
                return;
            }

            if (!GetConnectStat())//!bConnectState)
            {
                mBluetoothLeService.cleanAddress();

                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                // [2017/10/17] 暫時拿掉 重連 功能
                /*
                if(i32ReconnectCounter > MAX_RECONNECT_NUM)
                {
                    connectHandler.removeCallbacks(runnableReconnect);
                    HandShake.Instance().Log2File("BleFramework - runnableReconnect : (i32ReconnectCounter > MAX_RECONNECT_NUM(2次))");
                    return;
                }
                */

                HandShake.Instance().Log2File("mBluetoothLeService.connectDevice(gCurBluetoothDevice)");
                mBluetoothLeService.connectDevice(gCurBluetoothDevice);

                // [2017/10/17] 暫時拿掉 重連 功能
                /*
                HandShake.Instance().Log2File("connectHandler.postDelayed(this, RECONNECT_INTERVAL_TIME = 10 sec)");
                connectHandler.postDelayed(this, RECONNECT_INTERVAL_TIME);
                //Log.e("tag", "Reconnect");
                HandShake.Instance().Log2File("BleFramework - runnableReconnect : connectHandler.postDelayed(10sec)");
                i32ReconnectCounter++;
                */
            }
        }
    };


    // ble-framework
    public static BleFramework getInstance(Activity activity)
    {
	    if (_instance == null) {
		    synchronized (BleFramework.class) {
			    if (_instance == null) {
				    Log.d(TAG, "BleFramework: Creation of _instance");
				    _instance = new BleFramework(activity);
			    }
		    }
	    }
	    return _instance;
    }

    public static BleFramework getInstance()
    {
        return _instance;
    }

    public synchronized void SetConnectState(boolean bConnected)
    {
        bConnectState = bConnected;
        HandShake.Instance().SetConnected(bConnected);
        HandShake.Instance().Log2File("SetConnectState ( " + Boolean.toString(bConnected) + " ) ");

        /*
        if(bConnected)
        {


            if(mBluetoothLeService.setMTU(244))
            {
                Log.d(TAG,"setMTU = 244 ... ok");
            }
            else
            {
                Log.d(TAG,"setMTU = 244 ... fail");
            }


        }

         */
    }

    public  synchronized boolean GetConnectStat()
    {
        return bConnectState;
    }

    public void OnEnableBluetooth()
    {
        if (this.mScanner == null)
            this.mScanner = this.mBluetoothAdapter.getBluetoothLeScanner();
    }

    public BleFramework(Activity activity)
    {
        NetTools.Start_CheckNetworkState(activity);
	    this.mServiceConnection = new ServiceConnection()
        {
            public void onServiceConnected(ComponentName componentName, IBinder service)
            {
                HandShake.Instance().Log2File("onServiceConnected ( ) ... start ");


                mBluetoothLeService = ((BluetoothLeService.LocalBinder)service).getService();
                if (!mBluetoothLeService.initialize())
                {
                    //Log.e(TAG, "onServiceConnected: Unable to initialize Bluetooth");
                    HandShake.Instance().Log2File("onServiceConnected ( ) ... Unable to initialize Bluetooth ");
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidInitialize", "Fail: Unable to initialize Bluetooth");
                }
	            else
                {
	                //Log.d(TAG, "onServiceConnected: Success!");
                    HandShake.Instance().Log2File("onServiceConnected ( ) ... Success ");
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidInitialize", "Success");
                }

                HandShake.Instance().Log2File("onServiceConnected ( ) ... end ");
            }

            public void onServiceDisconnected(ComponentName componentName)
            {
                //Log.d(TAG, "onServiceDisconnected: Bluetooth disconnected");
                HandShake.Instance().Log2File("onServiceDisconnected ( ) ");
                mBluetoothLeService = null;
            }
        };

        // mScanner = mBluetoothAdapter.getBluetoothLeScanner();

		mLeScanCallback = new BluetoothAdapter.LeScanCallback()
		{
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
            {
                HandShake.Instance().Log2File("mLeScanCallback.onLeScan( ) ... start");

                // 檢查有沒有 獲取藍牙設備名稱的權限
                if(CheckDevieNamePermission() == false)
                {
                    return;
                }

	            if (null == device.getName()) {

                    HandShake.Instance().Log2File("mLeScanCallback.onLeScan( ) ...  null == device.getName() ... so ... return");
		            return;
	            }

	            for (int i = 0; i < mBluetoothLeService.listBTDevice.size(); i++) {
		            if (mBluetoothLeService.listBTDevice.get(i).m_BluetoothDevice.getAddress().equalsIgnoreCase(device.getAddress()))
		            {
                        HandShake.Instance().Log2File("mLeScanCallback.onLeScan( ) ...  repeat device ( " + device.getName() + " ) ...  so ... return");
			            return;
		            }
	            }

	            BLEObj obj = new BLEObj();
	            obj.m_BluetoothDevice = device;
	            mBluetoothLeService.listBTDevice.add(obj);

                String devName = device.getName();
                byte [] bs = devName.getBytes();
                String hexDevName = HandShake.bytesToHexString(bs);
                HandShake.Instance().Log2File( String.format("find dev = %s , Hex = %s",devName,hexDevName));


                // 如果找到 C1 就結束掃描
                if(HandShake.Instance().CheckSDBBleDevice(device.getName())) // device.getName().startsWith(HandShake.Instance().BLE_Device_Name))
                {
                    //Log.d(TAG, "scanLeDevice find : " + HandShake.Instance().BLE_Device_Name);
                    HandShake.Instance().Log2File("mLeScanCallback.onLeScan( ) ... find c1 and notify unity(OnBleDidCompletePeripheralScan:Success)");

                    //mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    //searchingDevice = false;
                    //SetSearchingDevice(false);
                    scanLeDevice(false);
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidCompletePeripheralScan", "Success");
                }
                HandShake.Instance().Log2File("mLeScanCallback.onLeScan( ) ... end");
            }
        };

        this.mBleStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR);
                    switch (state) {
                        case BluetoothAdapter.STATE_OFF:
                            break;
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            break;
                        case BluetoothAdapter.STATE_ON:
                            OnEnableBluetooth();
                            break;
                        case BluetoothAdapter.STATE_TURNING_ON:
                            break;
                    }
                }
            }
        };



        this.mGattUpdateReceiver = new BroadcastReceiver()
        {
            public void onReceive(Context context, Intent intent)
            {
                String action = intent.getAction();
	            //Log.d(TAG, "mGattUpdateReceiver onReceive Action = " + action);
                HandShake.Instance().Log2File("mGattUpdateReceiver.onReceive( ) ... start");
                if (ACTION_GATT_CONNECTED.equals(action))
                {
                    HandShake.Instance().Log2File("mGattUpdateReceiver.onReceive( ) ... ACTION_GATT_CONNECTED");
                    SetConnectState(false); // kevin.hsu.2020/08/18 預設為 false , 當連接成功時, 不變更 state 為 false , 而在之後取得服務後 , 才設定為連線成功並通知 unity 已連線成功
                    HandShake.Instance().SetNotifyUnityConnected(false);
                    HandShake.Instance().OnGetServiceStart();
                    HandShake.Instance().ResetTimeOut();
	                //bConnectState = true;
                    RECONNECT_INTERVAL_TIME = 15000;
                    //LogFile.GetInstance().AddLogAndSave(true,"ACTION_GATT_CONNECTED");
                    //Log.d(TAG, ("Connection estabilished with: " + BleFramework.this._mDeviceAddress));
                }
                else if (ACTION_GATT_DISCONNECTED.equals(action))
                {
                    HandShake.Instance().Log2File("mGattUpdateReceiver.onReceive( ) ... ACTION_GATT_DISCONNECTED");
                    SetConnectState(false);
	                //bConnectState = false;
                    //LogFile.GetInstance().AddLogAndSave(true,"ACTION_GATT_DISCONNECTED");
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidDisconnect", "Success");
                    HandShake.Instance().Log2File("mGattUpdateReceiver.onReceive( ) ... ACTION_GATT_DISCONNECTED ... notify unity : disconnect ");
                    //Log.d(TAG, "Connection lost");
                    HandShake.Instance().SetNotifyUnityConnected(false);
                }
                else if(ACTION_GATT_RSSI.equals(action))
                {
                    int rssi = intent.getIntExtra("value",0);
                    String sRSSI = String.format("%d",rssi);
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidReadRSSI", sRSSI);
                }
                else if (ACTION_GATT_SERVICES_DISCOVERED.equals(action))
                {
                    SetConnectState(true);
                    HandShake.Instance().OnGetServiceFinished(true);
                    HandShake.Instance().Log2File("mGattUpdateReceiver.onReceive( ) ... ACTION_GATT_SERVICES_DISCOVERED");
                    connectHandler.removeCallbacks(runnableReconnect);
                    i32ReconnectCounter = 0;
                    //LogFile.GetInstance().AddLogAndSave(true,"ACTION_GATT_SERVICES_DISCOVERED");
                    //Log.d(TAG, "Service discovered! Registering GattService ACTION_GATT_SERVICES_DISCOVERED");
                    //getGattService(BleFramework.this._mBluetoothLeService.getSupportedGattService());
                    //Log.d(TAG, "Send BLEUnityMessageName_OnBleDidConnect success signal to Unity");
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidConnect", "Success");

                    HandShake.Instance().SetNotifyUnityConnected(true);

                    // ============ [kevin.hsu] 2020.07.31. add SDB-BT 的機型在連線後,取得主服務後,開啟 接收特微符的通知功能. (ps. IOS 也要做相同處理 )
                    // 不能找到服務,就馬上丟通知 , 否則藍牙晶片會錯亂
                    // BleFramework.getInstance().EnableCharReadNotify(true);

                }
                else if (ACTION_DATA_AVAILABLE.equals(action))
                {
                    //LogFile.GetInstance().AddLogAndSave(true,"ACTION_DATA_AVAILABLE");
                    //Log.d(TAG, "New Data received by the server");
                    HandShake.Instance().Log2File("mGattUpdateReceiver.onReceive( ) ... ACTION_DATA_AVAILABLE");
                    //_dataRx = intent.getByteArrayExtra("EXTRA_DATA");
                    if (null != intent.getStringExtra(BluetoothLeService.READ_DATA))
                    {
                        String data = intent.getStringExtra(BluetoothLeService.READ_DATA);
                        //Log.d(HandShake.Instance().Tag,"Recv ... String Data = " + data);
                        HandShake.Instance().OnRecvPacket(true, StringTools.HexToBytes(data));
                        //HandShake.Instance().Log2File("READ_DATA = " + StringTools.HexToBytes(data));
                        //LogFile.GetInstance().AddLogAndSave(true, "READ_DATA = " + data);
                        //UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidReceiveData", data);
                        if(data.length() < 4)
                            return;
                        if(HandShake.Instance().GetIsResponseMode()) // no response mode
                            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidReceiveData", data);
                    }
                    else if (null != intent.getStringExtra(BluetoothLeService.WRITE_DATA))
                    {
                        String data = intent.getStringExtra(BluetoothLeService.WRITE_DATA);
                        HandShake.Instance().Log2File("WRITE_DATA = " + data);
                    }
                    else
                    {
                        HandShake.Instance().Log2File("unknow data");
                    }
                }
                else if (AUTO_CONNECT.equals(action))
                {
                    //LogFile.GetInstance().AddLogAndSave(true,"AUTO_CONNECT");
	                //Log.d(TAG, "mGattUpdateReceiver AUTO_CONNECT");
                    HandShake.Instance().Log2File("mGattUpdateReceiver.onReceive( ) ... AUTO_CONNECT");
	                i32ReconnectCounter = 0;

                    HandShake.Instance().Log2File("connectHandler.removeCallbacks(runnableReconnect)");
                    connectHandler.removeCallbacks(runnableReconnect);

                    HandShake.Instance().Log2File("connectHandler.postDelayed(runnableReconnect  , 10000 = 10 sec)");
	                connectHandler.postDelayed(runnableReconnect  , 10000);//0);  // kevin hsu , delay 500 ms
                }
                else if (BluetoothLeService.NEXT_RECONNECT.equals(action)) {
                    //LogFile.GetInstance().AddLogAndSave(true,"NEXT_RECONNECT");
                    HandShake.Instance().Log2File("mGattUpdateReceiver.onReceive( ) ... NEXT_RECONNECT");
                    HandShake.Instance().Log2File("connectHandler.removeCallbacks(runnableReconnect)");
                    connectHandler.removeCallbacks(runnableReconnect);
                    if (i32ReconnectCounter > MAX_RECONNECT_NUM)
                    {
                        HandShake.Instance().Log2File("if (i32ReconnectCounter > MAX_RECONNECT_NUM = 2) return ");
                        return;
                    }
                    HandShake.Instance().Log2File("connectHandler.postDelayed(runnableReconnect, 10000 = 10 sec)");
                    connectHandler.postDelayed(runnableReconnect, 10000);//100);  // kevin hsu , delay 1000 ms
                }
                HandShake.Instance().Log2File("mGattUpdateReceiver.onReceive( ) ... end");
            }
        };
        this._unityActivity = activity;
    }

    private long iScanForPeripherals_Count = 0;

    public synchronized void AddScanForPeripheralsCount() {
        iScanForPeripherals_Count++;
        HandShake.Instance().Log2File("iScanForPeripherals_Count = " + iScanForPeripherals_Count);
    }

    public synchronized void ResetScanForPeripheralsCount()
    {
        iScanForPeripherals_Count = 0;
        HandShake.Instance().Log2File("iScanForPeripherals_Count = " + iScanForPeripherals_Count);
    }

    public long GetScanForPeripheralsCount() {
        return iScanForPeripherals_Count;
    }

    public long GetSCAN_PERIOD()
    {
        int period = 10*1000;
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

    public long GetWaitForScan()
    {
        long waitForScan = 2*1000;
        if(iScanForPeripherals_Count % 4 == 3) // 每掃到第4次 , 下一次 delay 30 秒 , 否則 delay 2  秒
        {
            // return 30*1000 ;
        }

        return waitForScan;
    }

    private long pre_ScanForPeripherals = 0;
    public long Get_pre_ScanForPeripherals()
    {
        return pre_ScanForPeripherals;
    }

    public void DoStopScan()
    {
        boolean isOpenBLE = false;
        if(this.mBluetoothAdapter !=null)
            isOpenBLE = this.mBluetoothAdapter.isEnabled();

        // android sdk 版本小等於 21 時, 只能使用舊式掃描API
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP)
        {
            if(this.mBluetoothAdapter != null && isOpenBLE)
                this.mBluetoothAdapter.stopLeScan(this.mLeScanCallback);
            return;
        }
        else
        {
            if ( true )
            {
                if(this.mBluetoothAdapter != null && isOpenBLE)
                    this.mBluetoothAdapter.stopLeScan(this.mLeScanCallback);


                if( this.mScanner != null && isOpenBLE)
                    this.mScanner.stopScan(this.mScannerCallback);

                return;
            }

        }

        // android sdk 版本大等於 21 時
        if(this.scanMode == ScanMode.mode_a)
        {
            int iflag = (int)this.iScanForPeripherals_Count%9;
            if (iflag>=0 && iflag <=2) //  0,1,2   跑 mode-b
            {
                if( this.mScanner != null && isOpenBLE)
                    this.mScanner.stopScan(this.mScannerCallback);
            }
            else if (iflag>=3 && iflag <=5) // 3,4,5   跑 mode-d ( old api )
            {
                if(this.mBluetoothAdapter != null && isOpenBLE)
                    this.mBluetoothAdapter.stopLeScan(this.mLeScanCallback);
            }
            else // 6,7,8   跑 mode-c
            {
                if( this.mScanner != null && isOpenBLE)
                    this.mScanner.stopScan(this.mScannerCallback);
            }
        }
        else if(this.scanMode == ScanMode.mode_b) // new api
        {
            if( this.mScanner != null && isOpenBLE)
                this.mScanner.stopScan(this.mScannerCallback);
        }
        else if(this.scanMode == ScanMode.mode_c)  // new api
        {
            if( this.mScanner != null && isOpenBLE)
                this.mScanner.stopScan(this.mScannerCallback);
        }
        else if(this.scanMode == ScanMode.mode_d)  // old api
        {
            if(this.mBluetoothAdapter != null && isOpenBLE)
                this.mBluetoothAdapter.stopLeScan(this.mLeScanCallback);
        }

        /*
        if(!this.bNewScanMode)
        {
            this.mBluetoothAdapter.stopLeScan(this.mLeScanCallback);
        }
        else
        {
            if( this.mScanner == null)
                return;
            this.mScanner.stopScan(this.mScannerCallback);
        }

        //this.mBluetoothAdapter.stopLeScan(this.mLeScanCallback);
        //this.mScanner.stopScan(this.mScannerCallback);

        /*
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
        {
            if( this.mScanner == null)
                return;

            if(this.mBluetoothAdapter.isOffloadedFilteringSupported())
                this.mScanner.stopScan(this.mScannerCallback);
            else
                this.mBluetoothAdapter.stopLeScan(this.mLeScanCallback);
        }
        else {
            if(this.mBluetoothAdapter == null)
                return ;
            this.mBluetoothAdapter.stopLeScan(this.mLeScanCallback);
        }
        */
    }

    // 掃描模式
    public enum ScanMode
    {
        /* 自動模式 (default ):
                             (1) . 判段-OldScanAPI手機清單是否有本手機型號 ? Yes -- > 使用 Mode-D
                             (2) . No --> 使用 Mode-B 掃描
                                   .       --> Moed-B 掃描逾時 30 秒 未找到設備
                             (3) . No --> 使用 Mode-D 掃描
                                   .       --> Moed-D 掃描逾時 30 秒 未找到設備
                             (4) . No  --> 使用 Mode-D 掃描
                                   .       --> Moed-D 掃描逾時 30 秒 未找到設備
                             (5). 回到  (2)
        * */
        mode_a,
        /* 新 掃描 API + (名稱 / UUID ) 過濾功能      , 設定 : 積極掃描 */
        mode_b,
        /* 新 掃描 API + (沒有 )                過濾功能   , 設定 : 積極掃描 */
        mode_c,
        /* 舊 掃描 API */
        mode_d
    }

    private String curScanMode = "";
    // 預設
    private  ScanMode scanMode = ScanMode.mode_a;

    public boolean bNewScanMode = true;
    public void DoStartScan()
    {
        PackageManager pm = this._unityActivity.getPackageManager();
        // 檢查是否開啟位置檢限
        if(Build.VERSION.SDK_INT > 30) // 31.32: Android 12 ; 33: Android 13 ; 34:Android 14
        {


            //if(ContextCompat.checkSelfPermission(this._unityActivity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_DENIED )
            if(pm.checkPermission(Manifest.permission.BLUETOOTH_SCAN,this._unityActivity.getPackageName()) == PackageManager.PERMISSION_DENIED)
            {
                return;
            }
        }
        else
        {
            //if(ContextCompat.checkSelfPermission(this._unityActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED )
            if(pm.checkPermission(Manifest.permission.ACCESS_FINE_LOCATION,this._unityActivity.getPackageName()) == PackageManager.PERMISSION_DENIED)
            {
                return;
            }
        }



        // kevin.hsu . 調整如下
        boolean isOpenBLE = false;
        if(this.mBluetoothAdapter != null)
        {
            isOpenBLE = this.mBluetoothAdapter.isEnabled();
        }

        // android sdk 版本小等於 21 時, 只能使用舊式掃描API
        if(Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP)
        {
            if(this.mBluetoothAdapter == null)
                return;

            this.mBluetoothAdapter.stopLeScan(this.mLeScanCallback);
            return;
        }

        // 若沒有 mScanner 再獲取一次
        if(null == this.mScanner)
        {
            this.mScanner = mBluetoothAdapter.getBluetoothLeScanner(); // 可能沒有開啟藍牙服務

            if(null == this.mScanner)
                return;
        }

        if(isOpenBLE == false)
            return;

        HandShake.Instance().OnStartScan();

        // android sdk 版本大 於 21 時, 使用以下
        // 1. Mode A : 自動模式  :
        //                     (1) . 判段-OldScanAPI手機清單是否有本手機型號 ? Yes -- > 使用 Mode-D
        //                     (2) . No --> 使用 Mode-B 掃描
        //                           .       --> Moed-B 掃描逾時 30 秒 未找到設備 , 每10秒計數1次 : 1 , 2 , 3 ,  --> 當 4 時 ,  改為 Mode-D
        //                     (3) . No --> 使用 Mode-D 掃描
        //                           .       --> Moed-D 掃描逾時 30 秒 未找到設備 , 每10秒計數1次 : 4 , 5 , 6 ,  --> 當 7 時 ,  改為 Mode-C
        //                     (4) . No  --> 使用 Mode-C 掃描
        //                           .       --> Moed-C 掃描逾時 30 秒 未找到設備 , 每10秒計數1次 : 7 , 8 , 9 ,  --> 當 10 時 ,  改為 Mode-D
        //                     (5). 回到  (2)
        // 2. Mode B : 新 掃描 API + (名稱 / UUID ) 過濾功能      , 設定 : 積極掃描
        // 3. Mode B : 新 掃描 API + (沒有 )                過濾功能   , 設定 : 積極掃描
        // 4. Mode B : 舊 掃描 API
        if(this.scanMode == ScanMode.mode_a)
        {
            int iflag = (int)this.iScanForPeripherals_Count%9;

            if (iflag>=0 && iflag <=2) //  0,1,2             ----> mode - b.
            {
                this.mScanner.startScan(this.mScannerFilters_mode_b, this.mScannerSetting, this.mScannerCallback);
                curScanMode = "scan_mode_b";
            }
            else if (iflag>=3 && iflag <=5) // 3,4,5     ----> mode - d
            {
                this.mBluetoothAdapter.startLeScan(this.mLeScanCallback);
                curScanMode = "scan_mode_d";
            }
            else // 6,7,8                                                          ----> mode - c
            {
                this.mScanner.startScan(this.mScannerFilters_mode_c, this.mScannerSetting, this.mScannerCallback);
                curScanMode = "scan_mode_c";
            }
        }
        else if(this.scanMode == ScanMode.mode_b)
        {
            this.mScanner.startScan(this.mScannerFilters_mode_b, this.mScannerSetting, this.mScannerCallback);
            curScanMode = "scan_mode_b";
        }
        else if(this.scanMode == ScanMode.mode_c)
        {
            this.mScanner.startScan(this.mScannerFilters_mode_c, this.mScannerSetting, this.mScannerCallback);
            curScanMode = "scan_mode_c";
        }
        else if(this.scanMode == ScanMode.mode_d)
        {
            this.mBluetoothAdapter.startLeScan(this.mLeScanCallback);
            curScanMode = "scan_mode_d";
        }




        //this.mBluetoothAdapter.startLeScan(this.mLeScanCallback);

        /*
        if(this.mOldScanMode_SpecialMobilePhones.contains(Build.MODEL) || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
        {
            bNewScanMode = false;
        }

        if(!bNewScanMode)
        {
            this.mBluetoothAdapter.startLeScan(this.mLeScanCallback);
        }
        else
        {
            if(this.mScanner == null)
            {
                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    this._unityActivity.startActivityForResult(enableBtIntent, 0);//REQUEST_ENABLE_BT);
                }
                return;
            }
            this.mScanner.startScan(this.mScannerFilters, this.mScannerSetting, this.mScannerCallback);
        }
*/
        /*
             if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            {
                if(this.mScanner == null)
                {
                    if (!mBluetoothAdapter.isEnabled()) {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        this._unityActivity.startActivityForR       esult(enableBtIntent, 0);//REQUEST_ENABLE_BT);
                    }
                    return;
                }

                if(iScanForPeripherals_Count > 3)
                {
                    this.mBluetoothAdapter.startLeScan(this.mLeScanCallback);
                }
                else
                {
                    if(this.mBluetoothAdapter.isOffloadedFilteringSupported())
                        this.mScanner.startScan(this.mScannerFilters, this.mScannerSetting, this.mScannerCallback);
                    else
                        this.mBluetoothAdapter.startLeScan(this.mLeScanCallback);
                }

            }
            else {
                this.mBluetoothAdapter.startLeScan(this.mLeScanCallback);
            }
            */
    }
/*
    private synchronized boolean CanScanForPeripherals()
    {
        long delta = (System.currentTimeMillis() - pre_ScanForPeripherals);
        return (delta > GetSCAN_PERIOD());
    }

    public long GetSCAN_PERIOD()
    {
        return mBluetoothLeService.GetSCAN_PERIOD();
    }
*/

    public boolean CheckBluetoothEnablePermission()
    {
        PackageManager pm = this._unityActivity.getPackageManager();
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) // API=33 以後, 不允許對藍牙 開關
        {
            return false;
        }
        else if(Build.VERSION.SDK_INT > Build.VERSION_CODES.R /* = 30*/ ) // 31,32 : android 12 需要 Manifest.permission#BLUETOOTH_CONNECT
        {
            //if(ContextCompat.checkSelfPermission(_unityActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED )
            if(pm.checkPermission(Manifest.permission.BLUETOOTH_CONNECT,this._unityActivity.getPackageName()) == PackageManager.PERMISSION_DENIED)
            {
                return false;
            }
        }
        else
        {
            //if(ContextCompat.checkSelfPermission(_unityActivity, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_DENIED )
            if(pm.checkPermission(Manifest.permission.BLUETOOTH_ADMIN,this._unityActivity.getPackageName()) == PackageManager.PERMISSION_DENIED)
            {
                return false;
            }
        }
        return true;
    }

    // 檢查是否有取得設備名稱的權限
    public boolean CheckDevieNamePermission()
    {
        PackageManager pm = this._unityActivity.getPackageManager();
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.R /* = 30*/ ) // 31,32 : android 12 需要 Manifest.permission#BLUETOOTH_CONNECT
        {
            //if(ContextCompat.checkSelfPermission(_unityActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_DENIED )
            if(pm.checkPermission(Manifest.permission.BLUETOOTH_CONNECT,this._unityActivity.getPackageName()) == PackageManager.PERMISSION_DENIED)
            {
                return false;
            }
        }
        else
        {
            //if(ContextCompat.checkSelfPermission(_unityActivity, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_DENIED )
            if(pm.checkPermission(Manifest.permission.BLUETOOTH,this._unityActivity.getPackageName()) == PackageManager.PERMISSION_DENIED)
            {
                return false;
            }
        }
        return true;
    }

	public synchronized void scanLeDevice(final boolean enable)
	{

        HandShake.Instance().Log2File("scanLeDevice ( " + Boolean.toString(enable) + " ) ... start");
		if (enable)
		{
            CheckOpenBluetooth(); // 檢查沒開藍芽的話,幫它開啟來
			// Stops scanning after a pre-defined scan period.
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run()
                {
                    if(GetSearchingDevice()) //;searchingDevice)
                    {

                        HandShake.Instance().Log2File("scanLeDevice ( " + Boolean.toString(enable) + " ) ... time out - start ");

                        //  没有掃到指定機,就再重掃
                        boolean isFindBLE = false;
                        for (int i = 0; i < mBluetoothLeService.listBTDevice.size(); ++i) {
                            BluetoothDevice device = mBluetoothLeService.listBTDevice.get(i).m_BluetoothDevice;
                            if(device == null)
                                continue;

                            // 檢查有沒有 獲取藍牙設備名稱的權限
                            if(CheckDevieNamePermission() == false)
                            {
                                continue;
                            }

                            String devName = device.getName();

                            if(devName == null || devName.isEmpty())
                                continue;
                            byte [] bs = devName.getBytes();
                            String hexDevName = HandShake.bytesToHexString(bs);
                            HandShake.Instance().Log2File( String.format("dev - %d = %s , Hex = %s",(i+1),devName,hexDevName));
                            if(HandShake.Instance().CheckSDBBleDevice(device.getName()))  //bd.getName().startsWith(HandShake.Instance().BLE_Device_Name))
                            {
                                isFindBLE = true;
                                break;
                            }
                        }
                        HandShake.Instance().Log2File("scanLeDevice ( " + Boolean.toString(enable) + " ) ... time out - mBluetoothAdapter.stopLeScan(mLeScanCallback)  ");
                        //mBluetoothAdapter.stopLeScan(mLeScanCallback);
                        DoStopScan();

                        if (isFindBLE) {
                            //mBluetoothAdapter.stopLeScan(mLeScanCallback);
                            //searchingDevice = false;
                            SetSearchingDevice(false);
                            ResetScanForPeripheralsCount();
                            //scanLeDevice(false);

                            HandShake.Instance().Log2File("scanLeDevice ( " + Boolean.toString(enable) + " ) ... time out - is scan find C1 , and notify unity ");
                            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidCompletePeripheralScan", "Success");
                        }
                        else
                        {

                            //scanLeDevice(true);

                            //AddScanForPeripheralsCount();
                            long sleepTick = GetWaitForScan();
                            HandShake.Instance().Log2File("scanLeDevice ( " + Boolean.toString(enable) + " ) ... time out - and no find c1 ,  sleep  : " + sleepTick + " ms" );
                            mHandler.postDelayed(new Runnable() {
                                @Override
                                public void run()
                                {
                                    if(!GetConnectStat()) {
                                        HandShake.Instance().Log2File("scanLeDevice ( " + Boolean.toString(enable) + " ) ... time out - and no find c1 , to re scan : call scanLeDevice(true) ");
                                        SetSearchingDevice(true);
                                        scanLeDevice(true);
                                    }
                                }
                            },sleepTick);

                        }
                    }
				}
			},SCAN_PERIOD /* 最多掃5分鐘,掃到第一台緒C1就取消執行緒*/);
			//  下面直接掃描 , 然後 等 (SCAN_PERIOD) 10 秒後 ,
            //                            上面程式碼 檢查  ble 清單 (有資料的話)
            //
			//searchingDevice = true;
            SetSearchingDevice(true);
            AddScanForPeripheralsCount();

            this.DoStartScan();


//            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) // API 23 - Android 6
//            {
//                this.mScanner.startScan(mScannerFilters,mScannerSetting,mScannerCallback);
//            }
//            else
//            {
//                mBluetoothAdapter.startLeScan(mLeScanCallback);
//            }
		}
		else
		{
            ResetScanForPeripheralsCount();
            SetSearchingDevice(false);
			//mBluetoothAdapter.stopLeScan(mLeScanCallback);
            DoStopScan();
			//searchingDevice = false
		}

        HandShake.Instance().Log2File("scanLeDevice ( " + Boolean.toString(enable) + " ) ... end");
	}

    ScanCallback mScannerCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            HandShake.Instance().Log2File("mScanCallback.onScanResult( ) ... start");
            BluetoothDevice device = result.getDevice();

            if(device == null)
            {
                HandShake.Instance().Log2File("mScanCallback.onScanResult( ) ...  device == null ... so ... return");
                return;
            }

            // 檢查有沒有 獲取藍牙設備名稱的權限
            if(CheckDevieNamePermission() == false)
            {
                return;
            }

            String devName = device.getName();

            if (null == devName || devName.isEmpty())
            {
                HandShake.Instance().Log2File("mScanCallback.onScanResult( ) ...  null == device.getName() ... so ... return");
                return;
            }

            for (int i = 0; i < mBluetoothLeService.listBTDevice.size(); i++) {
                if (mBluetoothLeService.listBTDevice.get(i).m_BluetoothDevice.getAddress().equalsIgnoreCase(device.getAddress()))
                {
                    HandShake.Instance().Log2File("mScanCallback.onScanResult( ) ...  repeat device ( " + device.getName() + " ) ...  so ... return");
                    return;
                }
            }

            BLEObj obj = new BLEObj();
            obj.m_BluetoothDevice = device;
            mBluetoothLeService.listBTDevice.add(obj);

            byte [] bs = devName.getBytes();
            String hexDevName = HandShake.bytesToHexString(bs);
            HandShake.Instance().Log2File( String.format("find dev = %s , Hex = %s",devName,hexDevName));


            // 如果找到 C1 就結束掃描
            if(HandShake.Instance().CheckSDBBleDevice(device.getName())) // device.getName().startsWith(HandShake.Instance().BLE_Device_Name))
            {
                //Log.d(TAG, "scanLeDevice find : " + HandShake.Instance().BLE_Device_Name);
                HandShake.Instance().Log2File("mScanCallback.onScanResult( ) ... find c1 and notify unity(OnBleDidCompletePeripheralScan:Success)");

                //mBluetoothAdapter.stopLeScan(mLeScanCallback);
                //searchingDevice = false;
                //SetSearchingDevice(false);
                scanLeDevice(false);
                UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidCompletePeripheralScan", "Success");
            }
            HandShake.Instance().Log2File("mScanCallback.onScanResult( ) ... end");

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            HandShake.Instance().Log2File("mScanCallback.onBatchScanResults( ) ... count = " + (results == null ? 0 : results.size()));
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            HandShake.Instance().Log2File("mScanCallback.onScanFailed( ) ... err = " + errorCode);
        }
    };

	private void unregisterBleUpdatesReceiver() {
        Log.d(TAG, "unregisterBleUpdatesReceiver:");
        this._unityActivity.unregisterReceiver(this.mGattUpdateReceiver);
        BatteryTools.Instance().Close();
    }

    private void registerBleUpdatesReceiver() {
        Log.d(TAG, "registerBleUpdatesReceiver:");
        if (!this.mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "registerBleUpdatesReceiver: WARNING: _mBluetoothAdapter is not enabled!");
        }
        Log.d(TAG, "registerBleUpdatesReceiver: registerReceiver");
        this._unityActivity.registerReceiver(this.mGattUpdateReceiver, BleFramework.makeGattUpdateIntentFilter());
    }

    private void registerBleStateReceiver()
    {
        Log.d(TAG, "registerBleStateReceiver:");
        if (!this.mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "registerBleStateReceiver: WARNING: _mBluetoothAdapter is not enabled!");
        }
        Log.d(TAG, "registerBleStateReceiver: registerReceiver");

        // BluetoothAdapter.ACTION_STATE_CHANGED
        this._unityActivity.registerReceiver(this.mBleStateReceiver, BleFramework.makeBleStateIntentFilter());
    }

    private void unregisterBleStateReceiver() {
        Log.d(TAG, "unregisterBleStateReceiver:");
        this._unityActivity.unregisterReceiver(this.mBleStateReceiver);

    }

    private boolean isInitLogFile = false;

	private void CheckOpenGPS(Context context)
    {
        Intent GPSIntent = new Intent();
        GPSIntent.setClassName("com.android.settings",
                "com.android.settings.widget.SettingsAppWidgetProvider");
        GPSIntent.addCategory("android.intent.category.ALTERNATIVE");
        GPSIntent.setData(Uri.parse("custom:3"));
        try {
            PendingIntent.getBroadcast(context, 0, GPSIntent, 0).send();
        } catch (PendingIntent.CanceledException e) {
            e.printStackTrace();
        }
    }


    public boolean isLocationEnabled(Context context) {
        int locationMode = 0;
        String locationProviders;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            try {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
                return false;
            }

            String sMode = "";
            switch (locationMode)
            {
                case LOCATION_MODE_SENSORS_ONLY:
                    sMode = "SENSORS_ONLY";
                    break;
                case LOCATION_MODE_BATTERY_SAVING:
                    sMode = "BATTERY_SAVING";
                    break;
                case LOCATION_MODE_HIGH_ACCURACY:
                    sMode = "HIGH_ACCURACY";
                    break;
                default:
                    sMode = "OFF";
                    break;
            }
            Log.d("[GPS] ","Get GPS Status ... " + sMode);

            return (locationMode  != Settings.Secure.LOCATION_MODE_OFF);

        }else{
            locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }


    }

    public boolean setMTU(int len)
    {
        return mBluetoothLeService.setMTU(len);
    }

    private void CheckOpenBluetooth()
    {
        if(this.mBluetoothAdapter.isEnabled())
            return;

        // 未開啟 --> 自動開啟
        if(this.CheckBluetoothEnablePermission())
            this.mBluetoothAdapter.enable();
    }

    public void _InitBLEFramework(String mode,String devices)
    {
        HandShake.Instance().SetDevices(devices);
        _InitBLEFramework(mode);
    }


    // 初始化 - 帶掃描資料 (for android )
    public void _InitBLEFramework(String mode) {

        Log.d(TAG, "_InitBLEFramework( "+ mode + " ) ... ");

        SetScanMode(mode);

            /*
            // 取得  old api  掃描模式
            String oldScanAPI_MobilePhones = json.getString("oldScanAPI_MobilePhones");
            String [] lsMobilePhones = oldScanAPI_MobilePhones.split(",");
            this.mOldScanMode_SpecialMobilePhones = new ArrayList<String>();
            for(int i=0;i<lsMobilePhones.length;i++)
            {
                String str = lsMobilePhones[i];
                if(str == null || str.isEmpty())
                    continue;
                this.mOldScanMode_SpecialMobilePhones.add(str);
                Log.d(TAG,"...  add old scan api for mobile  = " + mode);
            }
            */


        //} catch (JSONException e) {
        //    e.printStackTrace();
        //}


        _InitBLEFramework();

    }

	@TargetApi(Build.VERSION_CODES.M)
    public void _InitBLEFramework() {

        HandShake.Instance().Log2File("_InitBLEFramework ( ) ... start");
        HandShake.Instance().Start();

        BatteryTools.Instance().Init(this._unityActivity);
        SystemInfo.Init(this._unityActivity);

        // kevin.hsu.add log to file
        if(isInitLogFile==false)
        {
            isInitLogFile = true;
            //SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            //String sTime = sdf.format(Calendar.getInstance().getTime());
            //String shortFileName = SystemInfo.GetPhoneModle() + "_BLE_Log_" + sTime + ".txt";
            //LogFile.GetInstance().SetFileName("BLE_Test2", shortFileName);
            LogFile.GetInstance().SetStopSave(true);
            //LogFile.GetInstance().Start();

            // report log to ftp

            //LogFile.GetInstance().ReportToFTP();
        }


        //System.out.println("Android Executing: _InitBLEFramework");
        // 檢查手機是支援 BLE
        if (!this._unityActivity.getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
           // Log.d(TAG, "onCreate: fail: missing FEATURE_BLUETOOTH_LE");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidInitialize", "Fail: missing FEATURE_BLUETOOTH_LE");
            HandShake.Instance().Log2File("_InitBLEFramework ( ) ... onCreate: fail: missing FEATURE_BLUETOOTH_LE .... end");
            return;
        }
        BluetoothManager mBluetoothManager = (BluetoothManager)this._unityActivity.getSystemService(BLUETOOTH_SERVICE);

        if(mBluetoothManager == null)
        {
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidInitialize", "Fail: mBluetoothManager == null");
            return;
        }

        this.mBluetoothAdapter = mBluetoothManager.getAdapter();


        if (this.mBluetoothAdapter == null) {
           // Log.d(TAG, "onCreate: fail: _mBluetoothAdapter is null");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidInitialize", "Fail: Context.BLUETOOTH_SERVICE");
            HandShake.Instance().Log2File("_InitBLEFramework ( ) ... onCreate: fail: _mBluetoothAdapter is null  ..... end");
            return;
        }

        this.mOldScanMode_SpecialMobilePhones = new ArrayList<String>();
        //this.mOldScanMode_SpecialMobilePhones.add("Redmi Note 8");
        //this.mOldScanMode_SpecialMobilePhones.add("Redmi Note 8T");



        mScannerSetting = new ScanSettings.Builder()
                //退到后台时设置扫描模式为低功耗
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY) //.SCAN_MODE_LOW_POWER)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .build();


        mScannerFilters = new ArrayList<ScanFilter>();

        // for mode-b : 有 filter - name/uuid
        mScannerFilters_mode_b = new ArrayList<ScanFilter>();

        for(String s : HandShake.Instance().lsSDB_Ble_DeviceName)
        {
            ScanFilter filter = new ScanFilter.Builder().setDeviceName(s).build();
            mScannerFilters.add(filter);
            mScannerFilters_mode_b.add(filter);
        }

        /*
        ScanFilter filter1 = new ScanFilter.Builder().setDeviceName("C1       ").build();
        ScanFilter filter0 = new ScanFilter.Builder().setDeviceName("C2       ").build();
        ScanFilter filter2 = new ScanFilter.Builder().setDeviceName("SDB-BT").build();
        ScanFilter filter3 = new ScanFilter.Builder().setDeviceName("DB-2-Pro").build();
        ScanFilter filter4 = new ScanFilter.Builder().setDeviceName("DB-2").build();
        //ScanFilter filter3 = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID.nameUUIDFromBytes(new byte[]{0x0D,0x18}))).build();
        //ScanFilter filter4 = new ScanFilter.Builder().setServiceUuid(new ParcelUuid(UUID.nameUUIDFromBytes(new byte[]{0x18,0x0D}))).build();

        mScannerFilters.add(filter0);
        mScannerFilters.add(filter1);
        mScannerFilters.add(filter2);
        mScannerFilters.add(filter3);
        mScannerFilters.add(filter4);

        mScannerFilters_mode_b.add(filter0);
        mScannerFilters_mode_b.add(filter1);
        mScannerFilters_mode_b.add(filter2);
        mScannerFilters_mode_b.add(filter3);
        mScannerFilters_mode_b.add(filter4);
*/

        // for mode-c : 空的 filter
        mScannerFilters_mode_c = new ArrayList<ScanFilter>();

        // 開啟 GPS 功能
        //this.CheckOpenGPS((Context)this._unityActivity);

        // 確保開啟藍芽功能 , 若沒有開啟的話 , Android 11 以下版本將可以自動開啟藍牙服務 ; Android 12 以上版本不允許制啟用服務 , 這時應該跳出提示請求開啟藍牙服務
        CheckOpenBluetooth();

        HandShake.Instance().Log2File("_InitBLEFramework ( ) ... Check Open Bluetooth ");

        // 如果沒有開啟藍牙服務 , mScanner 會等於 null
        this.mScanner = mBluetoothAdapter.getBluetoothLeScanner();

		this.registerBleUpdatesReceiver();
		this.registerBleStateReceiver();
		Intent gattServiceIntent = new Intent((Context)this._unityActivity, (Class)BluetoothLeService.class);
		this._unityActivity.bindService(gattServiceIntent, this.mServiceConnection, Context.BIND_AUTO_CREATE);

        //Log.d(TAG, "onCreate: _mBluetoothAdapter correctly initialized");

        // [2017/10/18]. adj , Init Success 應該要放在 拿到 BluetoothLeService 後才算 ok
        // UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidInitialize", "Success");

        HandShake.Instance().Log2File("_InitBLEFramework ( ) ... end");

    }

    /*
    public long Get_pre_ScanForPeripherals()
    {
        return mBluetoothLeService.Get_pre_ScanForPeripherals();
    }
*/
    //private long pre_ScanForPeripherals = 0;
    private synchronized boolean CanScanForPeripherals()
    {
        long delta = (System.currentTimeMillis() - Get_pre_ScanForPeripherals()); // 誤差單位為豪秒
        Log.d("[ScanBle]" , ".......... CanScanForPeripherals  Delta -  ms : " + String.valueOf(delta) );
        return (delta > GetSCAN_PERIOD());
    }

    public  synchronized  void _ScanForPeripherals()
    {
        HandShake.Instance().Log2File("unity  call  :   _ScanForPeripherals ( ) ... start");
        if ( GetConnectStat() && !CanScanForPeripherals()) // 限定3 秒內不能重新掃描,以免快連線前又被斷線
        {
            HandShake.Instance().Log2File("_ScanForPeripherals ( ) ... 限定3 秒內不能重新掃描,以免快連線前又被斷線 ,  return 掉不處理");
            return;
        }

        //pre_ScanForPeripherals = System.currentTimeMillis();
       // LogFile.GetInstance().AddLogAndSave(true,"_ScanForPeripherals ( )");
       // Log.d(HandShake.Instance().Tag, "_ScanForPeripherals ( )");

        connectHandler.removeCallbacks(runnableReconnect);

        //mBluetoothLeService.disconnect();    [2017/10/18]. Kevin.Hsu , adj , 不要執行 , 不然進去會 收到廣播 DISCONNEECT 然後又跑 ReConnect  , 會把 scan , connect 打在一起

        mBluetoothLeService.listBTDevice.clear();


		this.scanLeDevice(true);

        HandShake.Instance().Log2File("unity  call  :   _ScanForPeripherals ( ) ... end");
    }

    public  void _GetRSSI()
    {
        if(mBluetoothLeService.listBTDevice.size()==0)
            return  ;

        mBluetoothLeService.ReadRSSI();
    }

    public String _GetScanMode()
    {
        return this.curScanMode;
    }

    public  String _GetUUID()
    {
        if(mBluetoothLeService.listBTDevice.size()==0)
            return "";

        String mac = mBluetoothLeService.listBTDevice.get(curPeripheralIndex).m_BluetoothDevice.getAddress();
        return mac;
    }

    public boolean _IsDeviceConnected() {
	    Log.d(TAG, "_IsDeviceConnected");
        return GetConnectStat() ;//this.bConnectState;
    }

    public boolean _SearchDevicesDidFinish() {
	    Log.d(TAG, "_SearchDevicesDidFinish");
        return !GetSearchingDevice();
    }

    public String _GetWifiIP()
    {
        return NetTools.GetWifiIP();
    }

    public String _GetListOfDevices()
    {
        String jsonListString;
        if (mBluetoothLeService.listBTDevice.size() > 0) {
	        Log.d(TAG, "_GetListOfDevices");
            String[] uuidsArray = new String[mBluetoothLeService.listBTDevice.size()];
            for (int i = 0; i < mBluetoothLeService.listBTDevice.size(); ++i) {
                BluetoothDevice bd = mBluetoothLeService.listBTDevice.get(i).m_BluetoothDevice;
                //uuidsArray[i] = bd.getAddress();
	            uuidsArray[i] = bd.getName();
            }
            Log.d(TAG, "_GetListOfDevices: Building JSONArray");
            JSONArray uuidsJSON = new JSONArray(Arrays.asList(uuidsArray));
            Log.d(TAG, "_GetListOfDevices: Building JSONObject");
            JSONObject dataUuidsJSON = new JSONObject();
            try {
                Log.d(TAG, "_GetListOfDevices: Try inserting uuuidsJSON array in the JSONObject");
                dataUuidsJSON.put("data", (Object)uuidsJSON);
            }
            catch (JSONException e) {
                Log.e(TAG, "_GetListOfDevices: JSONException");
                LogFile.GetInstance().AddLogAndSave(true, "_GetListOfDevices: JSONException");
                e.printStackTrace();
            }
            jsonListString = dataUuidsJSON.toString();
            Log.d(TAG, ("_GetListOfDevices: sending found devices in JSON: " + jsonListString));
            LogFile.GetInstance().AddLogAndSave(true,"_GetListOfDevices: sending found devices in JSON: " + jsonListString);
        } else {
            jsonListString = "NO DEVICE FOUND";
            Log.d(TAG, "_GetListOfDevices: no device was found");
            LogFile.GetInstance().AddLogAndSave(true,"_GetListOfDevices: no device was found");
        }


        return jsonListString;
    }

    /*
        @breif 目前連接的 BLE 設備 device index
        */
    int curPeripheralIndex = 0;

    public boolean _ConnectPeripheralAtIndex(int peripheralIndex) {

        //HandShake.Instance().OnGetServiceStart();
        HandShake.Instance().Log2File("unity  call  :   _ConnectPeripheralAtIndex ( " + peripheralIndex + " ) .. start");
        //Log.d(HandShake.Instance().Tag, ("_ConnectPeripheralAtIndex: " + peripheralIndex));
        //LogFile.GetInstance().AddLogAndSave(true,"_ConnectPeripheralAtIndex: " + peripheralIndex);


	    //mBluetoothLeService.disconnect();
	    //mBluetoothLeService.cleanAddress();

	    try {
		    Thread.sleep(30);
	    } catch (InterruptedException e) {
		    // TODO Auto-generated catch block
		    e.printStackTrace();
	    }

        // 檢查有沒有 獲取藍牙設備名稱的權限
        if(CheckDevieNamePermission() == false)
        {
            return false;
        }

	    mDeviceName = mBluetoothLeService.listBTDevice.get(peripheralIndex).m_BluetoothDevice.getName();
	    mDeviceAddress = mBluetoothLeService.listBTDevice.get(peripheralIndex).m_BluetoothDevice.getAddress();

        curPeripheralIndex = peripheralIndex;
        //HandShake.Instance().AllowPolling = true; // 預設會做 pooling  的動作
        if(mDeviceName.contains("C1"))
        {
            mBluetoothLeService.sdb_ble_type = BluetoothLeService.SDB_BLE_TYPE.C1;
            HandShake.Instance().SetNRF52832(false);
            HandShake.Instance().AllowPolling = true;
        }
        else if(mDeviceName.contains("sdb Bt dongle"))
        {
            mBluetoothLeService.sdb_ble_type = BluetoothLeService.SDB_BLE_TYPE.USB_DONGLE;
            HandShake.Instance().SetNRF52832(false);
            HandShake.Instance().AllowPolling = true;
        }
        else if(mDeviceName.contains("DB-2-Pro"))
        {
            mBluetoothLeService.sdb_ble_type = BluetoothLeService.SDB_BLE_TYPE.DB2_Pro;
            HandShake.Instance().AllowPolling = false; // 不做 pooling  的動作
            HandShake.Instance().SetNRF52832(true);
        }
        else if(mDeviceName.contains("DB-2"))
        {
            mBluetoothLeService.sdb_ble_type = BluetoothLeService.SDB_BLE_TYPE.DB2;
            HandShake.Instance().AllowPolling = false; // 不做 pooling  的動作
            HandShake.Instance().SetNRF52832(false);
        }
        else if(mDeviceName.contains("SDB-BT"))
        {
            mBluetoothLeService.sdb_ble_type = BluetoothLeService.SDB_BLE_TYPE.SDB_BT;
            HandShake.Instance().AllowPolling = false; // 不做 pooling  的動作
            HandShake.Instance().SetNRF52832(true);
        }
        else if(mDeviceName.contains("C2"))
        {
            mBluetoothLeService.sdb_ble_type = BluetoothLeService.SDB_BLE_TYPE.SDB_BT;
            HandShake.Instance().AllowPolling = false; // 不做 pooling  的動作
            HandShake.Instance().SetNRF52832(true);
        }

        Log.d(HandShake.Instance().Tag, "Try connect device = " + mDeviceName + "( " + mDeviceAddress + " )");
        LogFile.GetInstance().AddLogAndSave(true, "Try connect device = " + mDeviceName + "( " + mDeviceAddress + " )");


	    gCurBluetoothDevice = mBluetoothLeService.listBTDevice.get(peripheralIndex).m_BluetoothDevice;

	    i32ReconnectCounter = 0;
	    connectHandler.removeCallbacks(runnableReconnect);
	    connectHandler.postDelayed(runnableReconnect  , 0);

        HandShake.Instance().Log2File("unity  call  :   _ConnectPeripheralAtIndex ( " + peripheralIndex + " ) .. end");
        return true;
    }

    public boolean _ConnectPeripheral(String peripheralID) {
        Log.d(TAG, ("_ConnectPeripheral: " + peripheralID));
        return false;
    }

    public byte[] _GetData() {
        Log.d(TAG, "_GetData: ");
        return this._dataRx;
    }

    public void _SendData(byte[] data, int size)
    {
        HandShake.Instance().Log2File("unity  call  :   _SendData (   ) .. start");
	    //mBluetoothLeService.WriteData(data);

        HandShake.Instance().PostPacket(data,size);

        HandShake.Instance().Log2File("unity  call  :   _SendData (  ) .. end");
    }

    // Post Data
    public void _PostData(byte[] data,int size)
    {
        HandShake.Instance().Log2File("unity  call  :   _PostData (   ) .. start");
        //mBluetoothLeService.WriteData(data);
        HandShake.Instance().PostPacket(data,size);
        HandShake.Instance().Log2File("unity  call  :   _PostData (   ) .. start");

    }

    public void _ReportLog()
    {
        LogFile.GetInstance().ReportToFTP();
    }

    public void SetScanMode(String data)
    {
        switch (data)
        {
            case "scan_mode_a":
            {
                this.scanMode = ScanMode.mode_a;
                Log.d(HandShake.Instance().Tag,".... scan_mode_a");
                break;
            }
            case "scan_mode_b":
            {
                this.scanMode = ScanMode.mode_b;
                Log.d(HandShake.Instance().Tag,".... scan_mode_b");
                break;
            }
            case "scan_mode_c":
            {
                this.scanMode = ScanMode.mode_c;
                Log.d(HandShake.Instance().Tag,".... scan_mode_c");
                break;
            }
            case "scan_mode_d":
            {
                this.scanMode = ScanMode.mode_d;
                Log.d(HandShake.Instance().Tag,".... scan_mode_d");
                break;
            }
        }
    }

    public String TestCommand(String cmd,String data)
    {
        String rs = "";

        // ----  帶資料特殊指令

        if(data.startsWith("oldScanMode4SpecialMobile")) // oldScanMode4SpecialMobile,mobilePhone1,mobilePhone2,mobilePhone3,...
        {
            String [] ls = data.split(",");
            if (ls.length >1 )
            {
                mOldScanMode_SpecialMobilePhones.clear();
                for(int i=1;i<ls.length;i++)
                {
                    mOldScanMode_SpecialMobilePhones.add(ls[i]);
                }
            }
        }


        // 指令列表
        switch (data)
        {
            case "scan_mode_a":
            case "scan_mode_b":
            case "scan_mode_c":
            case "scan_mode_d":
            {
                SetScanMode(data);
                break;
            }
            case "oldScanMode4SpecialMobile":
            {
                break;
            }
            case "isOpenGPS":
            {
                boolean isOpenGPS = this.isLocationEnabled(this._unityActivity);
                rs = isOpenGPS ? "true" : "false";
            }
            break;
            case "VersionCode":
            {
                try
                {
                    PackageInfo pInfo = this._unityActivity.getPackageManager().getPackageInfo(this._unityActivity.getPackageName(), 0);
                    int versionCode = pInfo.versionCode;
                    rs = String.valueOf(versionCode);
                }
                catch (PackageManager.NameNotFoundException e)
                {
                    e.printStackTrace();
                }
            }
                break;
            case "OpenGPSSetting":
                this._unityActivity.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                break;
            case "ExitAPP":
                _unityActivity.finish();
                //activity.finish();
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(0);
                break;
            case "00_00":
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().SetSimulate(false)");
                HandShake.Instance().SetSimulate(false);
                break;
            case "00_01":
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().SetSimulate(true)");
                HandShake.Instance().SetSimulate(true);
                break;
            case "01" : //  BLE - GBX - ON ... 模擬 BLE - Pooling Response : GBX ON
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().Simulator_Recv_BLE_Pooling(true)");
                HandShake.Instance().Simulator_Recv_BLE_Pooling(true);
                break;
            case "02": // BLE - GBX - OFF ... 模擬 BLE - Pooling Response : GBX OFF
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().Simulator_Recv_BLE_Pooling(false)");
                HandShake.Instance().Simulator_Recv_BLE_Pooling(false);
                break;
            case "03": // BLE - Response_Cmd_Index_OK
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().Simulator_Recv_BLE_Response_Cmd_Index(true)");
                HandShake.Instance().Simulator_Recv_BLE_ResponseCmdIndex(true);
                break;
            case "04": // BLE - Response_Cmd_Index_Error
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().Simulator_Recv_BLE_Response_Cmd_Index(false)");
                HandShake.Instance().Simulator_Recv_BLE_ResponseCmdIndex(false);
                break;
            case "05": // BLE - Send_Cmd_Packet
                Log.d(HandShake.Instance().Tag,"BLE - Send_Cmd_Packet");
                HandShake.Instance().Simulator_Recv_BLE_SendCmdPacket();
                break;
            case "10_100": // Set_APP_Poolling_100
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().SetSendPoolingIntervalTick(100)");
                HandShake.Instance().SetSendPoolingIntervalTick(100);
                break;
            case "10_200": // Set_APP_Poolling_200
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().SetSendPoolingIntervalTick(200)");
                HandShake.Instance().SetSendPoolingIntervalTick(200);
                break;
            case "10_300": // Set_APP_Poolling_300
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().SetSendPoolingIntervalTick(300)");
                HandShake.Instance().SetSendPoolingIntervalTick(300);
                break;
            case "10_500": // Set_APP_Poolling_500
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().SetSendPoolingIntervalTick(500)");
                HandShake.Instance().SetSendPoolingIntervalTick(500);
                break;
            case "10_1000": // Set_APP_Poolling_1000
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().SetSendPoolingIntervalTick(1000)");
                HandShake.Instance().SetSendPoolingIntervalTick(1000);
                break;
            case "10_2000": // Set_APP_Poolling_2000
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().SetSendPoolingIntervalTick(2000)");
                HandShake.Instance().SetSendPoolingIntervalTick(2000);
                break;
            case "10_5000": // Set_APP_Poolling_5000
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().SetSendPoolingIntervalTick(5000)");
                HandShake.Instance().SetSendPoolingIntervalTick(5000);
                break;
            case "10_10000": // Set_APP_Poolling_10000
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().SetSendPoolingIntervalTick(10000)");
                HandShake.Instance().SetSendPoolingIntervalTick(10000);
                break;
            case "11" : //  Set_APP_ResponseMode
                Log.d(cmd,"HandShake.Instance().SetResponseMode(true)");
                HandShake.Instance().SetResponseMode(true);
                break;
            case "12": // Set_APP_NoResponseMode
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().SetResponseMode(false)");
                HandShake.Instance().SetResponseMode(false);
                break;

            case "20_10": // Set_APP_SendCmdInterval_50
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().setSendCmdIntervalTick(10)");
                HandShake.Instance().setSendCmdIntervalTick(10);
                break;

            case "20_50": // Set_APP_SendCmdInterval_50
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().setSendCmdIntervalTick(50)");
                HandShake.Instance().setSendCmdIntervalTick(50);
                break;

            case "20_100": // Set_APP_SendCmdInterval_100
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().setSendCmdIntervalTick(100)");
                HandShake.Instance().setSendCmdIntervalTick(100);
                break;
            case "20_200": // Set_APP_SendCmdInterval_200
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().setSendCmdIntervalTick(200)");
                HandShake.Instance().setSendCmdIntervalTick(200);
                break;
            case "20_300": // Set_APP_SendCmdInterval_300
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().setSendCmdIntervalTick(300)");
                HandShake.Instance().setSendCmdIntervalTick(300);
                break;
            case "20_500": // Set_APP_SendCmdInterval_500
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().setSendCmdIntervalTick(500)");
                HandShake.Instance().setSendCmdIntervalTick(500);
                break;
            case "30_05": //  Command Packet 重送次數 5 以後會斷線重連
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().setReSendPacket_count_To_Disconnect(5)");
                HandShake.Instance().setReSendPacket_count_To_Disconnect(5);
            case "30_10": // Command Packet 重送次數 10 以後會斷線重+ 連
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().setReSendPacket_count_To_Disconnect(10)");
                HandShake.Instance().setReSendPacket_count_To_Disconnect(10);
            case "30_50": //  Command Packet 重送次數 50 以後會斷線重連
                Log.d(HandShake.Instance().Tag,"HandShake.Instance().setReSendPacket_count_To_Disconnect(50)");
                HandShake.Instance().setReSendPacket_count_To_Disconnect(50);
                break;

        }
        return rs;
    }

    public void EnableCharReadNotify(boolean bEnable)
    {
        if(!mBluetoothLeService.isNotifySuccess)
            mBluetoothLeService.EnableCharReadNotify(bEnable);
       // mBluetoothLeService.setCharacteristicNotification();
    }

    //  給 Unity 下 Log  到 Android 這邊顯示
    public String Log(String tag,String msg)
    {
        String rs = "";
        if(tag == null || tag.isEmpty())
            Log.d(TAG, msg);
        else if (tag.equals("TestCommand"))
        {
            rs = TestCommand(tag, msg);
        }
        else
            Log.d(tag,msg);

        return rs;
    }

	static {
		TAG = BleFramework.class.getSimpleName();
	}

}