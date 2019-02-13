package com.XiaomiM365Locker.app;

import android.bluetooth.BluetoothDevice;

import com.polidea.rxandroidble2.RxBleConnection;

public class Device {
    private static final String UNKNOWN = "Unknown";
    /**
     * BluetoothDevice
     */
    private BluetoothDevice mDevice;
    /**
     * RSSI
     */
    private int mRssi;
    /**
     * Display Name
     */
    private String mDisplayName;
    private RxBleConnection.RxBleConnectionState connection_state;

    public Device(BluetoothDevice device, int rssi) {
        if (device == null) {
            throw new IllegalArgumentException("BluetoothDevice is null");
        }
        mDevice = device;
        mDisplayName = device.getName();
        if ((mDisplayName == null) || (mDisplayName.length() == 0)) {
            mDisplayName = UNKNOWN;
        }
        mRssi = rssi;
        this.connection_state = RxBleConnection.RxBleConnectionState.DISCONNECTED;
    }

    public BluetoothDevice getDevice() {
        return mDevice;
    }

    public int getRssi() {
        return mRssi;
    }

    public void setRssi(int rssi) {
        mRssi = rssi;
    }

    public void setConnection_state(RxBleConnection.RxBleConnectionState newstate)
    {
        this.connection_state = newstate;
    }

    public RxBleConnection.RxBleConnectionState getConnection_state() {
        return this.connection_state;
    }

    public String getDisplayName() {
        return mDisplayName;
    }

    public void setDisplayName(String displayName) {
        mDisplayName = displayName;
    }
}
