package com.ddelpero.ridebridge.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

import android.util.Log;

import java.io.*;
import java.net.*;

import android.util.Log;


public class BluetoothManager {

    private PrintWriter tabletToPhoneOut; // The return path for the Tablet
    
    // Transport abstraction - can be TCP or Bluetooth
    private TransportConnection transport;
    
    // Configuration
    private boolean useTCP = true; // Default to TCP for testing; switch to false for Bluetooth
    private String remoteAddress = "10.0.2.2:6000"; // TCP: "host:port", BT: "MAC_ADDRESS"
    
    private boolean isActive = false; // The Master Switch

public void setTransport(TransportConnection transport) {
        this.transport = transport;
        Log.d("RideBridge", "Transport set to: " + (transport instanceof TCPConnection ? "TCP" : "Bluetooth"));
    }
    
    public void setUseTCP(boolean useTCP) {
        this.useTCP = useTCP;
    }
    
    public void setRemoteAddress(String address) {
        this.remoteAddress = address;
    }

    public void setServiceActive(boolean active) {
        this.isActive = active;
        // If we are turning it off, clean up the resources
        if (!active) {
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            if (transport != null) {
                transport.disconnect();
            }
        } catch (Exception e) {
            Log.e("RideBridge", "SENDER: Error during shutdown: " + e.getMessage());
        }
    }

    // Now sendMessage just pushes data into the existing pipe
    private OnMessageReceived phoneResponseListener;

    public void sendMessage(String message, OnMessageReceived listener) {
        // Only update the listener if a non-null listener is provided (don't overwrite command handlers with null)
        if (listener != null) {
            this.phoneResponseListener = listener;
        }

        if (!isActive) {
            android.util.Log.d("RideBridge", "SENDER: Service not started. Blocking message.");
            return;
        }

        new Thread(() -> {
            try {
                // Initialize transport if needed
                if (transport == null) {
                    if (useTCP) {
                        transport = new TCPConnection();
                    } else {
                        transport = new BluetoothConnection();
                    }
                    transport.setIncomingMessageListener(phoneResponseListener);
                }
                
                // Establish connection if not connected
                if (!transport.isConnected()) {
                    Log.d("RideBridge", "SENDER: Establishing connection to " + remoteAddress);
                    transport.connect(remoteAddress);
                }
                
                // Send message
                String logMessage = message.replaceAll("\"albumArt\":\"[^\"]*\"", "\"albumArt\":\"[base64...]\"");
                Log.d("RideBridge", "SENDER: Pushing message: " + logMessage);
                
                transport.sendMessage(message);
                Log.d("RideBridge", "SENDER: Actual data pushed to pipe: " + logMessage);

            } catch (Exception e) {
                Log.e("RideBridge", "SENDER: Send Error: " + e.getMessage());
                transport = null;
            }
        }).start();
    }

    public interface MessageListener {
        void onMessageReceived(String data);
    }

    @FunctionalInterface
    public interface OnMessageReceived {
        void onReceived(String data);
    }

    public void startEmulatorListener(OnMessageReceived listener, String roleName) {
        new Thread(() -> {
            try {
                android.util.Log.d("RideBridge", "DEBUG [" + roleName + "]: Starting ServerSocket on port 5000...");

                java.net.ServerSocket serverSocket = new java.net.ServerSocket();
                // This line helps prevent "Address already in use" errors
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new java.net.InetSocketAddress(6000));
                Log.d("RideBridge", "RECEIVER: Server online.");

                while (true) {
                    java.net.Socket clientSocket = serverSocket.accept();
                    Log.d("RideBridge", "RECEIVER: Phone connected.");

                    // Initialize the return path (Tablet -> Phone)
                    tabletToPhoneOut = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())), true);

                    java.io.BufferedReader in = new java.io.BufferedReader(
                            new java.io.InputStreamReader(clientSocket.getInputStream())
                    );

                    String line;
                    // This loop keeps the connection alive until the other side disconnects
                    while ((line = in.readLine()) != null) {
                        android.util.Log.d("RideBridge", "STREAM: Received: " + line);
                        listener.onReceived(line);
                    }
                    android.util.Log.d("RideBridge", "STREAM: Pipe closed by peer.");
                    tabletToPhoneOut = null;
                }
            } catch (Exception e) {
                android.util.Log.e("RideBridge", "DEBUG [" + roleName + "]: ServerSocket Failed: " + e.getMessage());
            }
        }).start();
    }

    // Tablet calls this to send commands back to the Phone
    public void sendCommandToPhone(String command) {
        if (tabletToPhoneOut != null) {
            new Thread(() -> {
                try {
                    tabletToPhoneOut.println(command);
                    if (tabletToPhoneOut.checkError()) {
                        Log.e("RideBridge", "TABLET: PrintWriter error detected after sending: " + command);
                    } else {
                        Log.d("RideBridge", "TABLET: Sent command: " + command);
                    }
                } catch (Exception e) {
                    Log.e("RideBridge", "TABLET: Exception sending command '" + command + "': " + e.getMessage());
                }
            }).start();
        } else {
            Log.e("RideBridge", "TABLET: No phone connected (tabletToPhoneOut is null). Port forward may not be working.");
        }
    }

}