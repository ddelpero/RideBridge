package com.ddelpero.ridebridge.core;

import android.content.Context;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Manages Bluetooth/TCP connection lifecycle, reconnection, and status tracking
 */
public class ConnectionManager {
    
    private Context context;
    private BluetoothManager bluetoothManager;
    private SharedPreferences prefs;
    
    // Connection state
    public enum ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED
    }
    
    private ConnectionStatus status = ConnectionStatus.DISCONNECTED;
    private OnConnectionStatusChanged statusListener;
    
    // Bluetooth event receiver
    private BroadcastReceiver bluetoothReceiver;
    private boolean isRegistered = false;
    
    // Connection parameters
    private String selectedDeviceMac;
    private boolean useBluetoothMode;
    
    public ConnectionManager(Context context, BluetoothManager bluetoothManager) {
        this.context = context;
        this.bluetoothManager = bluetoothManager;
        this.prefs = context.getSharedPreferences("RideBridgePrefs", Context.MODE_PRIVATE);
        
        // Auto-detect emulator and set defaults
        if (EmulatorDetector.isEmulator()) {
            this.useBluetoothMode = false; // Force TCP on emulator
            Log.d("RideBridge", "CONN: Emulator detected, using TCP mode");
        } else {
            this.useBluetoothMode = prefs.getBoolean("use_bluetooth_mode", true); // Default to Bluetooth
        }
        
        // Load saved device MAC
        this.selectedDeviceMac = prefs.getString("selected_bt_device", null);
        
        // Update BluetoothManager with settings
        bluetoothManager.setUseTCP(!useBluetoothMode);
        if (!useBluetoothMode) {
            bluetoothManager.setRemoteAddress("10.0.2.2:6000");
        } else if (selectedDeviceMac != null) {
            bluetoothManager.setRemoteAddress(selectedDeviceMac);
        }
    }
    
    /**
     * Initialize and check connection on startup
     */
    public void initializeConnection() {
        Log.d("RideBridge", "CONN: Initializing connection");
        
        // Check current connection status
        if (useBluetoothMode && selectedDeviceMac != null) {
            setStatus(ConnectionStatus.CONNECTING);
            attemptConnection();
            registerBluetoothReceiver();
        } else if (!useBluetoothMode) {
            setStatus(ConnectionStatus.CONNECTING);
            attemptConnection();
        } else {
            Log.d("RideBridge", "CONN: No device selected, waiting for user configuration");
            setStatus(ConnectionStatus.DISCONNECTED);
        }
    }
    
    /**
     * Attempt to connect to the remote device
     */
    private void attemptConnection() {
        new Thread(() -> {
            try {
                Log.d("RideBridge", "CONN: Attempting connection to " + (useBluetoothMode ? selectedDeviceMac : "TCP"));
                
                // Try to connect with timeout
                long startTime = System.currentTimeMillis();
                long timeout = 10000; // 10 seconds
                
                while (System.currentTimeMillis() - startTime < timeout) {
                    try {
                        bluetoothManager.sendMessage("{\"type\":\"ping\"}", response -> {
                            Log.d("RideBridge", "CONN: Connection successful");
                            setStatus(ConnectionStatus.CONNECTED);
                        });
                        
                        // Give it a moment to connect
                        Thread.sleep(1000);
                        
                        // If we got here, connection succeeded
                        setStatus(ConnectionStatus.CONNECTED);
                        return;
                        
                    } catch (Exception e) {
                        Thread.sleep(500);
                    }
                }
                
                // Timeout reached
                Log.d("RideBridge", "CONN: Connection timeout");
                setStatus(ConnectionStatus.DISCONNECTED);
                
            } catch (Exception e) {
                Log.e("RideBridge", "CONN: Connection attempt failed: " + e.getMessage());
                setStatus(ConnectionStatus.DISCONNECTED);
            }
        }).start();
    }
    
    /**
     * Register receiver for Bluetooth connection events
     */
    private void registerBluetoothReceiver() {
        if (isRegistered) return;
        
        bluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                
                if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && device.getAddress().equals(selectedDeviceMac)) {
                        Log.d("RideBridge", "CONN: Bluetooth device connected: " + device.getName());
                        setStatus(ConnectionStatus.CONNECTING);
                        attemptConnection();
                    }
                } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (device != null && device.getAddress().equals(selectedDeviceMac)) {
                        Log.d("RideBridge", "CONN: Bluetooth device disconnected: " + device.getName());
                        setStatus(ConnectionStatus.DISCONNECTED);
                    }
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        
        context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_EXPORTED);
        isRegistered = true;
        Log.d("RideBridge", "CONN: Bluetooth receiver registered");
    }
    
    /**
     * Unregister Bluetooth receiver
     */
    public void unregisterBluetoothReceiver() {
        if (isRegistered && bluetoothReceiver != null) {
            try {
                context.unregisterReceiver(bluetoothReceiver);
                isRegistered = false;
                Log.d("RideBridge", "CONN: Bluetooth receiver unregistered");
            } catch (Exception e) {
                Log.e("RideBridge", "CONN: Error unregistering receiver: " + e.getMessage());
            }
        }
    }
    
    /**
     * User selected a device in settings
     */
    public void selectDevice(String deviceMac) {
        this.selectedDeviceMac = deviceMac;
        prefs.edit().putString("selected_bt_device", deviceMac).apply();
        bluetoothManager.setRemoteAddress(deviceMac);
        
        Log.d("RideBridge", "CONN: Device selected: " + deviceMac);
        
        // Re-register Bluetooth receiver if switching to Bluetooth mode
        if (useBluetoothMode) {
            unregisterBluetoothReceiver();
            registerBluetoothReceiver();
            setStatus(ConnectionStatus.CONNECTING);
            attemptConnection();
        }
    }
    
    /**
     * User toggled TCP/Bluetooth mode
     */
    public void setBluetoothMode(boolean enabled) {
        this.useBluetoothMode = enabled;
        bluetoothManager.setUseTCP(!enabled);
        prefs.edit().putBoolean("use_bluetooth_mode", enabled).apply();
        
        if (enabled) {
            Log.d("RideBridge", "CONN: Switched to Bluetooth mode");
            registerBluetoothReceiver();
        } else {
            Log.d("RideBridge", "CONN: Switched to TCP mode");
            unregisterBluetoothReceiver();
            bluetoothManager.setRemoteAddress("10.0.2.2:6000");
        }
        
        setStatus(ConnectionStatus.CONNECTING);
        attemptConnection();
    }
    
    /**
     * Manual connect button pressed
     */
    public void manualConnect() {
        Log.d("RideBridge", "CONN: Manual connect requested");
        setStatus(ConnectionStatus.CONNECTING);
        attemptConnection();
    }
    
    /**
     * Update connection status and notify listeners
     */
    private void setStatus(ConnectionStatus newStatus) {
        if (this.status != newStatus) {
            this.status = newStatus;
            Log.d("RideBridge", "CONN: Status changed to " + newStatus);
            
            if (statusListener != null) {
                statusListener.onStatusChanged(newStatus);
            }
        }
    }
    
    public ConnectionStatus getStatus() {
        return status;
    }
    
    public void setStatusListener(OnConnectionStatusChanged listener) {
        this.statusListener = listener;
    }
    
    public boolean isUsingBluetoothMode() {
        return useBluetoothMode;
    }
    
    public String getSelectedDeviceMac() {
        return selectedDeviceMac;
    }
    
    public interface OnConnectionStatusChanged {
        void onStatusChanged(ConnectionStatus status);
    }
    
    public void cleanup() {
        unregisterBluetoothReceiver();
    }
}
