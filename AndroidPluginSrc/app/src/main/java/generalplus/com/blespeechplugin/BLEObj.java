package generalplus.com.blespeechplugin;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;

/**
 * Created by robinchiang on 2016/3/29.
 */
public class BLEObj {
    public BluetoothDevice m_BluetoothDevice = null;
    public BluetoothGatt m_BluetoothGatt = null;
    public BluetoothGattCharacteristic m_WriteCharacteristic = null;
    public int rssi = 0;

    public BLEObj()
    {
    }
}
