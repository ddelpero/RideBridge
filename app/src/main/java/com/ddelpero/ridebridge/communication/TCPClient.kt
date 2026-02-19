package com.ddelpero.ridebridge.communication

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
import java.net.InetSocketAddress
import java.net.Socket

class TCPClient(private val ip: String, private val port: Int) : DataTransport {
    private val TAG = "TcpClient"
    private var socket: Socket? = null
    private var out: PrintWriter? = null
    private var isRunning = false
    private val _isConnected = MutableStateFlow(false)
    override val isConnected = _isConnected.asStateFlow() // This is observable

    private val sendChannel = Channel<String>(Channel.BUFFERED)

    init {
        _isConnected.value = false
    }

    /**
     * Starts the connection loop.
     * If the server is not available, it will catch the exception and retry.
     */
    override fun start(
        onConnectionStateChanged: (Boolean) -> Unit,
        onMessageReceived: (String) -> Unit
    ) {
        isRunning = true
        CoroutineScope(Dispatchers.IO).launch {
            while (isRunning) {
                try {
                    socket = Socket()
                    socket?.connect(InetSocketAddress(ip, port), 5000)

                    _isConnected.value = true
                    onConnectionStateChanged(true)

                    out = PrintWriter(socket!!.getOutputStream(), true)
                    val input = BufferedReader(InputStreamReader(socket!!.getInputStream()))

                    // Launch a separate coroutine to handle SENDING messages
                    // while this loop handles RECEIVING messages.
                    val sendJob = launch {
                        try {
                            for (message in sendChannel) {
                                out?.println(message)
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "SendJob cancelled or failed")
                        }
                    }

                    try {
                        while (isRunning && socket?.isConnected == true) {
                            val message = input.readLine() ?: break
                            onMessageReceived(message)
                        }
                    } finally {
                        sendJob.cancel()
                    }

                    sendJob.cancel() // Stop trying to send if the receive loop breaks
                } catch (e: Exception) {
                    _isConnected.value = false
                    onConnectionStateChanged(false)
                    Log.e(TAG, "Connection lost. Retrying...")
                } finally {
                    out = null
                    socket?.close()
                    if (isRunning) delay(3000)
                }
            }
        }
    }

    /**
     * Send data. This no longer waits for a socket; it just queues the message.
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
            // 1. Close the socket to break the input.readLine() loop
            socket?.close()
            socket = null

            // 2. Clear the output stream reference
            out = null

            // 3. Clear any pending messages in the channel
            // so they don't send accidentally on the next start
            while (!sendChannel.isEmpty) {
                sendChannel.tryReceive()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during stop: ${e.message}")
        }
    }
}