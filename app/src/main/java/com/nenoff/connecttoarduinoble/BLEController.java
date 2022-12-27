/*
 * (c) Matey Nenov (https://www.thinker-talk.com)
 *
 * Licensed under Creative Commons: By Attribution 3.0
 * http://creativecommons.org/licenses/by/3.0/
 *
 */

package com.nenoff.connecttoarduinoble;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.bluetooth.BluetoothProfile.GATT;

public class BLEController {
    private static BLEController instance;

    private BluetoothLeScanner scanner;
    private BluetoothDevice device;
    private BluetoothGatt bluetoothGatt;
    private BluetoothManager bluetoothManager;

    private BluetoothGattCharacteristic btGattChar = null;

    private ArrayList<BLEControllerListener> listeners = new ArrayList<>();
    private HashMap<String, BluetoothDevice> devices = new HashMap<>();

    private String TAG = "OOBKey";
    public boolean isBLEConnected = false;

    private BLEController(Context ctx) {
        this.bluetoothManager = (BluetoothManager) ctx.getSystemService(Context.BLUETOOTH_SERVICE);
    }

    public static BLEController getInstance(Context ctx) {
        if(null == instance)
            instance = new BLEController((ctx));

        return instance;
    }

    public void addBLEControllerListener(BLEControllerListener l) {
        if(!this.listeners.contains(l))
            this.listeners.add(l);
    }

    public void removeBLEControllerListener(BLEControllerListener l) {
        this.listeners.remove(l);
    }

    public void init() {
        this.devices.clear();
        this.scanner = this.bluetoothManager.getAdapter().getBluetoothLeScanner();
        scanner.startScan(bleCallback);
    }

    private ScanCallback bleCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            BluetoothDevice device = result.getDevice();
            if(!devices.containsKey(device.getAddress()) && isThisTheDevice(device)) {
                deviceFound(device);
                connectToDevice(device.getAddress());
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for(ScanResult sr : results) {
                BluetoothDevice device = sr.getDevice();
                if(!devices.containsKey(device.getAddress()) && isThisTheDevice(device)) {
                    deviceFound(device);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d(TAG, "scan failed with errorcode: " + errorCode);
        }
    };

    private boolean isThisTheDevice(BluetoothDevice device) {
        if(device.getName() != null) {
            Log.d(TAG, device.getName());
        }
        return null != device.getName() && device.getName().startsWith("OOBKey");
    }

    private void deviceFound(BluetoothDevice device) {
        this.devices.put(device.getAddress(), device);
        fireDeviceFound(device);
    }

    public void connectToDevice(String address) {
        this.device = this.devices.get(address);
        this.scanner.stopScan(this.bleCallback);
        Log.d(TAG, "connect to device " + device.getAddress());
        this.bluetoothGatt = device.connectGatt(null, false, this.bleConnectCallback);
    }

    private final BluetoothGattCallback bleConnectCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.d(TAG, "start service discovery " + bluetoothGatt.discoverServices());
            }else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                btGattChar = null;
                Log.d(TAG, "DISCONNECTED with status " + status);
                fireDisconnected();
                isBLEConnected = false;
            }else {
                Log.d(TAG, "unknown state " + newState + " and status " + status);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if(null == btGattChar) {
                for (BluetoothGattService service : gatt.getServices()) {
                    if (service.getUuid().toString().toUpperCase().startsWith("19B10000")) {
                        List<BluetoothGattCharacteristic> gattCharacteristics = service.getCharacteristics();
                        for (BluetoothGattCharacteristic bgc : gattCharacteristics) {
                            if (bgc.getUuid().toString().toUpperCase().startsWith("19B10001")) {
                                int chprop = bgc.getProperties();
                                if (((chprop & BluetoothGattCharacteristic.PROPERTY_WRITE) | (chprop & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) > 0) {
                                    btGattChar = bgc;
                                    Log.d(TAG, "CONNECTED and ready to send");
                                    fireConnected();
                                    isBLEConnected = true;
                                }
                            }
                        }
                    }
                }
            }
        }
    };

    private void fireDisconnected() {
        for(BLEControllerListener l : this.listeners)
            l.BLEControllerDisconnected();

        this.device = null;
    }

    private void fireConnected() {
        for(BLEControllerListener l : this.listeners)
            l.BLEControllerConnected();
    }

    private void fireDeviceFound(BluetoothDevice device) {
        for(BLEControllerListener l : this.listeners)
            l.BLEDeviceFound(device.getName().trim(), device.getAddress());
    }

    public void sendData(byte [] data) {
        this.btGattChar.setValue(data);
        bluetoothGatt.writeCharacteristic(this.btGattChar);
    }

    public boolean checkConnectedState() {
        return this.bluetoothManager.getConnectionState(this.device, GATT) == 2;
    }

    public void disconnect() {
        this.bluetoothGatt.disconnect();
    }
}