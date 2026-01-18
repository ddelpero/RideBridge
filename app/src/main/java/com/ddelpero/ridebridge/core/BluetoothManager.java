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
                android.util.Log.d("RideBridge", "SENDER: sendmessage...");

                android.util.Log.d("RideBridge", String.format(
                        "DEBUG STATE: isPipeOpen=%b, clientSocket=%s, out=%s, isConnected=%b, isClosed=%b",
                        isPipeOpen,
                        (clientSocket == null ? "NULL" : "EXISTS"),
                        (out == null ? "NULL" : "EXISTS"),
                        (clientSocket != null && clientSocket.isConnected()),
                        (clientSocket != null && clientSocket.isClosed())
                ));

                // RETRY LOOP: Keep trying to establish the pipe if it's dead
                while (isActive && (out == null || out.checkError() || clientSocket == null || !clientSocket.isConnected())) {
                    try {
                        android.util.Log.d("RideBridge", "SENDER: Establishing new persistent pipe...");
                        clientSocket = new java.net.Socket();
                        clientSocket.connect(new java.net.InetSocketAddress("10.0.2.2", 6000), 2000);

                        isPipeOpen = true;
                        out = new java.io.PrintWriter(new java.io.BufferedWriter(new java.io.OutputStreamWriter(clientSocket.getOutputStream())), true);

                        // START THE INBOUND READER ON THE SAME PIPE
                        new Thread(() -> {
                            try {
                                java.io.BufferedReader in = new java.io.BufferedReader(new java.io.InputStreamReader(clientSocket.getInputStream()));
                                String incoming;
                                while ((incoming = in.readLine()) != null) {
                                    android.util.Log.d("RideBridge", "SENDER: Received from Tablet: " + incoming);
                                    if (phoneResponseListener != null) {
                                        phoneResponseListener.onReceived(incoming);
                                    }
                                }
                            } catch (Exception e) {
                                android.util.Log.e("RideBridge", "SENDER: Read loop failed");
                                isPipeOpen = false;
                            }
                        }).start();

                        // Successful connection: break the while loop to proceed to sending
                        break;

                    } catch (Exception e) {
                        android.util.Log.d("RideBridge", "SENDER: Server not ready, retrying in 3s...");
                        clientSocket = null;
                        out = null;
                        isPipeOpen = false;
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException ignored) {
                        }

                        // If isActive became false while we were sleeping, exit the loop
                        if (!isActive) return;
                    }
                }

                // PUSH DATA: Now that the loop has finished (or was already connected)
                if (out != null) {
                    // Log message before sending (strip albumArt for readability)
                    String logMessage = message.replaceAll("\"albumArt\":\"[^\"]*\"", "\"albumArt\":\"[base64...]\"");
                    android.util.Log.d("RideBridge", "SENDER: Pushing message: " + logMessage);
                    
                    out.println(message);
                    android.util.Log.d("RideBridge", "SENDER: Actual data pushed to pipe: " + logMessage);
                } else {
                    android.util.Log.e("RideBridge", "SENDER: Failed to send - PrintWriter is null.");
                }

            } catch (Exception e) {
                android.util.Log.e("RideBridge", "SENDER: Send Error: " + e.getMessage());
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