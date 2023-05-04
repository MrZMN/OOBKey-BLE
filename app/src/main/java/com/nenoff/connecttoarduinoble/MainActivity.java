package com.nenoff.connecttoarduinoble;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class MainActivity extends AppCompatActivity implements BLEControllerListener, SensorEventListener {

    // BLE
    private BLEController bleController;
    private RemoteControl remoteControl;
    private String deviceAddress;

    // UI
    private ImageButton startbt;
    private int bubackground = Color.parseColor("#00000000");

    // Sensor
    private SensorManager sensorManager;
    Sensor accelerometer, gyroscope;
    private Vibrator vibrator;

    // File IO
    private boolean isdatalog = false;          // control start/stop datalog
    private FileOutputStream out;
    private BufferedWriter writer;
    private final String ACC_FILE_NAME = "tap_phone_acc.dat";
    private final String GYO_FILE_NAME = "tap_phone_gyro.dat";
    final Handler handler = new Handler();      // for delay purpose
    private final int datalogtime = 10000;      // delay period in ms

    // Sound
    MediaPlayer one_glug;
    MediaPlayer two_glug;
    MediaPlayer three_glug;

    // Logs
    private String TAG = "OOBKey";

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

        TextView tv1 = findViewById(R.id.textView);
        TextView tv2 = findViewById(R.id.textView2);
        TextView tv3 = findViewById(R.id.textView3);

        String sentence1 = "1. Press the icon";
        String sentence2 = "2. Tap me on your chest";
        String sentence3 = "3. Stop with a vibration";

//        ForegroundColorSpan fcsRed = new ForegroundColorSpan(Color.RED);
//        ForegroundColorSpan fcsBlue = new ForegroundColorSpan(Color.BLUE);
//        ForegroundColorSpan fcsGreen = new ForegroundColorSpan(Color.parseColor("#A4C639"));
        ForegroundColorSpan fcsPurple = new ForegroundColorSpan(Color.parseColor("#A660EC"));

        SpannableString ss1 = new SpannableString(sentence1);
        SpannableString ss2 = new SpannableString(sentence2);
        SpannableString ss3 = new SpannableString(sentence3);

        ss2.setSpan(fcsPurple, 3,  6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss3.setSpan(fcsPurple, 15,  24, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tv1.setText(ss1);
        tv2.setText(ss2);
        tv3.setText(ss3);

        // Sensor
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(accelerometer != null) {
            sensorManager.registerListener(MainActivity.this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        }
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if(gyroscope != null) {
            sensorManager.registerListener(MainActivity.this, gyroscope, SensorManager.SENSOR_DELAY_FASTEST);
        }
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
    }

    @SuppressLint("MissingPermission")
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

        isdatalog = false;  // stop datalogging at any moment when phone returns to main page
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    42);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH},
                    43);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.HIGH_SAMPLING_RATE_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.HIGH_SAMPLING_RATE_SENSORS},
                    44);
        }
    }

    private void checkBLESupport() {
        // Check if BLE is supported on the device.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported!", Toast.LENGTH_SHORT).show();
            finish();
        }
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

    // UI
    private void initConnectButton() {
        this.startbt = findViewById(R.id.imageButton);
        this.startbt.setBackgroundColor(bubackground);

        this.startbt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                // Media
                one_glug = MediaPlayer.create(MainActivity.this, R.raw.one_glug);
                two_glug = MediaPlayer.create(MainActivity.this, R.raw.two_glug);
                three_glug = MediaPlayer.create(MainActivity.this, R.raw.three_glug);

                one_glug.start();

                // check if connected with device
                if(bleController.isBLEConnected == true) {

                    // disable the button
                    startbt.setEnabled(false);
                    startbt.setImageResource(R.drawable.ic_baseline_sd_card_24);

                    remoteControl.switchLED(true);      // send a signal to the device

                    // phone starts datalog
                    Log.d(TAG, "Datalog starts");
                    isdatalog = true;

                    // phone stops datalog after a period
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            // Do something after the delay
                            isdatalog = false;
                            Log.d(TAG, "Datalog stops");

                            // enable the button
                            startbt.setEnabled(true);
                            startbt.setImageResource(R.drawable.ic_baseline_play_circle_filled_24);

                            // Vibrate
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE));
                            } else {
                                //deprecated in API 26
                                vibrator.vibrate(1000);
                            }
                        }
                    }, datalogtime);
                }else {
                    Toast.makeText(getApplicationContext(), "Device not connected via BLE!", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // Sensor
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // operate only when datalog initialised
        if(isdatalog == true){
            if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                saveonesample(sensorEvent, "ACC");
            }
            if(sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                saveonesample(sensorEvent, "GYO");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    // File IO (new data will be appended)
    private void saveonesample(SensorEvent sensorEvent, String sensorType){
        try {
            if(sensorType == "ACC") {
                out = openFileOutput(ACC_FILE_NAME, MODE_APPEND);
            }else if(sensorType == "GYO") {
                out = openFileOutput(GYO_FILE_NAME, MODE_APPEND);
            }
            writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(sensorEvent.values[0] + ", ");
            writer.write(sensorEvent.values[1] + ", ");
            writer.write(sensorEvent.values[2] + "");
            writer.newLine();
            writer.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
