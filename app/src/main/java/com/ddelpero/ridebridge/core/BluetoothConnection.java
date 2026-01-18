package com.ddelpero.ridebridge.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

/**
 * Bluetooth Socket implementation of TransportConnection
 * Used for real Bluetooth communication between devices
 */
public class BluetoothConnection implements TransportConnection {
    
    private BluetoothSocket socket;
    private PrintWriter out;
    private BufferedReader in;
    private BluetoothManager.OnMessageReceived incomingListener;
    private Thread readerThread;
    private boolean isConnected = false;
    private static final java.util.UUID SERIAL_PORT_UUID = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    
    @Override
    public void connect(String address) throws IOException {
        try {
            Log.d("RideBridge", "BT: Connecting to device " + address);
            
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) {
                throw new IOException("Bluetooth adapter not available");
            }
            
            BluetoothDevice device = adapter.getRemoteDevice(address);
            if (device == null) {
                throw new IOException("Device not found: " + address);
            }
            
            // Create socket
            socket = device.createRfcommSocketToServiceRecord(SERIAL_PORT_UUID);
            
            // Cancel discovery to speed up connection
            adapter.cancelDiscovery();
            
            // Connect
            socket.connect();
            
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            isConnected = true;
            Log.d("RideBridge", "BT: Connected to " + address);
            
            // Start reader thread
            startReaderThread();
            
        } catch (IOException e) {
            isConnected = false;
            Log.e("RideBridge", "BT: Connection failed: " + e.getMessage());
            throw e;
        }
    }
    
    @Override
    public void disconnect() {
        try {
            isConnected = false;
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
            Log.d("RideBridge", "BT: Disconnected");
        } catch (IOException e) {
            Log.e("RideBridge", "BT: Error during disconnect: " + e.getMessage());
        }
    }
    
    @Override
    public void sendMessage(String message) throws IOException {
        if (!isConnected || out == null) {
            throw new IOException("Not connected");
        }
        
        out.println(message);
        if (out.checkError()) {
            throw new IOException("PrintWriter error detected after sending");
        }
        Log.d("RideBridge", "BT: Message sent");
    }
    
    @Override
    public String receiveMessage() throws IOException {
        if (!isConnected || in == null) {
            throw new IOException("Not connected");
        }
        return in.readLine();
    }
    
    @Override
    public boolean isConnected() {
        return isConnected && socket != null && socket.isConnected();
    }
    
    @Override
    public void setIncomingMessageListener(BluetoothManager.OnMessageReceived listener) {
        this.incomingListener = listener;
    }
    
    private void startReaderThread() {
        readerThread = new Thread(() -> {
            try {
                String line;
                while (isConnected && (line = in.readLine()) != null) {
                    Log.d("RideBridge", "BT: Received: " + line);
                    if (incomingListener != null) {
                        incomingListener.onReceived(line);
                    }
                }
            } catch (IOException e) {
                Log.d("RideBridge", "BT: Reader thread ended: " + e.getMessage());
            } finally {
                isConnected = false;
            }
        });
        readerThread.start();
    }
}
