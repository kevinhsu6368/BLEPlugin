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

            if (!bConnectState) {

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
	                bConnectState = true;
                    RECONNECT_INTERVAL_TIME = 10000;
                    LogFile.GetInstance().AddLogAndSave(true,"ACTION_GATT_CONNECTED");
                    Log.d(TAG, ("Connection estabilished with: " + BleFramework.this._mDeviceAddress));
                }
                else if (ACTION_GATT_DISCONNECTED.equals(action))
                {
	                bConnectState = false;
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
                        LogFile.GetInstance().AddLogAndSave(true,"READ_DATA:\r\n" + intent.getStringExtra(BluetoothLeService.READ_DATA));
                        UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidReceiveData", intent.getStringExtra(BluetoothLeService.READ_DATA));
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
		if (enable)
		{
			// Stops scanning after a pre-defined scan period.
			mHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					Log.d(TAG, "scanLeDevice time out!");
					mBluetoothAdapter.stopLeScan(mLeScanCallback);
					searchingDevice = false;
					UnityPlayer.UnitySendMessage("BLEControllerEventHandler", "OnBleDidCompletePeripheralScan", "Success");
				}
			}, SCAN_PERIOD);
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
        LogFile.GetInstance().AddLogAndSave(true,"_ScanForPeripherals");
        Log.d(TAG, "_ScanForPeripherals: Launching scanLeDevice");
        connectHandler.removeCallbacks(runnableReconnect);
        mBluetoothLeService.disconnect();
        mBluetoothLeService.listBTDevice.clear();
		this.scanLeDevice(true);
    }

    public boolean _IsDeviceConnected() {
	    Log.d(TAG, "_IsDeviceConnected");
        return this.bConnectState;
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




    //  給 Unity 下 Log  到 Android 這邊顯示
    public void Log(String tag,String msg)
    {
        if(tag == null || tag=="")
            Log.d(TAG, msg);
        else
            Log.d(tag,msg);
    }

	static {
		TAG = BleFramework.class.getSimpleName();
	}

}