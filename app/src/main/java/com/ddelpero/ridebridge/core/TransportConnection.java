package com.ddelpero.ridebridge.core;

import java.io.IOException;

/**
 * Abstract interface for different transport mechanisms (TCP sockets, Bluetooth, etc.)
 * Allows swapping between TCP for testing and Bluetooth for real deployment
 */
public interface TransportConnection {
    
    /**
     * Connect to the remote device
     * @param address Connection address (e.g., "10.0.2.2:6000" for TCP, MAC address for Bluetooth)
     * @throws IOException if connection fails
     */
    void connect(String address) throws IOException;
    
    /**
     * Disconnect from the remote device
     */
    void disconnect();
    
    /**
     * Send a message to the remote device
     * @param message The message to send
     * @throws IOException if send fails
     */
    void sendMessage(String message) throws IOException;
    
    /**
     * Receive a message from the remote device (blocking)
     * @return The received message
     * @throws IOException if receive fails
     */
    String receiveMessage() throws IOException;
    
    /**
     * Check if the connection is active
     * @return true if connected, false otherwise
     */
    boolean isConnected();
    
    /**
     * Set a listener for incoming messages
     * @param listener Callback for received messages
     */
    void setIncomingMessageListener(BluetoothManager.OnMessageReceived listener);
}
