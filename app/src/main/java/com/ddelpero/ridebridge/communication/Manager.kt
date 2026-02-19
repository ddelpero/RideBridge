package com.ddelpero.ridebridge.communication

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ddelpero.ridebridge.Widget.RideBridgeWidgetProvider
import com.ddelpero.ridebridge.core.MediaManager
import com.ddelpero.ridebridge.service.MediaDataHelper
import com.ddelpero.ridebridge.service.NotificationReceiver

object Manager {
    private val _connectionStatus = MutableLiveData<Boolean>(false)
    val connectionStatus: LiveData<Boolean> get() = _connectionStatus

    var isRunning: Boolean = false

    private lateinit var server: DataTransport
    private lateinit var client: DataTransport

    private val address: String = "10.0.2.2"
    private val port: Int = 6000

    private lateinit var appContext: Context

    fun onServerMessageReceived(message: String) {
        MediaManager.updateFromJson(message, server.isConnected.value)
        RideBridgeWidgetProvider.triggerUpdate(appContext)
    }

    @SuppressLint("ServiceCast")
    fun onClientMessageReceived(command: String) {
        Log.d("onClientMessageReceived", command)
        // try {
        val cn = ComponentName(
            "com.ddelpero.ridebridge",
            "com.ddelpero.ridebridge.ui.NotificationReceiver"
        )
        // Much cleaner, no casting needed
        val mediaSessionManager = appContext.getSystemService(MediaSessionManager::class.java)
        val controllers = mediaSessionManager.getActiveSessions(cn)

        try {
            controllers?.firstOrNull()?.let { controller ->
                val controls = controller.transportControls
                Log.d("RideBridge", "SOURCE: Executing $command")

                if (command.startsWith("SEEK:")) {
                    val seekPos = command.split(":")[1].toLongOrNull() ?: 0L
                    Log.d("RideBridge", "SOURCE: Seeking to $seekPos")
                    controls.seekTo(seekPos)
                } else {
                    Log.d("RideBridge", "IsPLaying: ${MediaManager.currentState?.isPlaying}")
                    when (command) {
                        "PLAY" -> if (MediaManager.currentState?.isPlaying == true)
                            controls.pause() else controls.play()

                        "NEXT" -> controls.skipToNext()
                        "PREV" -> controls.skipToPrevious()
                        "VOICE_ASSIST" -> {
                            val TAG = "VOICE_ASSIST"
                            Log.d(TAG, "Sending Media Play intent to wake up music player")

                            val audioManager = appContext.getSystemService(AudioManager::class.java)
                            if (audioManager != null) {
                                val eventDown =
                                    KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                                audioManager.dispatchMediaKeyEvent(eventDown)

                                val eventUp =
                                    KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
                                audioManager.dispatchMediaKeyEvent(eventUp)
                            }

                        }

                        else -> Log.w("RideBridge", "Unknown command: $command")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RideBridge", "SOURCE: Control Error: ${e.message}")
        }
    }

    fun onClientConnectionsStateChanged(isConnected: Boolean) {
        _connectionStatus.postValue(isConnected)
        Log.d("onClientConnectionsStateChanged", "Connection state: $isConnected")
    }

    fun onServerConnectionsStateChanged(isConnected: Boolean) {
        _connectionStatus.postValue(isConnected)
        RideBridgeWidgetProvider.triggerUpdate(appContext)
        Log.d("onServerConnectionsStateChanged", "Connection state: $isConnected")
    }

    fun dispatchClientEvent(json: String) {
        Log.d("dispatchClientEvent", "$json.take(100)...")
        if (::client.isInitialized) {
            client.send(json)
        }
    }

    fun sendCommandToClient(cmd: String) {
        Log.d("sendCommandToClient", "$cmd...")
        if (::server.isInitialized) {
            server.send(cmd)
        }
    }

    private fun syncMedia(context: Context) {
        val mm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

        // ComponentName needs the context and the class of your listener
        val componentName = ComponentName(context, NotificationReceiver::class.java)

        val controllers = mm.getActiveSessions(componentName)
        // Grab the first active session (e.g., YouTube Music)
        val activeController = controllers.firstOrNull()

        val state = MediaDataHelper.extractState(activeController)
        val jsonPayload = MediaManager.formatMediaJson(state)
        dispatchClientEvent(jsonPayload)
        // client.send(json)
    }

    fun start(context: Context, isServer: Boolean, isEmulator: Boolean) {
        isRunning = true
        appContext = context.applicationContext
        if (isServer) {
            if (isEmulator) {
                server = TCPServer()
            } else {
                server = BTServer(appContext)
            }
            // server = Server()
            server.start(
                { isConnected ->
                    onServerConnectionsStateChanged(isConnected)
                },
                { message -> onServerMessageReceived(message) }
            )
        } else {
            if (isEmulator) {
                client = TCPClient(address, port)
            } else {
                client = BTClient(appContext)
            }
            client.start(
                { isConnected ->
                    onClientConnectionsStateChanged(isConnected)
                    if (isConnected) {
                        syncMedia(context)
                    }
                },
                { message -> onClientMessageReceived(message) }
            )
            MediaManager.setTransport { jsonString ->
                if (client.isConnected.value) {
                    client.send(jsonString)
                }
            }

        }
    }

    fun stop() {
        _connectionStatus.postValue(false)
        isRunning = false
        if (::server.isInitialized) {
            server.stop()
        }
        if (::client.isInitialized) {
            client.stop()
        }
    }
}