package com.nenoff.connecttoarduinoble

import android.Manifest
import androidx.appcompat.app.AppCompatActivity
import com.nenoff.connecttoarduinoble.BLEControllerListener
import com.nenoff.connecttoarduinoble.BLEController
import com.nenoff.connecttoarduinoble.RemoteControl
import android.os.Bundle
import com.nenoff.connecttoarduinoble.R
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import android.widget.Toast

class MainActivity : AppCompatActivity(), BLEControllerListener {
    private var bleController: BLEController? = null
    private var remoteControl: RemoteControl? = null
    private var deviceAddress: String? = null
    private var isLEDOn = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bleController = BLEController.getInstance(this)
        remoteControl = bleController?.let { RemoteControl(it) }
        checkBLESupport()
        checkPermissions()
    }

    override fun onStart() {
        super.onStart()
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled) {
            val enableBTIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBTIntent, 1)
        }
    }

    override fun onResume() {
        super.onResume()
        deviceAddress = null
        bleController = BLEController.getInstance(this)
        bleController?.addBLEControllerListener(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("OOBKey", "BLE init")
            bleController?.init()
        }
    }

    override fun onPause() {
        super.onPause()
        bleController!!.removeBLEControllerListener(this)
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                42
            )
        }
    }

    private fun checkBLESupport() {
        // Check if BLE is supported on the device.
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun BLEControllerConnected() {}
    override fun BLEControllerDisconnected() {
        isLEDOn = false
    }

    override fun BLEDeviceFound(name: String?, address: String?) {
        deviceAddress = address
    }
}