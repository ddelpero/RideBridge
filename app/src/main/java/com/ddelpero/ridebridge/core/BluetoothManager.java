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

    // Add these class-level variables to BluetoothManager
//    private java.net.Socket persistentSocket;
//    private java.io.PrintWriter persistentOut;

    private PrintWriter tabletToPhoneOut; // The return path for the Tablet
    private Socket clientSocket;
    private PrintWriter out;
    private boolean isPipeOpen = false; // NEW: Manual flag we control

    private boolean isActive = false; // The Master Switch

    public void setServiceActive(boolean active) {
        this.isActive = active;
        // If we are turning it off, clean up the resources
        if (!active) {
            closeConnection();
        }
    }

    private void closeConnection() {
        try {
            isPipeOpen = false;
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
            out = null;
            clientSocket = null;
        } catch (Exception e) {
            Log.e("RideBridge", "SENDER: Error during shutdown: " + e.getMessage());
        }
    }

    // This mimics "Bluetooth Connect"
//    public void connectToDevice(String ip, int port) {
//        new Thread(() -> {
//            try {
//                persistentSocket = new java.net.Socket(ip, port);
//                persistentOut = new java.io.PrintWriter(new java.io.BufferedWriter(
//                        new java.io.OutputStreamWriter(persistentSocket.getOutputStream())), true);
//                android.util.Log.d("RideBridge", "STREAM: Connected to pipe.");
//            } catch (Exception e) {
//                android.util.Log.e("RideBridge", "STREAM: Connection failed: " + e.getMessage());
//            }
//        }).start();
//    }

    // Now sendMessage just pushes data into the existing pipe
    private OnMessageReceived phoneResponseListener;
    public void sendMessage(String message, OnMessageReceived listener) {
        this.phoneResponseListener = listener; // Save the callback

        if (!isActive) {
            Log.d("RideBridge", "SENDER: Service not started. Blocking message.");
            return;
        }

        new Thread(() -> {
            try {
                Log.d("RideBridge", "SENDER: sendmessage...");
                //if (clientSocket == null || clientSocket.isClosed() || !clientSocket.isConnected()) {
                //if (clientSocket == null || !clientSocket.isConnected() || clientSocket.isClosed() || out == null) {

                Log.d("RideBridge", String.format(
                        "DEBUG STATE: isPipeOpen=%b, clientSocket=%s, out=%s, isConnected=%b, isClosed=%b",
                        isPipeOpen,
                        (clientSocket == null ? "NULL" : "EXISTS"),
                        (out == null ? "NULL" : "EXISTS"),
                        (clientSocket != null && clientSocket.isConnected()),
                        (clientSocket != null && clientSocket.isClosed())
                ));

                if (out == null || out.checkError() || clientSocket == null || !clientSocket.isConnected()) {
                    Log.d("RideBridge", "SENDER: Establishing new persistent pipe...");
                    clientSocket = new Socket();
                    clientSocket.connect(new InetSocketAddress("10.0.2.2", 6000), 2000);
                    // Use 'true' for auto-flush
                    isPipeOpen = true;
                    out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream())), true);

                    // START THE INBOUND READER ON THE SAME PIPE
                    new Thread(() -> {
                        try {
                            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                            String incoming;
                            while ((incoming = in.readLine()) != null) {
                                Log.d("RideBridge", "SENDER: Received from Tablet: " + incoming);
                                // Execute the callback passed from MainActivity
                                if (phoneResponseListener != null) {
                                    phoneResponseListener.onReceived(incoming);
                                }
                            }
                        } catch (Exception e) { Log.e("RideBridge", "SENDER: Read loop failed"); }
                    }).start();
                }

                // CHANGE: Only send if 'out' was successfully initialized
                if (out != null) {
                    out.println(message);
                    Log.d("RideBridge", "SENDER: Actual data pushed to pipe: " + message);
                } else {
                    Log.e("RideBridge", "SENDER: Failed to send - PrintWriter is null.");
                }

            } catch (Exception e) {
                Log.e("RideBridge", "SENDER: Send Error: " + e.getMessage());
                clientSocket = null;
                isPipeOpen = false;
                out = null;
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
                tabletToPhoneOut.println(command);
                Log.d("RideBridge", "TABLET: Sent command: " + command);
            }).start();
        } else {
            Log.e("RideBridge", "TABLET: No phone connected to send command to.");
        }
    }

}