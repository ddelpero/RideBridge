package com.ddelpero.ridebridge.communication

import kotlinx.coroutines.flow.StateFlow

interface DataTransport {
    val isConnected: StateFlow<Boolean>

    fun start(
        onConnectionStateChanged: (Boolean) -> Unit,
        onMessageReceived: (String) -> Unit
    )

    fun send(command: String)
    fun stop()
}