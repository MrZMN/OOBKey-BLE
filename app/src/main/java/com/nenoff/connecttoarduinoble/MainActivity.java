package com.nenoff.connecttoarduinoble;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements BLEControllerListener {

    // BLE
    private BLEController bleController;
    private RemoteControl remoteControl;
    private String deviceAddress;

    // UI
    private Button startbt;
    private Button testbt;
    private Spinner spinner_mode;
    private Spinner spinner_frequency;
    private Spinner spinner_time;
//    String[] modes = {"swept", "constant", "stepped"};
    String[] modes = {"constant", "stepped"};
    String[] frequencies = {"50", "75", "100"};
//    String[] times = {"400", "600", "800", "1000"};
    String[] times = {"400", "700", "1000"};
    private int initialcolor = Color.parseColor("#9acd32");
    private int afterpresscolor = Color.parseColor("#d3d3d3");

    // Command
    private String vib_mode = "";
    private String vib_frequency = "";
    private String vib_time = "";
    final Handler handler = new Handler();      // for delay purpose
    private final int num_move = 15;      // number of moves per run
    private int run_time = 0;

    // Sensor
    private Vibrator vibrator;

    // Logs
    private String TAG = "VibKey";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // BLE
        this.bleController = BLEController.getInstance(this);
        this.remoteControl = new RemoteControl(this.bleController);

        checkBLESupport();
        checkPermissions();

        // UI
        initConnectButton();
        initTestButton();
        initSpinners();

        // Sensor
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if(!BluetoothAdapter.getDefaultAdapter().isEnabled()){
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        this.deviceAddress = null;
        this.bleController = BLEController.getInstance(this);
        this.bleController.addBLEControllerListener(this);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            this.bleController.init();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        this.bleController.removeBLEControllerListener(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    // UI
    private void initConnectButton() {
        this.startbt = findViewById(R.id.button);
        this.startbt.setBackgroundColor(initialcolor);

        this.startbt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Log.d(TAG, vib_mode + ", " + vib_frequency + "Hz, " + vib_time + "ms");

                // check if connected with device
                if(bleController.isBLEConnected == true) {

                    // disable the button
                    startbt.setEnabled(false);
                    startbt.setBackgroundColor(afterpresscolor);
                    testbt.setEnabled(false);
                    testbt.setBackgroundColor(afterpresscolor);

                    // how much time does one run cost?
                    if (vib_mode == "constant") {
                        run_time = (Integer.parseInt(vib_time) + 1500 + 1000) * num_move;
                    } else if (vib_mode == "stepped") {
                        run_time = (Integer.parseInt(vib_time) * 3 + 1500 + 1000) * num_move;
                    } else if (vib_mode == "swept") {
                        run_time = (1750 + 1500 + 1000) * num_move;
                    }

                    remoteControl.sendCommand(vib_mode, vib_frequency, vib_time, true);      // send the command to the device

                    // phone stops datalog after a period
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {

                            // enable the button
                            startbt.setEnabled(true);
                            startbt.setBackgroundColor(initialcolor);
                            testbt.setEnabled(true);
                            testbt.setBackgroundColor(initialcolor);

                            // Vibrate
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                            } else {
                                //deprecated in API 26
                                vibrator.vibrate(500);
                            }
                        }
                    }, run_time);
                }else {
                    Toast.makeText(getApplicationContext(), "Device not connected via BLE!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void initTestButton() {
        this.testbt = findViewById(R.id.button2);
        this.testbt.setBackgroundColor(initialcolor);

        this.testbt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // check if connected with device
                if(bleController.isBLEConnected == true) {
                    // disable the button
                    startbt.setEnabled(false);
                    startbt.setBackgroundColor(afterpresscolor);
                    testbt.setEnabled(false);
                    testbt.setBackgroundColor(afterpresscolor);

                    run_time = 10000 + 1500 + 1000;

                    remoteControl.testFrequency(vib_frequency, true);     // send the command to the device

                    // phone stops datalog after a period
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {

                            // enable the button
                            startbt.setEnabled(true);
                            startbt.setBackgroundColor(initialcolor);
                            testbt.setEnabled(true);
                            testbt.setBackgroundColor(initialcolor);

                            // Vibrate
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                            } else {
                                //deprecated in API 26
                                vibrator.vibrate(500);
                            }
                        }
                    }, run_time);
                }else {
                    Toast.makeText(getApplicationContext(), "Device not connected via BLE!", Toast.LENGTH_SHORT).show();
                }
            }
        });


    }

    private void initSpinners() {
        spinner_mode = findViewById(R.id.spinner);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_spinner_item, modes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_mode.setAdapter(adapter);
        spinner_mode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String value = adapterView.getItemAtPosition(i).toString();
                vib_mode = value;
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        spinner_frequency = findViewById(R.id.spinner2);
        ArrayAdapter<String> adapter2 = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_spinner_item, frequencies);
        adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_frequency.setAdapter(adapter2);
        spinner_frequency.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String value = adapterView.getItemAtPosition(i).toString();
                vib_frequency = value;
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
        spinner_time = findViewById(R.id.spinner3);
        ArrayAdapter<String> adapter3 = new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_spinner_item, times);
        adapter3.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner_time.setAdapter(adapter3);
        spinner_time.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                String value = adapterView.getItemAtPosition(i).toString();
                vib_time = value;
            }
            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });
    }

    // BLE
    @Override
    public void BLEControllerConnected() {

    }

    @Override
    public void BLEControllerDisconnected() {

    }

    @Override
    public void BLEDeviceFound(String name, String address) {
        this.deviceAddress = address;
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    42);
        }
    }

    private void checkBLESupport() {
        // Check if BLE is supported on the device.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported!", Toast.LENGTH_SHORT).show();
            finish();
        }
    }
}
