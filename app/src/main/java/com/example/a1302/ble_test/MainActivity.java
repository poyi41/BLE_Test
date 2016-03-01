package com.example.a1302.ble_test;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class MainActivity extends AppCompatActivity implements BluetoothAdapter.LeScanCallback {
    private static final String TAG = "BLEDevice";
    private Button btnConnect;
    private Button btnUpdateData;
    private TextView tvDeviceName;
    private static final int REQUEST_ENABLE_BT = 1;
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    private BluetoothAdapter mBTAdapter;
    private BluetoothDevice mDevice;
    private BluetoothGatt mConnGatt;
    private boolean mIsScanning, enabled = true;
    private int mStatus, GCcount = 0;
    private DeviceAdapter mDeviceAdapter;
    private ListView deviceListView, lvUpdateData;
    private ArrayAdapter<String> adUpdateData;
    final Context context = this;
    private AlertDialog dialog;
    private ScannedDevice item;
    private Queue<BluetoothGattDescriptor> descriptorWriteQueue = new LinkedList<BluetoothGattDescriptor>();
    private Queue<BluetoothGattCharacteristic> characteristicReadQueue = new LinkedList<BluetoothGattCharacteristic>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnConnect = (Button)findViewById(R.id.btnConnect);
        btnUpdateData = (Button)findViewById(R.id.btnUpdateData);
        lvUpdateData = (ListView)findViewById(R.id.lvGlucoseData);
        adUpdateData = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1);
        tvDeviceName = (TextView)findViewById(R.id.tvDeviceName);
        init();
    }

    public void myClickHandler(View view){
        switch(view.getId()){
            case R.id.btnScan:
                startScan();
                Toast.makeText(this, "Scan", Toast.LENGTH_SHORT).show();
                dialog_scaneddevice();
                break;
            case R.id.btnConnect:
                try{
                    BluetoothGattCharacteristic charGC =
                            mConnGatt.getService(UUID.fromString(BleUuid.SERVICE_NUS_BASE_UUID))
                                    .getCharacteristic(UUID.fromString(BleUuid.CHAR_CHOLEST_MEASUREMENT_STRING));
                    readCharacteristic(charGC);
                    //unpairDevice(mDevice);

                }catch(Exception e){
                    e.printStackTrace();
                }
                break;
            case R.id.btnUpdateData:
                try{
                    writeRACPchar();
                }catch(Exception e){
                    e.printStackTrace();
                }
                break;
        }
    }

    private void writeRACPchar(){
        //***PUSH 0x01 TO RECORD ACCESS CONTROL POINT
        BluetoothGattCharacteristic writeRACPchar =
                mConnGatt.getService(UUID.fromString(BleUuid.SERVICE_GLUCOSE))
                        .getCharacteristic(UUID.fromString(BleUuid.CHAR_RECORD_ACCESS_CONTROL_POINT_STRING));
        byte[] data = new byte[2];
        data[0] = (byte)0x01;
        data[1] = (byte)0x01;
        writeRACPchar.setValue(data);
        mConnGatt.writeCharacteristic(writeRACPchar);
    }

    private void init() {
        btnConnect.setEnabled(false);
        btnUpdateData.setEnabled(false);
        // BLE check
        if (!BleUtil.isBLESupported(this)) {
            Toast.makeText(this,"ble not support", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // BT check
        BluetoothManager manager = BleUtil.getManager(this);
        if (manager != null) {
            mBTAdapter = manager.getAdapter();

            Intent turnOnIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(turnOnIntent, REQUEST_ENABLE_BT);

            Toast.makeText(getApplicationContext(),"Bluetooth turned on" ,
                    Toast.LENGTH_LONG).show();
        }
        if (mBTAdapter == null) {
            Toast.makeText(this, "ble unavailable", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // init listview & sacned dialog
        View viewScaned=View.inflate(this,R.layout.scaned_list, deviceListView);
        deviceListView = (ListView) viewScaned.findViewById(R.id.list);
        mDeviceAdapter = new DeviceAdapter(this, R.layout.listitem_device,
                new ArrayList<ScannedDevice>());
        deviceListView.setAdapter(mDeviceAdapter);
        deviceListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterview, View view, int position, long id) {
                item = mDeviceAdapter.getItem(position);
                if (item != null) {
                    connect2Gatt();
                    dialog.cancel();
                    // stop before change Activity
                    stopScan();
                }
            }
        });
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(viewScaned).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // TODO Auto-generated method stub
                stopScan();
                dialog.cancel();
            }
        });
        dialog = builder.create();
        stopScan();
    }

    public void dialog_scaneddevice(){
        dialog.show();
    }

    @Override
    public void onLeScan(final BluetoothDevice newDeivce, final int newRssi,
                         final byte[] newScanRecord) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDeviceAdapter.update(newDeivce, newRssi, newScanRecord);
            }
        });
    }

    private void startScan() {
        if ((mBTAdapter != null) && (!mIsScanning)) {
            mBTAdapter.startLeScan(this);
            mIsScanning = true;
            setProgressBarIndeterminateVisibility(true);
            invalidateOptionsMenu();
        }
    }

    private void stopScan() {
        if (mBTAdapter != null) {
            mBTAdapter.stopLeScan(this);
        }
        mIsScanning = false;
        setProgressBarIndeterminateVisibility(false);
        invalidateOptionsMenu();
    }

    private void connect2Gatt(){
        // check BluetoothDevice
        if (mDevice == null) {
            mDevice = item.getDevice();
            if (mDevice == null) {
                finish();
                return;
            }
        }
        // connect to Gatt
        if ((mConnGatt == null)	&& (mStatus == BluetoothProfile.STATE_DISCONNECTED))
        {
            // try to connect
            mConnGatt = mDevice.connectGatt(this, false, mGattcallback);
            mStatus = BluetoothProfile.STATE_CONNECTING;
            tvDeviceName.setText(mDevice.getName());
            // btnConnect.setText("Disconnect");
        } else {
            if (mConnGatt != null) {
                // re-connect and re-discover Services
                mConnGatt.connect();
                mConnGatt.discoverServices();
            } else {
                Log.e(TAG, "state error");
                finish();
                return;
            }
        }
    }

    //connect to gatt
    private final BluetoothGattCallback mGattcallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mStatus = newState;
                mConnGatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                mStatus = newState;
                runOnUiThread(new Runnable() {
                    public void run() {
                        //未連接時的操作
                        close();
                    }
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            for (BluetoothGattService service : gatt.getServices()) {
                if ((service == null) || (service.getUuid() == null)) {
                    continue;
                }
                if (BleUuid.SERVICE_GLUCOSE.equalsIgnoreCase(service
                        .getUuid().toString())) {

                    BluetoothGattCharacteristic charGM =
                            mConnGatt.getService(UUID.fromString(BleUuid.SERVICE_GLUCOSE))
                                    .getCharacteristic(UUID.fromString(BleUuid.CHAR_GLUCOSE_MEASUREMENT_STRING));
                    mConnGatt.setCharacteristicNotification(charGM, enabled);
                    BluetoothGattDescriptor descGM = charGM.getDescriptor(UUID.fromString(BleUuid.CHAR_CLIENT_CHARACTERISTIC_CONFIG_STRING));
                    descGM.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    writeGattDescriptor(descGM);
/*
                    BluetoothGattCharacteristic charGC =
                            mConnGatt.getService(UUID.fromString(BleUuid.SERVICE_NUS_BASE_UUID))
                                    .getCharacteristic(UUID.fromString(BleUuid.CHAR_CHOLEST_MEASUREMENT_STRING));
                    mConnGatt.setCharacteristicNotification(charGC, enabled);
                    BluetoothGattDescriptor descGC = charGC.getDescriptor(UUID.fromString(BleUuid.CHAR_CLIENT_CHARACTERISTIC_CONFIG_STRING));
                    descGC.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    writeGattDescriptor(descGC);
*/
                    BluetoothGattCharacteristic charRACP =
                            mConnGatt.getService(UUID.fromString(BleUuid.SERVICE_GLUCOSE))
                                    .getCharacteristic(UUID.fromString(BleUuid.CHAR_RECORD_ACCESS_CONTROL_POINT_STRING));
                    mConnGatt.setCharacteristicNotification(charRACP, enabled);
                    BluetoothGattDescriptor descRACP = charRACP.getDescriptor(UUID.fromString(BleUuid.CHAR_CLIENT_CHARACTERISTIC_CONFIG_STRING));
                    descRACP.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                    writeGattDescriptor(descRACP);

                    runOnUiThread(new Runnable() {
                        public void run() {
                            btnUpdateData.setEnabled(true);
                            btnConnect.setEnabled(true);
                        }
                    });
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            characteristicReadQueue.remove();
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
            else{
                Log.d(TAG, "onCharacteristicRead error: " + status);
            }
            if(characteristicReadQueue.size() > 0)
                mConnGatt.readCharacteristic(characteristicReadQueue.element());
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Callback: Wrote GATT Descriptor successfully.");
            }
            else{
                Log.d(TAG, "Callback: Error writing GATT Descriptor: "+ status);
            }
            descriptorWriteQueue.remove();  //pop the item that we just finishing writing
            //if there is more to write, do it!
            if(descriptorWriteQueue.size() > 0)
                mConnGatt.writeDescriptor(descriptorWriteQueue.element());
            else if(characteristicReadQueue.size() > 0)
                mConnGatt.readCharacteristic(characteristicReadQueue.element());
        }
    };

    public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (mBTAdapter == null || mConnGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        //BluetoothGattService s = mConnGatt.getService(UUID.fromString(BleUuid.SERVICE_NUS_BASE_UUID));
        //BluetoothGattCharacteristic c = s.getCharacteristic(UUID.fromString(characteristicName));
        //put the characteristic into the read queue
        characteristicReadQueue.add(characteristic);
        //if there is only 1 item in the queue, then read it.  If more than 1, we handle asynchronously in the callback above
        //GIVE PRECEDENCE to descriptor writes.  They must all finish first.
        if((characteristicReadQueue.size() == 1) && (descriptorWriteQueue.size() == 0))
            mConnGatt.readCharacteristic(characteristic);
    }

    private void writeGattDescriptor(BluetoothGattDescriptor d){
        //put the descriptor into the write queue
        descriptorWriteQueue.add(d);
        //if there is only 1 item in the queue, then write it.  If more than 1, we handle asynchronously in the callback above
        if(descriptorWriteQueue.size() == 1){
            mConnGatt.writeDescriptor(d);
        }
    }

    private void broadcastUpdate(final String action,
                                 final BluetoothGattCharacteristic characteristic) {
        //final Intent intent = new Intent(action);
        if(BleUuid.CHAR_GLUCOSE_MEASUREMENT_STRING
                .equalsIgnoreCase(characteristic.getUuid().toString())){
            // For all other profiles, writes the data formatted in HEX.
            final byte[] dataGM = characteristic.getValue();
            final String strGM = DecadeGMdata(dataGM);
            displayResult(strGM);
        } else if(BleUuid.CHAR_RECORD_ACCESS_CONTROL_POINT_STRING
                .equalsIgnoreCase(characteristic.getUuid().toString())){
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                displayResult(stringBuilder.toString());

                for(int i = 0;i < 200;i++){
                    BluetoothGattCharacteristic charGC =
                            mConnGatt.getService(UUID.fromString(BleUuid.SERVICE_NUS_BASE_UUID))
                                    .getCharacteristic(UUID.fromString(BleUuid.CHAR_CHOLEST_MEASUREMENT_STRING));
                    readCharacteristic(charGC);
                        //Thread.sleep(10);
                }
            }
            //Toast.makeText(getApplicationContext(), "RACP Notify...", Toast.LENGTH_SHORT).show();
        } else if(BleUuid.CHAR_CHOLEST_MEASUREMENT_STRING
                .equalsIgnoreCase(characteristic.getUuid().toString())){
            final byte[] dataGM = characteristic.getValue();
            final String strGM = DecadeGCdata(dataGM);
            displayResult(GCcount + " " + strGM);
            GCcount++;
        }else {
            // For all other profiles, writes the data formatted in HEX.
            final byte[] data = characteristic.getValue();
            if (data != null && data.length > 0) {
                final StringBuilder stringBuilder = new StringBuilder(data.length);
                for(byte byteChar : data)
                    stringBuilder.append(String.format("%02X ", byteChar));
                displayResult(stringBuilder.toString());
            }
        }
    }

    private String DecadeGCdata(byte[] dataByte){
        String result = null;
        // For all other profiles, writes the data formatted in HEX.
        if (dataByte != null && dataByte.length > 0) {
            int unit = (dataByte[0] & 0xFF)/128;
            int year = (dataByte[0] & 0x7F) + 2000;
            int month = dataByte[1] >> 4;
            int day = dataByte[2] & 0x1F;
            int hour = (dataByte[3] & 0x03)*4 + (dataByte[2] & 0xFF)/32;
            int min = (dataByte[3] & 0xFF)/4;
            int reading = (dataByte[5] & 0xFF)*256 + (dataByte[4] & 0xFF);
            String unitStr = "";
            if (unit == 1) {
                unitStr = "mg/dL";
            } else {
                unitStr = "mmol/L";
            }
            result = year + "/" + month + "/" + day + " " +
                    hour + ":" + min + " " +
                    reading + " " + unitStr;
        }
        return result;
    }

    private String DecadeGMdata(byte[] dataByte){
        String result = null;
        // For all other profiles, writes the data formatted in HEX.
        if (dataByte != null && dataByte.length > 0) {
            final StringBuilder stringBuilder = new StringBuilder(dataByte.length);
            for(byte byteChar : dataByte)
                stringBuilder.append(String.format("%02X ", byteChar));
            result = stringBuilder.toString();
        }
        return result;
    }

    private void displayResult(final String result){
        runOnUiThread(new Runnable() {
            public void run() {
                adUpdateData.add(result);
                adUpdateData.notifyDataSetChanged();
                lvUpdateData.setAdapter(adUpdateData);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
    }

    public void close() {
        if (mConnGatt == null) {
            return;
        }
        unpairDevice(mDevice);
        mStatus = BluetoothProfile.STATE_DISCONNECTED;
        mConnGatt.close();
        mConnGatt = null;
        mDevice = null;
        tvDeviceName.setText("NULL");
//        btnConnect.setText("Connect");
        adUpdateData.clear();
        lvUpdateData.setAdapter(adUpdateData);
        btnUpdateData.setEnabled(false);
        btnConnect.setEnabled(false);
    }

    private void unpairDevice(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("removeBond", (Class[]) null);
            method.invoke(device, (Object[]) null);

            } catch (Exception e) {
            e.printStackTrace();
            }
        }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            close();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
