package com.ddelpero.ridebridge.communication

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class TCPServer() : DataTransport {
    public var port: Int = 6000
    private val TAG = "TcpServer"
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var out: PrintWriter? = null
    private var isRunning = false

    private val _isConnected = MutableStateFlow(false)
    override val isConnected = _isConnected.asStateFlow() // This is observable

    init {
        _isConnected.value = false
    }

    override fun start(
        onConnectionStateChanged: (Boolean) -> Unit,
        onMessageReceived: (String) -> Unit
    ) {
        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "Server listening on port $port")

                while (isRunning) {
                    // accept() blocks until a client connects
                    clientSocket = serverSocket?.accept()
                    Log.d(TAG, "Client connected: ${clientSocket?.inetAddress?.hostAddress}")
                    onConnectionStateChanged(true)
                    // Setup streams
                    out = PrintWriter(clientSocket!!.getOutputStream(), true)
                    val input = BufferedReader(InputStreamReader(clientSocket!!.getInputStream()))

                    _isConnected.value = true
                    // send("Hi client")

                    // Internal loop to keep this specific connection alive
                    while (isRunning && clientSocket?.isConnected == true) {
                        try {
                            val message = input.readLine() ?: break // Exit if client disconnects
                            onMessageReceived(message)
                        } catch (e: Exception) {
                            Log.e(TAG, "Read error: ${e.message}")
                            break
                        }
                    }
                    Log.d(TAG, "Client disconnected. Waiting for new connection...")
                    onConnectionStateChanged(false)
                    clientSocket?.close()
                    _isConnected.value = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            } finally {
                stop()
            }
        }
    }

    override fun send(message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.e(TAG, "sending: $message/$out")
                out?.println(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send to client: ${e.message}")
            }
        }
    }

    override fun stop() {
        isRunning = false
        _isConnected.value = false
        clientSocket?.close()
        serverSocket?.close()
        Log.d(TAG, "Server stopped.")
    }
}
