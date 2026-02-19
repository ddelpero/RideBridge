package com.ddelpero.ridebridge.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

class BTClient(context: Context) : DataTransport {
    private val TAG = "BTClient"
    private val appContext = context.applicationContext // Safe context for background work
    private var isRunning = false

    private val _isConnected = MutableStateFlow(false)
    override val isConnected = _isConnected.asStateFlow()

    private var socket: BluetoothSocket? = null
    private var out: PrintWriter? = null
    private val sendChannel = Channel<String>(Channel.BUFFERED)
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    init {
        _isConnected.value = false
    }

    @SuppressLint("MissingPermission")
    override fun start(
        onConnectionStateChanged: (Boolean) -> Unit,
        onMessageReceived: (String) -> Unit
    ) {
        isRunning = true
        val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter

        CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    // Search for the tablet by the name defined in BTServer
                    // val device = adapter.bondedDevices.find { it.name == "RideBridge" }
                    // if (device == null) {
                    //     Log.e(
                    //         TAG,
                    //         "Tablet 'RideBridge' not found. Ensure it is paired. Retrying..."
                    //     )
                    //     delay(5000)
                    //     continue
                    // }
                    //
                    // Log.d(TAG, "Attempting connection to ${device.name}")
                    // socket = device.createRfcommSocketToServiceRecord(uuid)
                    // socket?.connect()

                    val prefs =
                        appContext.getSharedPreferences("RideBridgePrefs", Context.MODE_PRIVATE)
                    val macAddress = prefs.getString("tablet_mac_address", null)

                    if (macAddress != null) {
                        val device = adapter.getRemoteDevice(macAddress) // Direct connection!
                        socket = device.createRfcommSocketToServiceRecord(uuid)
                        socket?.connect()
                    }

                    _isConnected.value = true
                    onConnectionStateChanged(true)

                    out = PrintWriter(socket!!.outputStream, true)
                    val input = BufferedReader(InputStreamReader(socket!!.inputStream))

                    // Launch a separate coroutine to handle SENDING messages (Matches TCPClient)
                    val sendJob = launch {
                        try {
                            for (message in sendChannel) {
                                out?.println(message)
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "SendJob terminated")
                        }
                    }

                    try {
                        // Receiving loop (Matches TCPClient)
                        while (isRunning && socket?.isConnected == true) {
                            val message = input.readLine() ?: break
                            onMessageReceived(message)
                        }
                    } finally {
                        sendJob.cancel()
                    }

                } catch (e: Exception) {
                    _isConnected.value = false
                    onConnectionStateChanged(false)
                    Log.e(TAG, "Connection lost or failed: ${e.message}. Retrying...")
                } finally {
                    out = null
                    try {
                        socket?.close()
                    } catch (e: Exception) {
                    }

                    if (isRunning) delay(3000) // Backoff before next attempt
                }
            }
        }
    }

    /**
     * Queues the message for the send loop
     */
    override fun send(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            sendChannel.send(message)
        }
    }

    override fun stop() {
        isRunning = false
        _isConnected.value = false
        try {
            socket?.close()
            socket = null
            out = null
            // Flush the channel
            while (!sendChannel.isEmpty) {
                sendChannel.tryReceive()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stop error: ${e.message}")
        }
    }
}