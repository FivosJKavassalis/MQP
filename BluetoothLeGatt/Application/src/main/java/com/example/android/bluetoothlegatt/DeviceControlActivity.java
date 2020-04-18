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

package com.example.android.bluetoothlegatt;

import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.example.android.bluetoothlegatt.database.Database;
import com.example.android.bluetoothlegatt.graphs.HRVGraphActivity;
import com.example.android.bluetoothlegatt.graphs.HeartRateGraphActivity;
import com.example.android.bluetoothlegatt.graphs.RRGraphActivity;
import com.example.android.bluetoothlegatt.graphs.SP02GraphActivity;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.example.android.bluetoothlegatt.R;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference myRef = database.getReference();

    final DatabaseReference heartRate = myRef.child("HeartRate");
    final DatabaseReference hrv = myRef.child("HRV");
    final DatabaseReference sp02 = myRef.child("SP02");
    final DatabaseReference respiratoryRate = myRef.child("RespiratoryRate");

    Button hr_button;
    Button hrv_button;
    Button sp02_button;
    Button rr_button;

    //--------------------------------------------------------------------------
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    //--------------------------------------------------------------------------------------------------------
    Handler handler = new Handler();
    public double[] ppg_array = new double[16384];
    public int ppg_array_index = 0;

    private Runnable runnableCode = new Runnable() {
        @Override
        public void run() {
            // Do something here on the main thread
            Log.d("Handlers", "Called on main thread");
            // Repeat this the same runnable code block again another 2 seconds
            // 'this' is referencing the Runnable object
            if(mBluetoothLeService != null) {
                //TextView val = findViewById(R.id.data_value);
                BluetoothGattCharacteristic CHARACTER = mBluetoothLeService.readCustomCharacteristic();
//                if(CHARACTER != null) {
////                    if (ppg_array_index == 14999) {
////                        ppg_array_index = 0;
////                    }
//
////                    String value = String.valueOf(CHARACTER);
////                    if (value.contains("android") == false) {
////                        ppg_array[ppg_array_index] = Double.valueOf(value); // Double...
////
////                        String spo2_val = String.valueOf(ppg_array[ppg_array_index]);
////                        val.setText(spo2_val);
////                        ppg_array_index+=1;
////                    }
//
//                }

            }
            handler.postDelayed(this, 1);
        }
    };

//--------------------------------------------------------------------------------------------------------


    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                //  displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
            };

    private void clearUI() {
//        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {


        super.onCreate(savedInstanceState);

        FirebaseApp.initializeApp(this);

        setContentView(R.layout.button_control);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        hr_button  = findViewById(R.id.heartRate);
        hrv_button  = findViewById(R.id.HRV);
        sp02_button  = findViewById(R.id.SP02);
        rr_button  = findViewById(R.id.respiratoryRate);

        // Sets up UI references.
        /*
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
    mConnectionState = (TextView) findViewById(R.id.connection_state);
    */




        heartRate.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                double heartRateValue = (double) dataSnapshot.getValue();

                Log.d(getClass().getName(), String.valueOf(heartRateValue));
                hr_button.setText(String.valueOf(heartRateValue));

//                String value = (String) dataSnapshot.getValue();
//                Log.d("file", "Value is: " + value);
//                hr_button.setText(value);

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Failed to read value
                Log.w("file", "Failed to read value.", databaseError.toException());
            }
        });

        hrv.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                double hrvValue = (double) dataSnapshot.getValue();
                hrv_button.setText(String.valueOf(hrvValue));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Failed to read value
                Log.w("file", "Failed to read value.", databaseError.toException());
            }
        });

        sp02.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                double sp02Value = (double) dataSnapshot.getValue();
                sp02_button.setText(String.valueOf(sp02Value));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Failed to read value
                Log.w("file", "Failed to read value.", databaseError.toException());
            }
        });

        respiratoryRate.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                double respiratoryRateValue = (double) dataSnapshot.getValue();
                rr_button.setText(String.valueOf(respiratoryRateValue));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                // Failed to read value
                Log.w("file", "Failed to read value.", databaseError.toException());
            }
        });

//        DateTime dateTime = new DateTime();
//        Database.getInstance().addListEntries("a97b60c18d59e" + dateTime.getMillis() + "f");

