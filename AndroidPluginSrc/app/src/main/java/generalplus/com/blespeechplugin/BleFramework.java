package generalplus.com.blespeechplugin;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.kevin.Tool.HandShake;
import com.kevin.Tool.LogFile;
import com.kevin.Tool.NetTools;
import com.kevin.Tool.StringTools;
import com.unity3d.player.UnityPlayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static android.content.Context.BLUETOOTH_SERVICE;
import static generalplus.com.blespeechplugin.BluetoothLeService.ACTION_DATA_AVAILABLE;
import static generalplus.com.blespeechplugin.BluetoothLeService.ACTION_GATT_CONNECTED;
import static generalplus.com.blespeechplugin.BluetoothLeService.ACTION_GATT_DISCONNECTED;
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
    private static final long SCAN_PERIOD = 15000;
	private static final String TAG;
    //private List<BluetoothDevice> listBTDevice = new ArrayList<BluetoothDevice>();
    private byte[] _dataRx = new byte[3];
    public static BluetoothLeService mBluetoothLeService;
    private Map<UUID, BluetoothGattCharacteristic> _map = new HashMap<UUID, BluetoothGattCharacteristic>();
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice _device;
    private String _mDeviceAddress;
    private String _mDeviceName;
    private boolean bConnectState = false;
    private boolean searchingDevice = false;
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
        intentFilter.addAction(ACTION_DATA_AVAILABLE);
        intentFilter.addAction(AUTO_CONNECT);
        intentFilter.addAction(NEXT_RECONNECT);
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
        HandShake.Instance().SetConnected(false);
        HandShake.Instance().Log2File("SetConnectState ( " + Boolean.toString(bConnected) + " ) ");
    }

    public  synchronized boolean GetConnectStat()
    {
        return bConnectState;
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
                }
	            else
                {
	                //Log.d(TAG, "onServiceConnected: Success!");
                    HandShake.Instance().Log2File("onServiceConnected ( ) ... Success ");
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

		mLeScanCallback = new BluetoothAdapter.LeScanCallback()
		{
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
            {
                HandShake.Instance().Log2File("mLeScanCallback.onLeScan( ) ... start");
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
                    SetConnectState(true);
                    HandShake.Instance().SetNotifyUnityConnected(false);
	                //bConnectState = true;
                    RECONNECT_INTERVAL_TIME = 10000;
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
                    //Log.d(TAG, "Connection lost");
                    HandShake.Instance().SetNotifyUnityConnected(false);
                }
                else if (ACTION_GATT_SERVICES_DISCOVERED.equals(action))
                {
                    HandShake.Instance().Log2File("mGattUpdateReceiver.onReceive( ) ... ACTION_GATT_SERVICES_DISCOVERED");
                    connectHandler.removeCallbacks(runnableReconnect);
                    i32ReconnectCounter = 0;
                    //LogFile.GetInstance().AddLogAndSave(true,"ACTION_GATT_SERVICES_DISCOVERED");
                    //Log.d(TAG, "Service discovered! Registering GattService ACTION_GATT_SERVICES_DISCOVERED");
                    //BleFramework.this.getGattService(BleFramework.this._mBluetoothLeService.getSupportedGattService());
                    //Log.d(TAG, "Send BLEUnityMessageName_OnBleDidConnect success signal to Unity");
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidConnect", "Success");

                    HandShake.Instance().SetNotifyUnityConnected(true);
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

    public long GetWaitForScan()
    {
        long waitForScan = 5*1000;
        if(iScanForPeripherals_Count % 4 == 3) // 每掃到第4次 , 下一次 delay 30 秒 , 否則 delay 5 秒
        {
            return 30*1000 ;
        }

        return waitForScan;
    }

    private long pre_ScanForPeripherals = 0;
    public long Get_pre_ScanForPeripherals()
    {
        return pre_ScanForPeripherals;
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
	public synchronized void scanLeDevice(final boolean enable)
	{

        HandShake.Instance().Log2File("scanLeDevice ( " + Boolean.toString(enable) + " ) ... start");
		if (enable)
		{

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

                            if(HandShake.Instance().CheckSDBBleDevice(device.getName()))  //bd.getName().startsWith(HandShake.Instance().BLE_Device_Name))
                            {
                                isFindBLE = true;
                                break;
                            }
                        }
                        HandShake.Instance().Log2File("scanLeDevice ( " + Boolean.toString(enable) + " ) ... time out - mBluetoothAdapter.stopLeScan(mLeScanCallback)  ");
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);

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
			},SCAN_PERIOD /* 最多掃5分鐘,掃到第一台緒C1就取消執行緒*/);//SCAN_PERIOD);
			//searchingDevice = true;
            SetSearchingDevice(true);
            AddScanForPeripheralsCount();
			mBluetoothAdapter.startLeScan(mLeScanCallback);
		}
		else
		{
            ResetScanForPeripheralsCount();
            SetSearchingDevice(false);
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
			//searchingDevice = false
		}

        HandShake.Instance().Log2File("scanLeDevice ( " + Boolean.toString(enable) + " ) ... end");
	}

	private void unregisterBleUpdatesReceiver() {
        Log.d(TAG, "unregisterBleUpdatesReceiver:");
        this._unityActivity.unregisterReceiver(this.mGattUpdateReceiver);
    }

    private void registerBleUpdatesReceiver() {
        Log.d(TAG, "registerBleUpdatesReceiver:");
        if (!this.mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, "registerBleUpdatesReceiver: WARNING: _mBluetoothAdapter is not enabled!");
        }
        Log.d(TAG, "registerBleUpdatesReceiver: registerReceiver");
        this._unityActivity.registerReceiver(this.mGattUpdateReceiver, BleFramework.makeGattUpdateIntentFilter());
    }

    private boolean isInitLogFile = false;

    private void CheckOpenBluetooth()
    {
        if(this.mBluetoothAdapter.isEnabled())
            return;

        // 未開啟 --> 自動開啟
        this.mBluetoothAdapter.enable();
    }

	public void _InitBLEFramework() {

        HandShake.Instance().Log2File("_InitBLEFramework ( ) ... start");
        HandShake.Instance().Start();



        // kevin.hsu.add log to file
        if(isInitLogFile==false)
        {
            isInitLogFile = true;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String sTime = sdf.format(Calendar.getInstance().getTime());
            String shortFileName = "AndroidPluginLog_" + sTime + ".txt";
            LogFile.GetInstance().SetFileName("BLE_Test2", shortFileName);
            LogFile.GetInstance().SetStopSave(false);
            LogFile.GetInstance().Start();
        }


        System.out.println("Android Executing: _InitBLEFramework");
        if (!this._unityActivity.getPackageManager().hasSystemFeature("android.hardware.bluetooth_le")) {
            Log.d(TAG, "onCreate: fail: missing FEATURE_BLUETOOTH_LE");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidInitialize", "Fail: missing FEATURE_BLUETOOTH_LE");
            return;
        }
        BluetoothManager mBluetoothManager = (BluetoothManager)this._unityActivity.getSystemService(BLUETOOTH_SERVICE);
        this.mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (this.mBluetoothAdapter == null) {
            Log.d(TAG, "onCreate: fail: _mBluetoothAdapter is null");
            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidInitialize", "Fail: Context.BLUETOOTH_SERVICE");
            return;
        }

        // 確保開啟藍芽功能
        CheckOpenBluetooth();


		this.registerBleUpdatesReceiver();
		Intent gattServiceIntent = new Intent((Context)this._unityActivity, (Class)BluetoothLeService.class);
		this._unityActivity.bindService(gattServiceIntent, this.mServiceConnection, Context.BIND_AUTO_CREATE);

        Log.d(TAG, "onCreate: _mBluetoothAdapter correctly initialized");
        UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidInitialize", "Success");

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
        long delta = (System.currentTimeMillis() - Get_pre_ScanForPeripherals());
        return (delta > GetSCAN_PERIOD());
    }

    public  synchronized  void _ScanForPeripherals()
    {
        HandShake.Instance().Log2File("_ScanForPeripherals ( ) ... start");
        if ( GetConnectStat() && CanScanForPeripherals()) // 限定3 秒內不能重新掃描,以免快連線前又被斷線
        {
            HandShake.Instance().Log2File("_ScanForPeripherals ( ) ... 限定3 秒內不能重新掃描,以免快連線前又被斷線 ,  return 掉不處理");
            return;
        }

        //pre_ScanForPeripherals = System.currentTimeMillis();
        LogFile.GetInstance().AddLogAndSave(true,"_ScanForPeripherals ( )");
        Log.d(HandShake.Instance().Tag, "_ScanForPeripherals ( )");
        connectHandler.removeCallbacks(runnableReconnect);
        mBluetoothLeService.disconnect();
        mBluetoothLeService.listBTDevice.clear();
		this.scanLeDevice(true);
        HandShake.Instance().Log2File("_ScanForPeripherals ( ) ... end");
    }

    public boolean _IsDeviceConnected() {
	    Log.d(TAG, "_IsDeviceConnected");
        return GetConnectStat() ;//this.bConnectState;
    }

    public boolean _SearchDevicesDidFinish() {
	    Log.d(TAG, "_SearchDevicesDidFinish");
        return !GetSearchingDevice();
    }

    public String _GetListOfDevices() {
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

    public boolean _ConnectPeripheralAtIndex(int peripheralIndex) {

        HandShake.Instance().OnGetServiceStart();
        HandShake.Instance().Log2File("_ConnectPeripheralAtIndex ( " + peripheralIndex + " ) .. start");
        //Log.d(HandShake.Instance().Tag, ("_ConnectPeripheralAtIndex: " + peripheralIndex));
        //LogFile.GetInstance().AddLogAndSave(true,"_ConnectPeripheralAtIndex: " + peripheralIndex);


	    mBluetoothLeService.disconnect();
	    mBluetoothLeService.cleanAddress();

	    try {
		    Thread.sleep(30);
	    } catch (InterruptedException e) {
		    // TODO Auto-generated catch block
		    e.printStackTrace();
	    }

	    mDeviceName = mBluetoothLeService.listBTDevice.get(peripheralIndex).m_BluetoothDevice.getName();
	    mDeviceAddress = mBluetoothLeService.listBTDevice.get(peripheralIndex).m_BluetoothDevice.getAddress();

        Log.d(HandShake.Instance().Tag, "Try connect device = " + mDeviceName + "( " + mDeviceAddress + " )");
        LogFile.GetInstance().AddLogAndSave(true, "Try connect device = " + mDeviceName + "( " + mDeviceAddress + " )");


	    gCurBluetoothDevice = mBluetoothLeService.listBTDevice.get(peripheralIndex).m_BluetoothDevice;

	    i32ReconnectCounter = 0;
	    connectHandler.removeCallbacks(runnableReconnect);
	    connectHandler.postDelayed(runnableReconnect  , 0);

        HandShake.Instance().Log2File("_ConnectPeripheralAtIndex ( " + peripheralIndex + " ) .. end");
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

    public void _SendData(byte[] data, int size) {
	    //mBluetoothLeService.WriteData(data);
        HandShake.Instance().PostPacket(data);
    }

    // Post Data
    public void _PostData(byte[] data,int size)
    {
        //mBluetoothLeService.WriteData(data);
        HandShake.Instance().PostPacket(data);

    }


    public void TestCommand(String cmd,String data)
    {

        switch (data)
        {
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
    }


    //  給 Unity 下 Log  到 Android 這邊顯示
    public void Log(String tag,String msg)
    {

        if(tag == null || tag.isEmpty())
            Log.d(TAG, msg);
        else if (tag.equals("TestCommand"))
        {
            TestCommand(tag, msg);
        }
        else
            Log.d(tag,msg);
    }

	static {
		TAG = BleFramework.class.getSimpleName();
	}

}