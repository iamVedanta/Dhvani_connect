package com.sarmale.arduinobtexample_v3;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;
import java.util.UUID;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "FrugalLogs";
    private static final int REQUEST_ENABLE_BT = 1;
    public static Handler handler;
    private final static int ERROR_READ = 0;
    BluetoothDevice arduinoBTModule = null;
    UUID arduinoUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        TextView btReadings = findViewById(R.id.btReadings);
        TextView btDevices = findViewById(R.id.btDevices);
        Button connectToDevice = findViewById(R.id.connectToDevice);
        Button seachDevices = findViewById(R.id.seachDevices);
        Button clearValues = findViewById(R.id.refresh);

        Button actionButton1 = findViewById(R.id.actionButton1);
        Button actionButton2 = findViewById(R.id.actionButton2);
        Button actionButton3 = findViewById(R.id.actionButton3);

        Log.d(TAG, "Begin Execution");

        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case ERROR_READ:
                        String arduinoMsg = msg.obj.toString();
                        btReadings.setText(arduinoMsg);
                        break;
                }
            }
        };

        clearValues.setOnClickListener(view -> {
            btDevices.setText("");
            btReadings.setText("");
        });

        final Observable<String> connectToBTObservable = Observable.create(emitter -> {
            Log.d(TAG, "Calling connectThread class");
            ConnectThread connectThread = new ConnectThread(arduinoBTModule, arduinoUUID, handler);
            connectThread.run();
            if (connectThread.getMmSocket().isConnected()) {
                Log.d(TAG, "Calling ConnectedThread class");
                ConnectedThread connectedThread = new ConnectedThread(connectThread.getMmSocket());
                connectedThread.run();
                if (connectedThread.getValueRead() != null) {
                    emitter.onNext(connectedThread.getValueRead());
                }
                connectedThread.cancel();
            }
            connectThread.cancel();
            emitter.onComplete();
        });

        connectToDevice.setOnClickListener(view -> {
            btReadings.setText("");
            if (arduinoBTModule != null) {
                connectToBTObservable
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribeOn(Schedulers.io())
                        .subscribe(valueRead -> btReadings.setText(valueRead));
            }
        });

        seachDevices.setOnClickListener(view -> {
            if (bluetoothAdapter == null) {
                Log.d(TAG, "Device doesn't support Bluetooth");
            } else {
                Log.d(TAG, "Device support Bluetooth");
                if (!bluetoothAdapter.isEnabled()) {
                    Log.d(TAG, "Bluetooth is disabled");
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    if (ActivityCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        Log.d(TAG, "We don't have BT Permissions");
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                        Log.d(TAG, "Bluetooth is enabled now");
                    } else {
                        Log.d(TAG, "We have BT Permissions");
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                        Log.d(TAG, "Bluetooth is enabled now");
                    }
                } else {
                    Log.d(TAG, "Bluetooth is enabled");
                }
                String btDevicesString = "";
                Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                if (pairedDevices.size() > 0) {
                    for (BluetoothDevice device : pairedDevices) {
                        String deviceName = device.getName();
                        String deviceHardwareAddress = device.getAddress();
                        Log.d(TAG, "deviceName:" + deviceName);
                        Log.d(TAG, "deviceHardwareAddress:" + deviceHardwareAddress);
                        btDevicesString = btDevicesString + deviceName + " || " + deviceHardwareAddress + "\n";
                        if (deviceName.equals("HC-05")) {
                            Log.d(TAG, "HC-05 found");
                            arduinoUUID = device.getUuids()[0].getUuid();
                            arduinoBTModule = device;
                            connectToDevice.setEnabled(true);
                        }
                        btDevices.setText(btDevicesString);
                    }
                }
            }
            Log.d(TAG, "Button Pressed");
        });

        actionButton1.setOnClickListener(view -> sendActionRequest("http://your-flask-server-ip:5000/action1"));
        actionButton2.setOnClickListener(view -> sendActionRequest("http://your-flask-server-ip:5000/action2"));
        actionButton3.setOnClickListener(view -> sendActionRequest("http://192.168.1.3:5000/action3"));
    }

    private void sendActionRequest(String urlString) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Sending POST request to: " + urlString);
                URL url = new URL(urlString);
                HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                int responseCode = urlConnection.getResponseCode();
                Log.d(TAG, "Response Code: " + responseCode);
                urlConnection.disconnect();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "Error: " + e.getMessage());
            }
        }).start();
    }
}
