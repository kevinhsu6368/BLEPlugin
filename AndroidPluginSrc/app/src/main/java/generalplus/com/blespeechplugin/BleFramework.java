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
    private static final long SCAN_PERIOD = 5000;
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
                Log.e("tag", "gCurBluetoothDevice = null");
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

                if(i32ReconnectCounter > MAX_RECONNECT_NUM)
                {
                    connectHandler.removeCallbacks(runnableReconnect);
                    return;
                }

                mBluetoothLeService.connectDevice(gCurBluetoothDevice);
                connectHandler.postDelayed(this, RECONNECT_INTERVAL_TIME);
                Log.e("tag", "Reconnect");

                i32ReconnectCounter++;
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

    public synchronized void SetConnectState(boolean bConnected)
    {
        bConnectState = bConnected;
    }

    public  synchronized boolean GetConnectStat()
    {
        return bConnectState;
    }

    public BleFramework(Activity activity)
    {

	    this.mServiceConnection = new ServiceConnection()
        {
            public void onServiceConnected(ComponentName componentName, IBinder service)
            {
                mBluetoothLeService = ((BluetoothLeService.LocalBinder)service).getService();
                if (!mBluetoothLeService.initialize())
                {
                    Log.e(TAG, "onServiceConnected: Unable to initialize Bluetooth");
                }
	            else
                {
	                Log.d(TAG, "onServiceConnected: Success!");
                }
            }

            public void onServiceDisconnected(ComponentName componentName)
            {
                Log.d(TAG, "onServiceDisconnected: Bluetooth disconnected");
                mBluetoothLeService = null;
            }
        };

		mLeScanCallback = new BluetoothAdapter.LeScanCallback()
		{
            public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord)
            {
	            if (null == device.getName()) {
		            return;
	            }

	            for (int i = 0; i < mBluetoothLeService.listBTDevice.size(); i++) {
		            if (mBluetoothLeService.listBTDevice.get(i).m_BluetoothDevice.getAddress().equalsIgnoreCase(device.getAddress())) {
			            return;
		            }
	            }

	            BLEObj obj = new BLEObj();
	            obj.m_BluetoothDevice = device;
	            mBluetoothLeService.listBTDevice.add(obj);

                // 如果找到 C1 就結束掃描
                if(device.getName().startsWith(HandShake.Instance().BLE_Device_Name))
                {
                    Log.d(TAG, "scanLeDevice find : " + HandShake.Instance().BLE_Device_Name);
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    searchingDevice = false;
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidCompletePeripheralScan", "Success");
                }
            }
        };



        this.mGattUpdateReceiver = new BroadcastReceiver()
        {
            public void onReceive(Context context, Intent intent)
            {
                String action = intent.getAction();
	            Log.d(TAG, "mGattUpdateReceiver onReceive Action = " + action);
                if (ACTION_GATT_CONNECTED.equals(action))
                {
                    SetConnectState(true);
	                //bConnectState = true;
                    RECONNECT_INTERVAL_TIME = 10000;
                    LogFile.GetInstance().AddLogAndSave(true,"ACTION_GATT_CONNECTED");
                    Log.d(TAG, ("Connection estabilished with: " + BleFramework.this._mDeviceAddress));
                }
                else if (ACTION_GATT_DISCONNECTED.equals(action))
                {
                    SetConnectState(false);
	                //bConnectState = false;
                    LogFile.GetInstance().AddLogAndSave(true,"ACTION_GATT_DISCONNECTED");
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidDisconnect", "Success");
                    Log.d(TAG, "Connection lost");

                }
                else if (ACTION_GATT_SERVICES_DISCOVERED.equals(action))
                {
                    connectHandler.removeCallbacks(runnableReconnect);
                    i32ReconnectCounter = 0;
                    LogFile.GetInstance().AddLogAndSave(true,"ACTION_GATT_SERVICES_DISCOVERED");
                    Log.d(TAG, "Service discovered! Registering GattService ACTION_GATT_SERVICES_DISCOVERED");
                    //BleFramework.this.getGattService(BleFramework.this._mBluetoothLeService.getSupportedGattService());
                    Log.d(TAG, "Send BLEUnityMessageName_OnBleDidConnect success signal to Unity");
                    UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidConnect", "Success");
                }
                else if (ACTION_DATA_AVAILABLE.equals(action))
                {
                    LogFile.GetInstance().AddLogAndSave(true,"ACTION_DATA_AVAILABLE");
                    Log.d(TAG, "New Data received by the server");
                    //_dataRx = intent.getByteArrayExtra("EXTRA_DATA");
                    if (null != intent.getStringExtra(BluetoothLeService.READ_DATA))
                    {
                        String data = intent.getStringExtra(BluetoothLeService.READ_DATA);
                        Log.d(HandShake.Instance().Tag,"Recv ... String Data = " + data);
                        HandShake.Instance().OnRecvPacket(true, StringTools.HexToBytes(data));
                        LogFile.GetInstance().AddLogAndSave(true, "READ_DATA = " + data);
                        //UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidReceiveData", data);
                        if(HandShake.Instance().GetIsResponseMode()) // no response mode
                            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidReceiveData", data);
                    }
                }
                else if (AUTO_CONNECT.equals(action))
                {
                    LogFile.GetInstance().AddLogAndSave(true,"AUTO_CONNECT");
	                Log.d(TAG, "mGattUpdateReceiver AUTO_CONNECT");
	                i32ReconnectCounter = 0;
	                connectHandler.removeCallbacks(runnableReconnect);
	                connectHandler.postDelayed(runnableReconnect  , 0);
                }
                else if (BluetoothLeService.NEXT_RECONNECT.equals(action)) {
                    LogFile.GetInstance().AddLogAndSave(true,"NEXT_RECONNECT");
                    connectHandler.removeCallbacks(runnableReconnect);
                    if (i32ReconnectCounter > MAX_RECONNECT_NUM) {
                        return;
                    }
                    connectHandler.postDelayed(runnableReconnect, 0);
                }
            }
        };
        this._unityActivity = activity;
    }

	public void scanLeDevice(final boolean enable)
	{
        Log.d("BLEScan","scanLeDevice ..... " + Boolean.toString(enable));
		if (enable)
		{
			// Stops scanning after a pre-defined scan period.
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
                    if(searchingDevice)
                    {
                        Log.d(TAG, "scanLeDevice time out!");
                        Log.d("BLEScan","scanLeDevice .....  Device time out ! ( 60sec ) ");

                        //  没有掃到指定機,就再重掃
                        boolean isFindBLE = false;
                        for (int i = 0; i < mBluetoothLeService.listBTDevice.size(); ++i) {
                            BluetoothDevice bd = mBluetoothLeService.listBTDevice.get(i).m_BluetoothDevice;

                            if(bd.getName().startsWith(HandShake.Instance().BLE_Device_Name))
                            {
                                isFindBLE = true;
                                break;
                            }
                        }
                        if (isFindBLE) {
                            mBluetoothAdapter.stopLeScan(mLeScanCallback);
                            searchingDevice = false;
                            UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidCompletePeripheralScan", "Success");
                        }
                        else
                        {
                            // re scan
                            scanLeDevice(true);
                        }
                    }
				}
			},5*1000/* 最多掃5分鐘,掃到第一台緒C1就取消執行緒*/);//SCAN_PERIOD);
			searchingDevice = true;
			mBluetoothAdapter.startLeScan(mLeScanCallback);
		}
		else
		{
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
			searchingDevice = false;
		}
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

	public void _InitBLEFramework() {

        HandShake.Instance().Start();

        // kevin.hsu.add log to file
        if(isInitLogFile==false)
        {
            isInitLogFile = true;
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
            String sTime = sdf.format(Calendar.getInstance().getTime());
            String shortFileName = "AndroidPluginLog_" + sTime + ".txt";
            LogFile.GetInstance().SetFileName("BLE_Test", shortFileName);
        }
        LogFile.GetInstance().AddLogAndSave(true,"_InitBLEFramework");

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
		this.registerBleUpdatesReceiver();
		Intent gattServiceIntent = new Intent((Context)this._unityActivity, (Class)BluetoothLeService.class);
		this._unityActivity.bindService(gattServiceIntent, this.mServiceConnection, Context.BIND_AUTO_CREATE);

        Log.d(TAG, "onCreate: _mBluetoothAdapter correctly initialized");
        UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidInitialize", "Success");
    }

    public void _ScanForPeripherals() {
        if ( GetConnectStat() )
            return;

        LogFile.GetInstance().AddLogAndSave(true,"_ScanForPeripherals");
        Log.d(TAG, "_ScanForPeripherals: Launching scanLeDevice");
        connectHandler.removeCallbacks(runnableReconnect);
        mBluetoothLeService.disconnect();
        mBluetoothLeService.listBTDevice.clear();
		this.scanLeDevice(true);
    }

    public boolean _IsDeviceConnected() {
	    Log.d(TAG, "_IsDeviceConnected");
        return GetConnectStat() ;//this.bConnectState;
    }

    public boolean _SearchDevicesDidFinish() {
	    Log.d(TAG, "_SearchDevicesDidFinish");
        return !this.searchingDevice;
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
        LogFile.GetInstance().AddLogAndSave(true,"_ConnectPeripheralAtIndex: " + peripheralIndex);

        Log.d(TAG, ("_ConnectPeripheralAtIndex: " + peripheralIndex));
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

        LogFile.GetInstance().AddLogAndSave(true,"Try connect mDeviceAddress = " + mDeviceName);
	    Log.d("tag", "Try connect mDeviceAddress = " + mDeviceName);

	    gCurBluetoothDevice = mBluetoothLeService.listBTDevice.get(peripheralIndex).m_BluetoothDevice;

	    i32ReconnectCounter = 0;
	    connectHandler.removeCallbacks(runnableReconnect);
	    connectHandler.postDelayed(runnableReconnect  , 0);

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