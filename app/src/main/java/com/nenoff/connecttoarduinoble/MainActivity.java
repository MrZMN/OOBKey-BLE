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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class MainActivity extends AppCompatActivity implements BLEControllerListener, SensorEventListener {

    /*
    VARIABLES & OBJECTS
     */

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
    private final int datalogtime = 10000;      // datalog period in ms

    // Peak detection
    private final float MIN_AMPLITUDE = 29.4F;  // min amplitude to be a peak (29.4 m/s2 = 9.8 * 3)
    private final int MIN_DISTANCE = 20;        // min distance between two consecutive peaks
    private final int MAX_HA_WIDTH = 10;        // max width (from the lastest half-amplitude point to the peak)
    private ArrayList<Float> signal = new ArrayList<Float>();
    private int last_peak_index = 0;

    // Feedback Calculation
    private ArrayList<Integer> classes = new ArrayList<Integer>();
    private int prev_score = 2;
    private int no_punish_rounds = 0;       // number of 'NO punish rounds'
    private Boolean isButtonTest = false;   // to enable tap detection (provisional for testing)

    // Feedback Conversion
    private MediaPlayer earcon_label1;
    private MediaPlayer earcon_label2;
    private MediaPlayer earcon_label3;
//    private ArrayList<Integer> audiotime = new ArrayList<Integer>();
//    private int glug_time = 0;

    // Log
    private String TAG = "OOBKey";

    /*
    ANDROID LIFECYCLE
    You know what it is.
     */

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

        ForegroundColorSpan fcsPurple = new ForegroundColorSpan(Color.parseColor("#A660EC"));

        SpannableString ss1 = new SpannableString(sentence1);
        SpannableString ss2 = new SpannableString(sentence2);
        SpannableString ss3 = new SpannableString(sentence3);

        ss2.setSpan(fcsPurple, 3,  6, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss3.setSpan(fcsPurple, 15,  24, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        tv1.setText(ss1);
        tv2.setText(ss2);
        tv3.setText(ss3);

        // Media
        earcon_label1 = MediaPlayer.create(MainActivity.this, R.raw.glug_pitch1);
        earcon_label2 = MediaPlayer.create(MainActivity.this, R.raw.glug_pitch2);
        earcon_label3 = MediaPlayer.create(MainActivity.this, R.raw.glug_pitch3);
//        audiotime.add(0);
//        audiotime.add(earcon_label1.getDuration());   // 335 ms
//        audiotime.add(earcon_label2.getDuration());   // 291 ms
//        audiotime.add(earcon_label3.getDuration());   // 290 ms

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

    /*
    BUTTON CONTROL
    When pressing the button, the icon changes and:
    (i) Start tap detection
    (ii) If BLE connected, synchronize and start datalogging
     */

    private void initConnectButton() {
        this.startbt = findViewById(R.id.imageButton);
        this.startbt.setBackgroundColor(bubackground);

        this.startbt.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                startbt.setEnabled(false);  // disable the button
                startbt.setImageResource(R.drawable.ic_baseline_sd_card_24);    // change the icon
                isButtonTest = true;    // Enable tap detection

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

    /*
    SENSOR CONTROL
    This section handles sensor data reading in real-time.
     */

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {

        // tap detection
        if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER && isButtonTest == true) {
            signal.add(sensorEvent.values[0]);  // check ACC_X
            detectPeak(signal);
        }

        // datalog
        if(isdatalog == true){
            if(sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                saveonesample(sensorEvent, "ACC");
            }
            if(sensorEvent.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                saveonesample(sensorEvent, "GYO");
            }
        }
    }

    /*
    TAP DETECTION
    This section is to detect tap in real-time.
     */

    public void detectPeak(ArrayList<Float> signal) {
        int length = signal.size();
        if (length > 2) {
            // local optimum
            if (signal.get(length - 2) > signal.get(length - 3) && signal.get(length - 2) > signal.get(length - 1)) {
                // amplitude
                if (signal.get(length - 2) > MIN_AMPLITUDE) {
                    // distance between two peaks
                    if ((length - 2) - last_peak_index > MIN_DISTANCE) {
                        // width of the signal
                        if (detecthawidth(signal)) {

                            int interval = ((length - 2 - last_peak_index) * 1000 / 449);  // in ms
//                            interval -=  - glug_time;     // Question: should we deduct the earcon time from the reaction time?

                            Log.d("Feedback", "Interval: " + interval + " ms, Peak Amplitude: " + signal.get(length - 2) + " m/s2");

//                            feedbackControl(interval);    // feedback system

                            last_peak_index = length - 2;
                        }
                    }
                }
            }
        }
    }

    public boolean detecthawidth(ArrayList<Float> signal) {
        int length = signal.size();
        if (length > MAX_HA_WIDTH) {
            // from the second-to-last element to the first element <--
            for (int i = length - 2; i >= 0; i--) {
                // find the first half-amplitude point
                if (signal.get(i) <= signal.get(length - 2) / 2) {
                    // width of half amplitude
                    if ((length - 2 - i) < MAX_HA_WIDTH) {
                        return true;
                    } else {
                        return false;
                    }
                }
            }
        }
        return false;
    }

    /*
    FEEDBACK CALCULATION
    This section is to quantify the randomness of taps, according to some criteria of RNG tests.
     */

    public static boolean checkFrequency(ArrayList<Integer> arr) {
        int n = arr.size();
        if (n < 6) {
            return false;
        }

        // Slice the last six elements of the array.
        ArrayList<Integer> last_six = new ArrayList<>(arr.subList(n - 6, n));

        // Check the count of each element in the last_six array.
        for (int i = 0; i < 6; i++) {
            if (Collections.frequency(last_six, last_six.get(i)) > 4) {
                return true;
            }
        }

        // If we have checked all the elements without finding an element that
        // appears more than forth, return false.
        return false;
    }

    public static boolean checkOscillation(ArrayList<Integer> arr) {
        int n = arr.size();
        if (n < 4) {
            return false;
        }

        // Slice the last four elements of the array.
        ArrayList<Integer> last_four = new ArrayList<>(arr.subList(n - 4, n));

        // Check if all four elements are identical.
        if (last_four.get(0).equals(last_four.get(1)) && last_four.get(1).equals(last_four.get(2)) && last_four.get(2).equals(last_four.get(3))) {
            return true;
        }

        // If the last four elements are not identical, return false.
        return false;
    }

    public static boolean checkPattern(ArrayList<Integer> arr) {
        if (arr.size() < 5) {
            return false;
        }

        int a = arr.get(arr.size() - 1);
        int b = arr.get(arr.size() - 2);

        if (a == b) {
            return false;
        }

        ArrayList<Integer> pattern = new ArrayList<>(Arrays.asList(a, b, a, b, a));
        ArrayList<Integer> last_five = new ArrayList<>(arr.subList(arr.size() - 5, arr.size()));
        return last_five.equals(pattern);
    }

    // feedback calculation based on latest input and recent history
    public void feedbackControl(Integer interval) {
        // classify the time interval
        if(interval <= 750){
            classes.add(1);
            Log.d("Feedback", "Class: ①");
        }else if(interval > 750 && interval <= 1500){
            classes.add(2);
            Log.d("Feedback", "Class: ②");
        }else if(interval > 1500 && interval <= 2250){
            classes.add(3);
            Log.d("Feedback", "Class: ③");
        }else if(interval > 2250){
            classes.add(4);
            Log.d("Feedback", "Class: ④");
        }

        // init score
        int score = 2;
        int is_punish = 0;

        // Punish strategy 1: frequency check
        if(checkFrequency(classes)) {
            score -= 1;
            is_punish = 1;
        }
        // Punish strategy 2: oscillation check
        if(checkOscillation(classes)) {
            score -= 1;
            is_punish = 1;
        }
        // Punish strategy 3: pattern check
        if(checkPattern(classes)) {
            score -= 1;
            is_punish = 1;
        }

        // Reward strategy 1: if previous round was punished, and the score is higher in this round (rectify)
        if(no_punish_rounds == 0 && score > prev_score) {
            score += 1;
        }
        // Reward strategy 2: if there are no punish for three consecutive rounds, add 1 to the score
        if(no_punish_rounds >= 3) {
            score += 1;
            no_punish_rounds = 0;
        }

        // score always in [0, 1, 2, 3]
        score = Math.max(0, Math.min(score, 3));
        Log.d("Feedback", "Score: " + score);

//        glug_time = audiotime.get(score);
        play_earcon(score);

        prev_score = score;
        if(is_punish == 1) {
            no_punish_rounds = 0;
        }else if(classes.size() > 4){
            no_punish_rounds += 1;
        }
    }

    /*
    FEEDBACK CONVERSION
    This section is to convert the score of a tap to an earcon.
     */

    public void play_earcon(Integer score) {
        if(score == 1){
            earcon_label1.start();
        }else if(score == 2){
            earcon_label2.start();
        }else if(score == 3){
            earcon_label3.start();
        }else{
        }
    }

    /*
    DATALOGGING
    This section stores the IMU data as local files.
     */

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

    /*
    OTHERS
    You may not want to care about them
     */

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
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
}
