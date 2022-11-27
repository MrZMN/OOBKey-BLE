/*
 * (c) Matey Nenov (https://www.thinker-talk.com)
 *
 * Licensed under Creative Commons: By Attribution 3.0
 * http://creativecommons.org/licenses/by/3.0/
 *
 */
package com.nenoff.connecttoarduinoble

//import com.nenoff.connecttoarduinoble.BLEControllerListener.BLEControllerDisconnected
//import com.nenoff.connecttoarduinoble.BLEControllerListener.BLEControllerConnected
//import com.nenoff.connecttoarduinoble.BLEControllerListener.BLEDeviceFound
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothGattCharacteristic
import com.nenoff.connecttoarduinoble.BLEControllerListener
import android.bluetooth.le.ScanCallback
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothGattService
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.nenoff.connecttoarduinoble.BLEController
import java.util.*

class BLEController private constructor(ctx: Context) {
    private var scanner: BluetoothLeScanner? = null
    private var device: BluetoothDevice? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private val bluetoothManager: BluetoothManager
    private var btGattChar: BluetoothGattCharacteristic? = null
    private val listeners = ArrayList<BLEControllerListener>()
    private val devices = HashMap<String, BluetoothDevice>()
    fun addBLEControllerListener(l: BLEControllerListener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeBLEControllerListener(l: BLEControllerListener) {
        listeners.remove(l)
    }

    fun init() {
        devices.clear()
        scanner = bluetoothManager.adapter.bluetoothLeScanner
        scanner?.startScan(bleCallback)
    }

    private val bleCallback: ScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!devices.containsKey(device.address) && isThisTheDevice(device)) {
                deviceFound(device)
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            for (sr in results) {
                val device = sr.device
                if (!devices.containsKey(device.address) && isThisTheDevice(device)) {
                    deviceFound(device)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.i("[BLE]", "scan failed with errorcode: $errorCode")
        }
    }

    private fun isThisTheDevice(device: BluetoothDevice): Boolean {
        if (device.name != null) {
            Log.d("OOBKey", device.name)
        }
        return null != device.name && device.name.startsWith("LEDCallback")
    }

    private fun deviceFound(device: BluetoothDevice) {
        devices[device.address] = device
        fireDeviceFound(device)
    }

    fun connectToDevice(address: String) {
        device = devices[address]
        scanner!!.stopScan(bleCallback)
        Log.i("[BLE]", "connect to device " + device!!.address)
        bluetoothGatt = device!!.connectGatt(null, false, bleConnectCallback)
    }

    private val bleConnectCallback: BluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("[BLE]", "start service discovery " + bluetoothGatt!!.discoverServices())
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                btGattChar = null
                Log.w("[BLE]", "DISCONNECTED with status $status")
                fireDisconnected()
            } else {
                Log.i("[BLE]", "unknown state $newState and status $status")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (null == btGattChar) {
                for (service in gatt.services) {
                    if (service.uuid.toString().uppercase(Locale.getDefault())
                            .startsWith("19B10000")
                    ) {
                        val gattCharacteristics = service.characteristics
                        for (bgc in gattCharacteristics) {
                            if (bgc.uuid.toString().uppercase(Locale.getDefault())
                                    .startsWith("19B10001")
                            ) {
                                val chprop = bgc.properties
                                if (chprop and BluetoothGattCharacteristic.PROPERTY_WRITE or (chprop and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
                                    btGattChar = bgc
                                    Log.i("[BLE]", "CONNECTED and ready to send")
                                    fireConnected()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun fireDisconnected() {
        for (l in listeners) l.BLEControllerDisconnected()
        device = null
    }

    private fun fireConnected() {
        for (l in listeners) l.BLEControllerConnected()
    }

    private fun fireDeviceFound(device: BluetoothDevice) {
        for (l in listeners) l.BLEDeviceFound(device.name.trim { it <= ' ' }, device.address)
    }

    fun sendData(data: ByteArray?) {
        btGattChar!!.value = data
        bluetoothGatt!!.writeCharacteristic(btGattChar)
    }

    fun checkConnectedState(): Boolean {
        return bluetoothManager.getConnectionState(device, BluetoothProfile.GATT) == 2
    }

    fun disconnect() {
        bluetoothGatt!!.disconnect()
    }

    companion object {
        private var instance: BLEController? = null
        fun getInstance(ctx: Context): BLEController? {
            if (null == instance) instance = BLEController(
                ctx
            )
            return instance
        }
    }

    init {
        bluetoothManager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
}