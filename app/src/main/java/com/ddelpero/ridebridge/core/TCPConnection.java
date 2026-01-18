package com.ddelpero.ridebridge.core;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

import android.util.Log;

/**
 * TCP Socket implementation of TransportConnection
 * Used for testing with emulators or TCP-based connections
 */
public class TCPConnection implements TransportConnection {
    
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private BluetoothManager.OnMessageReceived incomingListener;
    private Thread readerThread;
    private boolean isConnected = false;
    
    @Override
    public void connect(String address) throws IOException {
        try {
            String[] parts = address.split(":");
            if (parts.length != 2) {
                throw new IOException("Invalid address format. Expected 'host:port'");
            }
            
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);
            
            Log.d("RideBridge", "TCP: Connecting to " + host + ":" + port);
            
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 2000);
            
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            
            isConnected = true;
            Log.d("RideBridge", "TCP: Connected successfully");
            
            // Start reader thread
            startReaderThread();
            
        } catch (IOException e) {
            isConnected = false;
            Log.e("RideBridge", "TCP: Connection failed: " + e.getMessage());
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
            Log.d("RideBridge", "TCP: Disconnected");
        } catch (IOException e) {
            Log.e("RideBridge", "TCP: Error during disconnect: " + e.getMessage());
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
        Log.d("RideBridge", "TCP: Message sent");
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
                    Log.d("RideBridge", "TCP: Received: " + line);
                    if (incomingListener != null) {
                        incomingListener.onReceived(line);
                    }
                }
            } catch (IOException e) {
                Log.d("RideBridge", "TCP: Reader thread ended: " + e.getMessage());
            } finally {
                isConnected = false;
            }
        });
        readerThread.start();
    }
}
