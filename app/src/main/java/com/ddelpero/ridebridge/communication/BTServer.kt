package com.ddelpero.ridebridge.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.util.UUID

class BTServer(private val context: Context) : DataTransport {
    private val TAG = "BTServer"
    private var isRunning = false

    // StateFlow restored
    private val _isConnected = MutableStateFlow(false)
    override val isConnected = _isConnected.asStateFlow()

    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var out: PrintWriter? = null
    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @SuppressLint("MissingPermission")
    override fun start(
        onConnectionStateChanged: (Boolean) -> Unit,
        onMessageReceived: (String) -> Unit
    ) {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = manager.adapter ?: return

        // Hardware activation happens here as requested
        // val discoverIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
        //     putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        //     addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // }
        // context.startActivity(discoverIntent)

        if (!adapter.isEnabled) {
            Log.w(TAG, "Bluetooth is disabled. Server will not start.")
            return
        }

        isRunning = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord("RideBridge", uuid)
                Log.d(TAG, "Bluetooth Server listening...")

                while (isRunning) {
                    val socket = serverSocket?.accept()
                    if (socket != null) {
                        clientSocket = socket
                        Log.d(TAG, "Client connected")

                        // Update both the callback and the Flow
                        onConnectionStateChanged(true)
                        _isConnected.value = true

                        out = PrintWriter(clientSocket!!.outputStream, true)
                        val input = BufferedReader(InputStreamReader(clientSocket!!.inputStream))

                        while (isRunning && clientSocket?.isConnected == true) {
                            try {
                                val message = input.readLine() ?: break
                                onMessageReceived(message)
                            } catch (e: Exception) {
                                Log.e(TAG, "Read error: ${e.message}")
                                break
                            }
                        }

                        Log.d(TAG, "Client disconnected")
                        onConnectionStateChanged(false)
                        _isConnected.value = false
                        cleanupSession()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    override fun send(command: String) {
        // Now 'out' is accessible here!
        CoroutineScope(Dispatchers.IO).launch {
            try {
                out?.println(command)
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
            }
        }
    }

    private fun cleanupSession() {
        out?.close()
        out = null
        clientSocket?.close()
        clientSocket = null
    }

    override fun stop() {
        isRunning = false
        _isConnected.value = false
        cleanupSession()
        try {
            serverSocket?.close()
            serverSocket = null
        } catch (e: Exception) {
            Log.e(TAG, "Error closing server: ${e.message}")
        }
    }
}