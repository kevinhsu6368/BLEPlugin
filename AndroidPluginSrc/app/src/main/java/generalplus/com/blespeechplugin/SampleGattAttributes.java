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

import java.util.HashMap;

/**
 * This class includes a small subset of standard GATT attributes for demonstration purposes.
 */
public class SampleGattAttributes {
    private static HashMap<String, String> attributes = new HashMap();
    public static String HEART_RATE_MEASUREMENT = "00002a37-0000-1000-8000-00805f9b34fb";
    public static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    
    public static String FFF0_CHARACTERISTIC = "0000fff0-0000-1000-8000-00805f9b34fb";
    public static String FFF1_CHARACTERISTIC = "0000fff1-0000-1000-8000-00805f9b34fb";  //   C1 ---- Notify
    public static String FFF2_CHARACTERISTIC = "0000fff2-0000-1000-8000-00805f9b34fb";  //   C1 ---- WRITE
    public static String FFF4_CHARACTERISTIC = "0000fff4-0000-1000-8000-00805f9b34fb";  //   DB-2 ---- Notify
    public static String FFF5_CHARACTERISTIC = "0000fff5-0000-1000-8000-00805f9b34fb";  //   DB-2 ----- READ

    static {
        // Sample Services.
        
        attributes.put("00001800-0000-1000-8000-00805f9b34fb", "GENERIC ACCESS SERVICE");
        attributes.put("00002a00-0000-1000-8000-00805f9b34fb", "DEVICE NAME");
        attributes.put("00002a01-0000-1000-8000-00805f9b34fb", "APPEARANCE");
        attributes.put("00002a04-0000-1000-8000-00805f9b34fb", "PERIPHERAL CONNECT PARAMETER");       
        
        
        attributes.put("0000fff0-0000-1000-8000-00805f9b34fb", "UUID:FFF0");
        attributes.put("0000fff1-0000-1000-8000-00805f9b34fb", "0xFFF1");
        attributes.put("0000fff2-0000-1000-8000-00805f9b34fb", "0xFFF2"); // for C1 get service
        attributes.put("0000fff4-0000-1000-8000-00805f9b34fb", "0xFFF4"); // for DB-2  get service
        
        attributes.put("00001809-0000-1000-8000-00805f9b34fb", "Unknow");          
        
        attributes.put("0000180f-0000-1000-8000-00805f9b34fb", "BATTERY SERVICE");
        attributes.put("00002a19-0000-1000-8000-00805f9b34fb", "Battery Level");  
        
        attributes.put("0000180a-0000-1000-8000-00805f9b34fb", "Device Information Service");
        attributes.put("00002a29-0000-1000-8000-00805f9b34fb", "Manufacturer Name");
        attributes.put("00002a24-0000-1000-8000-00805f9b34fb", "Model Number");
        attributes.put("00002a25-0000-1000-8000-00805f9b34fb", "Serial Number");
        attributes.put("00002a27-0000-1000-8000-00805f9b34fb", "Hardware Revision");
        attributes.put("00002a26-0000-1000-8000-00805f9b34fb", "Firmware Revision");
        attributes.put("00002a28-0000-1000-8000-00805f9b34fb", "Software Revision"); 
        
        // Sample Characteristics.
        attributes.put("0000180d-0000-1000-8000-00805f9b34fb", "HEART RATE");
        attributes.put(HEART_RATE_MEASUREMENT, "Heart Rate Measurement");  
        attributes.put("00002a38-0000-1000-8000-00805f9b34fb", "Body Sensor Location");
        attributes.put("00002a98-0000-1000-8000-00805f9b34fb", "RyanTEST String");        
    }

    public static String lookup(String uuid, String defaultName) {
        String name = attributes.get(uuid);
        return name == null ? defaultName : name;
    }
}