//        if (ppg_array_index == 10) {
//            for (int i=0; i<1; i++){
//                String spo2_val = String.valueOf(ppg_array[i+5]);
//                DateTime dateTime = new DateTime();
//                Database.getInstance().addListEntries("a" + "96"  + "b60c18d59e" + dateTime.getMillis() + "f");
//            }
//
//        }




        mDataField = (TextView) findViewById(R.id.data_value);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
//                mBluetoothLeService.readCustomCharacteristic();

            handler.post(runnableCode);


//            if (ppg_array_index == 10) { // 16383   ppg_array_index == 1000
////                for (int i=0; i<=10; i++){ // 14999
//
////                    String spo2 = String.valueOf(ppg_array[i]); // TODO: MAKE 4 FUNCTIONS WITH EACH HEALTH ALG AND CALL HERE!!!
////                    String heart_rate = String.valueOf(ppg_array[i]);
////                    String respiratory_rate = String.valueOf(ppg_array[i]);
////                    String hrv = String.valueOf(ppg_array[i]);
//
//                    String spo2 = String.valueOf(spo2_calculation(ppg_array)); // TODO: MAKE 4 FUNCTIONS WITH EACH HEALTH ALG AND CALL HERE!!!
//                    String heart_rate = String.valueOf(hr_calculation(ppg_array));
//                    String respiratory_rate = String.valueOf(rr_calculation(ppg_array));
////                    String hrv = String.valueOf(hrv_calculation(ppg_array));
//
//
//                    DateTime dateTime = new DateTime();
//                    try {
//                        Database.getInstance().addListEntries("a" + spo2 + "b" + heart_rate + "c" + respiratory_rate + "d" + hrv + "e" + dateTime.getMillis() + "f");
//                    } catch (Exception e) {
//                        Log.e(getClass().getName(), e.getMessage());
//                    }
////                }
//                ppg_array_index = 0;
//            }


        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //  mConnectionState.setText(resourceId);
            }
        });
    }

    private void displayData(String data) {
        if (data != null) {
            //mDataField.setText(data); - HAVE A CASE WHERE WE CUT TO 14999 BUT MCU IS TRANSMITING MORE AND THEN DELETE REST OF VALUES W ARE GETTING
            data = data.substring(0, data.indexOf(".")+3);
            if((Double.valueOf(data) != ppg_array[ppg_array_index - 1]) ) {

                ppg_array[ppg_array_index] = Double.valueOf(data); // Double...

                //String spo2_val = String.valueOf(ppg_array[ppg_array_index]);
                mDataField.setText(data);
                ppg_array_index+=1;

            }


            if (ppg_array_index == 10) {

                String spo2 = String.valueOf(spo2_calculation(ppg_array)); // TODO: MAKE 4 FUNCTIONS WITH EACH HEALTH ALG AND CALL HERE!!!
                String heart_rate = String.valueOf(hr_calculation(ppg_array));
                String respiratory_rate = String.valueOf(rr_calculation(ppg_array));
//                    String hrv = String.valueOf(hrv_calculation(ppg_array));


                DateTime dateTime = new DateTime();
                try {
                    Database.getInstance().addListEntries("a" + spo2 + "b" + heart_rate + "c" + respiratory_rate + "d" + hrv + "e" + dateTime.getMillis() + "f");
                } catch (Exception e) {
                    Log.e(getClass().getName(), e.getMessage());
                }


                ppg_array_index = 0;
            }
        }
    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    public void onClickWrite(View v){
        if(mBluetoothLeService != null) {
            mBluetoothLeService.writeCustomCharacteristic(0x41);
        }
    }

    public void onClickRead(View v){
        if(mBluetoothLeService != null) {
            mBluetoothLeService.readCustomCharacteristic();
        }
    }

//    public void onCharacteristicChanged(View v){
//        if(mBluetoothLeService != null) {
//            mBluetoothLeService.readCustomCharacteristic();
//        }
//    }

//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        getMenuInflater().inflate(R.menu.main_menu, menu);
//        return true;
//    }

//    @Override
//    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
//        int id = item.getItemId();
//
//        if (id == R.id.action_settings)
//        {
//            Intent settingsIntent = new Intent(DeviceControlActivity.this, SettingsActivity.class);
//            startActivity(settingsIntent);
//            return true;
//        }
//
//        return super.onOptionsItemSelected(item);
//    }

    public void onClick_sp02(View view) {
        Intent intent = new Intent(DeviceControlActivity.this, SP02GraphActivity.class);
        startActivity(intent);
    }

    public void onClick_hr(View view) {
        Intent intent = new Intent(DeviceControlActivity.this, HeartRateGraphActivity.class);
        startActivity(intent);
    }

    public void onClick_hrv(View view) {
        Intent intent = new Intent(DeviceControlActivity.this, HRVGraphActivity.class);
        startActivity(intent);
    }

    public void onClick_rr(View view) {
        Intent intent = new Intent(DeviceControlActivity.this, RRGraphActivity.class);
        startActivity(intent);
    }





    // compute the FFT of x[], assuming its length is a power of 2
    public static void fft(Complex[] x) {

        // check that length is a power of 2
        int n = x.length;
        if (Integer.highestOneBit(n) != n) {
            throw new RuntimeException("n is not a power of 2");
        }

        // bit reversal permutation
        int shift = 1 + Integer.numberOfLeadingZeros(n);
        for (int k = 0; k < n; k++) {
            int j = Integer.reverse(k) >>> shift;
            if (j > k) {
                Complex temp = x[j];
                x[j] = x[k];
                x[k] = temp;
            }
        }

        // butterfly updates
        for (int L = 2; L <= n; L = L+L) {
            for (int k = 0; k < L/2; k++) {
                double kth = -2 * k * Math.PI / L;
                Complex w = new Complex(Math.cos(kth), Math.sin(kth));
                for (int j = 0; j < n/L; j++) {
                    Complex tao = w.times(x[j*L + k + L/2]);
                    x[j*L + k + L/2] = x[j*L + k].minus(tao);
                    x[j*L + k]       = x[j*L + k].plus(tao);
                }
            }
        }
    }

    //TODO: create 4 functions that calculate each of the 4 algorithms

    // SpO2
    public double spo2_calculation(double[] ppg_array_all) {

        // Initialize variables, data structures and split input data into red and ir arrays
        Complex[] red_ppg_arr = new Complex[8192];
        Complex[] ir_ppg_arr = new Complex[8192];
        double red_DC_val = -1;
        double ir_DC_val = -1;
        double red_AC_val = 0;
        double ir_AC_val = 0;
        double SpO2_ratio = 0;
        double local_max_red = 0;
        double local_max_ir = 0;
        int red_local_max_index = 0;
        int ir_local_max_index = 0;
        int Fs = 50; // sampling frequency
 //       int Fs = 1000;
        int array_length = 8192;
        double freq_values[] = new double[4097];

        // Copy RED and IR values received from our pulse oximeter to their corresponding
        // arrays to perform FFT
        for (int i = 0; i < 8192; i++) {
            red_ppg_arr[i] = new Complex(ppg_array_all[i], 0);
            ir_ppg_arr[i] = new Complex(ppg_array_all[i+8192], 0);

        }

        // Perform FFR to RED and IR waveforms
        fft(red_ppg_arr);
        fft(ir_ppg_arr);

        // Compute two-sided spectrum of RED and IR by calculating absolute value of each point and
        // dividing by length of FFT. Furthermore, we calculate frequency values associated with
        // each one-sided spectrum value for RED and IR respectively
        double[] abs_red_ppg_arr = new double[8192];
        double[] abs_ir_ppg_arr = new double[8192];
        for (int i = 0; i < 4097; i++) {
            abs_red_ppg_arr[i] = red_ppg_arr[i].abs()/array_length;
            abs_ir_ppg_arr[i] = red_ppg_arr[i].abs()/array_length;
            freq_values[i] = (Fs * i)/array_length; // frequency values associated with each manipulated PPG waveform value

        }

        // Complete computation of one-sided spectrum by multiplying first half of the FFT points
        // by 2, excluding the edges.
        for (int i = 1; i < 4096; i++) {
            abs_red_ppg_arr[i] = 2 * abs_red_ppg_arr[i];
            abs_ir_ppg_arr[i] = 2 * abs_ir_ppg_arr[i];
        }

        // Assign DC component of RED and IR waveforms by assigning the 0th point of
        // the one-sided spectrum respectively (amplitude at 0 Hz)
        red_DC_val = abs_red_ppg_arr[0];
        ir_DC_val = abs_ir_ppg_arr[0];

        // Calculate AC component of RED and IR waveforms by finding local max in the range of
        // 0.5 to 5 Hz
        double freq = 0;
        for (int i = 1; i < 4096; i++) {
            freq = freq_values[i];
            if ((freq >= 0.5) && (freq <= 5)) {
                if (local_max_red < abs_red_ppg_arr[i]) {
                    local_max_red = abs_red_ppg_arr[i];
                    red_local_max_index = i;
                }

                if (local_max_ir < abs_ir_ppg_arr[i]) {
                    local_max_ir =  abs_ir_ppg_arr[i];
                    ir_local_max_index = i;
                }
            }
        }



        // assigning resulting local maxes to the corresponding AC component variables
        red_AC_val = local_max_red;
        ir_AC_val = local_max_ir;

        // Compute SpO2 ratio by dividing the normalized RED and IR AC components
        //(ACred/DCred)/(ACir/DCir)
        SpO2_ratio = (red_AC_val/red_DC_val)/(ir_AC_val/ir_DC_val);

        // Calculate SpO2 percentage by applying our empirical formula to the resulting ratio
        //(A - B * Ratio)
        double SpO2_percentage = 110 - SpO2_ratio * 25;

        return SpO2_percentage;

    }

    // Heart Rate
    public double hr_calculation(double[] ppg_array_all) {

        Complex[] red_ppg_arr = new Complex[8192];
        int Fs = 50; // sampling frequency
        //int Fs = 1000;
        int array_length = 8192;
        double freq_values[] = new double[4097];
        double local_max_red = 0;
        int red_local_max_index = 0;
        double heart_rate = 0;

        for (int i = 0; i < 8192; i++) {
            red_ppg_arr[i] = new Complex(ppg_array_all[i], 0);
        }

        // FFT, find dominant frequency between 1 and 1.7 Hz (60 to 100 bpm), multiply dominant frequency with 60
        // Perform fft
        fft(red_ppg_arr);

        double[] abs_red_ppg_arr = new double[8192];
        for (int i = 0; i < 4097; i++) {
            abs_red_ppg_arr[i] = red_ppg_arr[i].abs()/array_length;
            freq_values[i] = (Fs * i)/array_length; // frequency values associated with each manipulated PPG waveform value

        }


        // double values since one-sided...
        for (int i = 1; i < 4096; i++) {
            abs_red_ppg_arr[i] = 2 * abs_red_ppg_arr[i];
        }

        double freq = 0;

        for (int i = 1; i < 4096; i++) {
            freq = freq_values[i];
            if ((freq >= 1) && (freq <= 1.7)) {
                if (local_max_red < abs_red_ppg_arr[i]) {
                    local_max_red = abs_red_ppg_arr[i];
                    red_local_max_index = i;
                }
            }
        }

        heart_rate = freq_values[red_local_max_index] * 60;

        return heart_rate;
    }


    // Respiratory Rate
    public double rr_calculation(double[] ppg_array_all) {

        Complex[] red_ppg_arr = new Complex[8192];
        int Fs = 50; // sampling frequency
        //int Fs = 1000;
        int array_length = 8192;
        double freq_values[] = new double[4097];
        double local_max_red = 0;
        int red_local_max_index = 0;
        double respiratory_rate = 0;

        for (int i = 0; i < 8192; i++) {
            red_ppg_arr[i] = new Complex(ppg_array_all[i], 0);
        }

        // FFT, find dominant frequency between 0.05 and 0.7 Hz (3 to 42 breaths per minute), multiply dominant frequency with 60

        // Perform fft
        fft(red_ppg_arr);


        double[] abs_red_ppg_arr = new double[8192];
        for (int i = 0; i < 4097; i++) {
            abs_red_ppg_arr[i] = red_ppg_arr[i].abs()/array_length;
            freq_values[i] = (Fs * i)/array_length; // frequency values associated with each manipulated PPG waveform value

        }


        // double values since one-sided...
        for (int i = 1; i < 4096; i++) {
            abs_red_ppg_arr[i] = 2 * abs_red_ppg_arr[i];
        }

        double freq = 0;

        for (int i = 1; i < 4096; i++) {
            freq = freq_values[i];
            if ((freq >= 0.05) && (freq <= 0.7)) {
                if (local_max_red < abs_red_ppg_arr[i]) {
                    local_max_red = abs_red_ppg_arr[i];
                    red_local_max_index = i;
                }
            }
        }

        respiratory_rate = freq_values[red_local_max_index] * 60;

        return respiratory_rate;
    }
}
