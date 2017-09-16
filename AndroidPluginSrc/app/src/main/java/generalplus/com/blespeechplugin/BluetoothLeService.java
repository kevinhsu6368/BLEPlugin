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
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;

import com.kevin.Tool.HandShake;

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
    private	BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;
    public boolean m_bAck = false;
    private String m_strVersion = "";
    
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    
    boolean Write_Characteristic_Status = false;

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
    
    public Handler mHandler = new Handler();
    // Stops scanning after 20 seconds.
    private static final long SCAN_PERIOD = 20000;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    
    public ArrayList<BLEObj> listBTDevice = new ArrayList<BLEObj>();
    private String m_strAckType = "";
    private boolean m_bActiveDiscoonnect = false;
    private int m_iIntervalTime = -1;
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            Log.e(TAG, "onConnectionStateChange received: " + status + " newState = " + newState);
            switch (status) {
                case BluetoothGatt.GATT_SUCCESS:
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        intentAction = ACTION_GATT_CONNECTED;
                        broadcastUpdate(intentAction);
                        Log.i(TAG, "Connected to GATT server.");
                        // Attempts to discover services after successful connection.
                        Log.e(TAG, "Attempting to start service discovery:" +
                                gatt.discoverServices());
                        return;
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        if (false == m_bActiveDiscoonnect) {
                        	doReConnect();
						}
                        else {
                        	close();
                        	m_bActiveDiscoonnect = false;
                        	intentAction = ACTION_GATT_DISCONNECTED;
                            mConnectionState = STATE_DISCONNECTED;
                            Log.e(TAG, "Disconnected from GATT server.");
                            broadcastUpdate(intentAction);
                        }
                        return;
                    }
                    break;
                case 0x01:
                    Log.e(TAG, "GATT CONN L2C FAILURE");
                    break;
                case 0x08:
                    Log.e(TAG, "GATT CONN TIMEOUT");
                    break;
                case 0x13:
                    Log.e(TAG, "GATT CONN TERMINATE PEER USER");
                    break;
                case 0x16:
                    Log.e(TAG, "GATT CONN TERMINATE LOCAL HOST");
                    break;
                case 0x3E:
                    Log.e(TAG, "GATT CONN FAIL ESTABLISH");
                    break;
                case 0x22:
                    Log.e(TAG, "GATT CONN LMP TIMEOUT");
                    break;
                case 0x0100:
                    Log.e(TAG, "GATT CONN CANCEL ");
                    break;
                case 0x0085:
                    Log.e(TAG, "GATT ERROR"); // Device not reachable
                    break;
                default:
                    Log.e(TAG, "UNKNOWN (" + status + ")");
                    break;
            }

            intentAction = ACTION_GATT_DISCONNECTED;
            mConnectionState = STATE_DISCONNECTED;
            Log.e(TAG, "Disconnected from GATT server.");
            broadcastUpdate(intentAction);

            try {  
                Thread.sleep(100);  
            } catch (InterruptedException e) {  
                e.printStackTrace();  
            }
            
            intentAction = NEXT_RECONNECT;
            broadcastUpdate(intentAction);
            Log.e(TAG, "NEXT_RECONNECT");
        }
        
        private void doReConnect() {
        	String intentAction = ACTION_GATT_DISCONNECTED;
            mConnectionState = STATE_DISCONNECTED;
            Log.e(TAG, "Disconnected from GATT server.");
            broadcastUpdate(intentAction);
            
            if(null != m_strOldAddress) {
            	for (int i = 0; i < listBTDevice.size(); i++) {
					if (listBTDevice.get(i).m_BluetoothDevice.getAddress().equalsIgnoreCase(m_strOldAddress)) {
						Log.e(TAG, "Auto connecting.");    			
						intentAction = AUTO_CONNECT;
	                    broadcastUpdate(intentAction);
						//connectDevice(listBTDevice.get(i).m_BluetoothDevice);
						return;
					}
				}
            }
            else
            {
            	Log.e(TAG, "Not Auto connecting.");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.e("Minwen", "Service discovered " + status);
                BluetoothGattService Service = gatt.getService(UUID_FFF0_CHARACTERISTIC);
                if (Service == null) {
                    Log.e(TAG, "service not found!");
                    return;
                }
                BluetoothGattCharacteristic characteristic = Service.getCharacteristic(UUID_FFF2_CHARACTERISTIC);
                if (characteristic == null) {
                    Log.e(TAG, "char not found!");
                    return;
                }
                else {
                	int iType = characteristic.getProperties();
                	
                	String intentAction = GET_ACK;
                	if (BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE == iType) {
                		m_bAck = false;
                		//m_strAckType = "No Ack";
					}
                	else if (BluetoothGattCharacteristic.PROPERTY_WRITE == iType) {
                		m_bAck = true;
                		//m_strAckType = "Ack";
					}
                	else {
                		m_bAck = true;
                		m_strAckType = "error ack:" + iType;
                	}
                	//broadcastUpdate(intentAction);
                }

                try {  
                    Thread.sleep(500);  
                } catch (InterruptedException e) {  
                    e.printStackTrace();  
                }
                if(ReadData(gatt)) {
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
							return;
						}
					}
                }
                else
                {
                	Log.e(TAG, "ReadData failed.");
                }

            } else {
                Log.e(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
                HandShake.Instance().OnRecvPacket(true,characteristic.getValue());
            }
            else
            {
                HandShake.Instance().OnRecvPacket(false,characteristic.getValue());
            }
        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, 
        								  BluetoothGattCharacteristic characteristic, 
        								  int status) {
        	if (status == BluetoothGatt.GATT_SUCCESS) {
        		Log.e(TAG, "InWrite");
        		
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);

                HandShake.Instance().OnWritePacket(true);

                //OnWritePacket
            }
        	else
            {
                HandShake.Instance().OnWritePacket(false);
        		Log.e(TAG, "InWrite fail");
        	}
            
        }
        
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {         
           
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
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
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
    
    private void broadcastUpdate(final String action,
            final BluetoothGattCharacteristic characteristic) {
		final Intent intent = new Intent(action);
		
		String strOutput = "";
		
		// === For all other profiles, writes the data formatted in HEX. ===
		final byte[] data = characteristic.getValue();
		if (data != null && data.length > 0) {
			final StringBuilder stringBuilder = new StringBuilder(data.length);
			for(byte byteChar : data)
				stringBuilder.append(String.format("%02X ", byteChar));
			//intent.putExtra(EXTRA_DATA, new String(data) + "\n" + stringBuilder.toString());
			//intent.putExtra(EXTRA_DATA, stringBuilder.toString());
			
			strOutput = stringBuilder.toString();
		}        
		
		// === For Read Characteristic ===
		if (UUID_FFF1_CHARACTERISTIC.equals(characteristic.getUuid())) 
		{
			intent.putExtra(READ_DATA, strOutput);
			//LogDataManager.getInstance().AddLogText("Read Data : " + strOutput);
		}
		
		// === For Write Characteristic ===
		if (UUID_FFF2_CHARACTERISTIC.equals(characteristic.getUuid())) 
		{  
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
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
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

        HandShake.Instance().SetConnected(false);

        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.e(TAG, "disconnect fail mBluetoothGatt not initialized");
            return;
        }
        Log.e(TAG, "Try Disconnecting");
        StopReadData();
        mBluetoothGatt.disconnect();
        mNotifyCharacteristic = null;
    }

    public synchronized void ActiveDisconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.e(TAG, "disconnect fail mBluetoothGatt not initialized");
            return;
        }
        m_bActiveDiscoonnect = true;
        Log.e(TAG, "Try Disconnecting");
        StopReadData();
        mBluetoothGatt.disconnect();
        mNotifyCharacteristic = null;
    }

    /**
     * After using a given BLE device, the app must call this method to ensure resources are
     * released properly.
     */
    public synchronized void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        disconnect();
        try {  
            Thread.sleep(100);  
        } catch (InterruptedException e) {  
            e.printStackTrace();  
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
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
        BluetoothGattCharacteristic characteristic1 = Service.getCharacteristic(UUID_FFF2_CHARACTERISTIC);
        if (characteristic1 == null) {
            Log.e(TAG, "char not found!");
            return null;
        }
        
        if (UUID_FFF2_CHARACTERISTIC.equals(characteristic.getUuid()))
        { 
        	
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
              
            if (m_bAck) {
        		characteristic1.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); 
			}
        	else {
        		characteristic1.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); 
        	}
            
            
            Write_Characteristic_Status = mBluetoothGatt.writeCharacteristic(characteristic1); 
            
            
            //Log.e("minwen", "Write_Characteristic_Status " + Write_Characteristic_Status);
                        
            try {
				Thread.sleep(30);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}            
            
            
            // === 2. Get Return data after Write data ===        
            /*BluetoothGattCharacteristic Response_characteristic1 = Service.getCharacteristic(UUID_FFF1_CHARACTERISTIC);
            if (Response_characteristic1 == null) {
                Log.e(TAG, "char not found!");
                return null;
            }            
            
            setCharacteristicNotification(Response_characteristic1, true);  */  
            
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
        BluetoothGattCharacteristic characteristic1 = Service.getCharacteristic(UUID_FFF2_CHARACTERISTIC);
        if (characteristic1 == null) {
            Log.e(TAG, "char not found!");
            return null;
        }   
        
        if (UUID_FFF2_CHARACTERISTIC.equals(characteristic.getUuid()))
        {       
        	// === 1. Write data to Characteristic ===        	
        	characteristic1.setValue(byData);
              
            //characteristic1.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); 
            if (m_bAck) {
        		characteristic1.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT); 
			}
        	else {
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
            BluetoothGattCharacteristic Response_characteristic1 = Service.getCharacteristic(UUID_FFF1_CHARACTERISTIC);
            if (Response_characteristic1 == null) {
                Log.e(TAG, "char not found!");
                return null;
            }            
            
            //setCharacteristicNotification(Response_characteristic1, true); 
            
            return characteristic1;
        }
        return null;
       
    }

    /**
     * Enables or disables notification on a give characteristic.
     *
     * @param characteristic Characteristic to act on.
     * @param enabled If true, enable notification.  False otherwise.
     */
    public void setCharacteristicNotification(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic,
                                              boolean enabled) {
        if (mBluetoothAdapter == null || bluetoothGatt == null) {
            Log.w(TAG, "setCharacteristicNotification BluetoothAdapter not initialized");
            return;
        }
        
        boolean bNotify = bluetoothGatt.setCharacteristicNotification(characteristic, enabled);

        // Enable Notification for FFF1, added by Ryan        
        if (UUID_FFF1_CHARACTERISTIC.equals(characteristic.getUuid())) 
        {	
        	BluetoothGattDescriptor descriptor = characteristic.getDescriptor(
        	UUID.fromString(SampleGattAttributes.CLIENT_CHARACTERISTIC_CONFIG));
        	descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
        	descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        	bluetoothGatt.writeDescriptor(descriptor);
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
    
    public void setWriteStatus(boolean status)
    {
    	 Write_Characteristic_Status = status;
    }
    
    public boolean getWriteStatus()
    {
    	return Write_Characteristic_Status;
    }
    // ======================================== Adding ............ ============================================
    public boolean WriteData(byte[] byVRCommand)
	{	
		if(this == null || mBluetoothGatt == null)
			return false;
		
		BluetoothGattService Service = mBluetoothGatt.getService(UUID_FFF0_CHARACTERISTIC);
        if (Service == null) {
            return false;
        }
        BluetoothGattCharacteristic characteristic = Service.getCharacteristic(UUID_FFF2_CHARACTERISTIC);
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
    
    public boolean WriteReadData(byte[] byVRCommand)
   	{	
   		if(this == null || mBluetoothGatt == null)
   			return false;
   		
   		BluetoothGattService Service = mBluetoothGatt.getService(UUID_FFF0_CHARACTERISTIC);
           if (Service == null) {
               return false;
           }
           BluetoothGattCharacteristic characteristic = Service.getCharacteristic(UUID_FFF2_CHARACTERISTIC);
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
    
    
    public boolean ReadData(BluetoothGatt bluetoothGatt) {        
       
        BluetoothGattService Service = bluetoothGatt.getService(UUID_FFF0_CHARACTERISTIC);
        if (Service == null) {
            return false;
        }
        BluetoothGattCharacteristic characteristic = Service.getCharacteristic(UUID_FFF1_CHARACTERISTIC);
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
            readCharacteristic(bluetoothGatt,characteristic);
        }
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            mNotifyCharacteristic = characteristic;
            setCharacteristicNotification(bluetoothGatt, characteristic, true);
        }
        return true;  
	}
    
    public boolean StopReadData() 
    {  
        BluetoothGattService Service = mBluetoothGatt.getService(UUID_FFF0_CHARACTERISTIC);
        if (Service == null) {
            return false;
        }
        BluetoothGattCharacteristic characteristic = Service.getCharacteristic(UUID_FFF1_CHARACTERISTIC);
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
            readCharacteristic(mBluetoothGatt,characteristic);
        }
        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
            mNotifyCharacteristic = characteristic;
            setCharacteristicNotification(mBluetoothGatt, characteristic, true);
        }
        
        setCharacteristicNotification(mBluetoothGatt, characteristic, false);        
        return true;  
    	
    }
    
    public void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    Log.e(TAG, "scanLeDevice stopLeScan");
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);                    
                }
            }, SCAN_PERIOD);

            Log.e(TAG, "scanLeDevice startLeScan");
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
					if (null == device.getName()) {
						return;
					}
					
					for (int i = 0; i < listBTDevice.size(); i++) {
						if (listBTDevice.get(i).m_BluetoothDevice.getAddress().equalsIgnoreCase(device.getAddress())) {
							return;
						}
					}
					
					BLEObj obj = new BLEObj();
					obj.m_BluetoothDevice = device;
					listBTDevice.add(obj);
					
                    // Send Message for updating UI List
                    Bundle mUpdateDeviceBundle = new Bundle();
                    mUpdateDeviceBundle.putInt("count", rssi);
                    
                    Message msg = new Message();
                    msg.setData(mUpdateDeviceBundle);
                    //ConnectActivity.mUpdateDeviceHandler.sendMessage(msg);
                    
                    if (null != m_strOldAddress) {
                    	if (m_strOldAddress.equalsIgnoreCase(device.getAddress())) {
                    		connectDevice(device);
                    		return;
						}
					}
				}
    };
    
    public void StopScan()
	{
		scanLeDevice(false);		
		disconnect();			
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}		
	}
    
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
            Thread.sleep(100);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    	mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
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