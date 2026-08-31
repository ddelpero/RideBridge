package com.ddelpero.ridebridge.communication

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.ddelpero.ridebridge.Widget.RideBridgeWidgetProvider
import com.ddelpero.ridebridge.core.MediaManager
import com.ddelpero.ridebridge.service.MediaSessionMonitor
import com.ddelpero.ridebridge.service.MediaSessionPicker
import com.ddelpero.ridebridge.service.NotificationReceiver
import com.ddelpero.ridebridge.service.RideBridgeForegroundService
import com.ddelpero.ridebridge.voice.PlaceSearch
import com.ddelpero.ridebridge.voice.VoiceAssist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Manager {
    private const val TAG = "RideBridgeManager"
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
        when {
            command == "VOICE_CANCEL" -> {
                RideBridgeForegroundService.cancelVoice(appContext)
                return
            }
            command == "VOICE_ASSIST" || command == "VOICE_START" ||
                command.startsWith("VOICE_START:") -> {
                parseRiderLocation(command)
                RideBridgeForegroundService.toggleVoice(appContext)
                return
            }
        }

        if (command.startsWith("SEEK:")) {
            val seekPos = command.split(":")[1].toLongOrNull() ?: 0L
            Log.d(TAG, "SOURCE: Seeking to $seekPos")
            withMediaControls { it.seekTo(seekPos) }
            return
        }

        when (command) {
            "PLAY" -> withController { controller ->
                val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
                if (playing) controller.transportControls.pause() else controller.transportControls.play()
            }
            "NEXT" -> withMediaControls { it.skipToNext() }
            "PREV" -> withMediaControls { it.skipToPrevious() }
            else -> Log.w(TAG, "Unknown command: $command")
        }
    }

    private fun parseRiderLocation(command: String) {
        val coords = command.substringAfter("VOICE_START:", missingDelimiterValue = "")
        if (coords.isBlank()) return
        val parts = coords.split(",")
        if (parts.size < 2) return
        val lat = parts[0].toDoubleOrNull() ?: return
        val lng = parts[1].toDoubleOrNull() ?: return
        PlaceSearch.setRiderLocation(lat, lng)
    }

    fun withMediaControls(block: (android.media.session.MediaController.TransportControls) -> Unit) {
        withController { block(it.transportControls) }
    }

    fun withController(block: (android.media.session.MediaController) -> Unit) {
        if (!::appContext.isInitialized) return
        try {
            val cn = ComponentName(appContext, NotificationReceiver::class.java)
            val mediaSessionManager = appContext.getSystemService(MediaSessionManager::class.java)
            val controller = MediaSessionPicker.pick(mediaSessionManager.getActiveSessions(cn))
            if (controller == null) {
                Log.w(TAG, "No active media session")
                return
            }
            MediaSessionMonitor.attach(controller)
            block(controller)
        } catch (e: Exception) {
            Log.e(TAG, "SOURCE: Control Error: ${e.message}")
        }
    }

    fun withPackageController(
        packageName: String,
        block: (android.media.session.MediaController) -> Unit
    ): Boolean {
        if (!::appContext.isInitialized) return false
        return try {
            val cn = ComponentName(appContext, NotificationReceiver::class.java)
            val mm = appContext.getSystemService(MediaSessionManager::class.java)
            val controller = mm.getActiveSessions(cn).firstOrNull { it.packageName == packageName }
            if (controller == null) {
                Log.w(TAG, "No media session for $packageName")
                false
            } else {
                block(controller)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "withPackageController failed: ${e.message}")
            false
        }
    }

    fun onClientConnectionsStateChanged(isConnected: Boolean) {
        _connectionStatus.postValue(isConnected)
        Log.d("onClientConnectionsStateChanged", "Connection state: $isConnected")
        if (::appContext.isInitialized) {
            if (isConnected) {
                MediaManager.resetTrackCache()
                RideBridgeForegroundService.start(appContext)
                scheduleSyncMedia()
            } else {
                VoiceAssist.cancel(appContext)
                RideBridgeForegroundService.stop(appContext)
            }
        }
    }

    fun onServerConnectionsStateChanged(isConnected: Boolean) {
        _connectionStatus.postValue(isConnected)
        RideBridgeWidgetProvider.triggerUpdate(appContext)
        Log.d("onServerConnectionsStateChanged", "Connection state: $isConnected")
    }

    fun onListenerReady(context: Context) {
        if (!::appContext.isInitialized) {
            appContext = context.applicationContext
        }
        scheduleSyncMedia()
    }

    fun appContextOrNull(): Context? = if (::appContext.isInitialized) appContext else null

    fun rebindPreferredSession() {
        if (!::appContext.isInitialized) return
        try {
            val mm = appContext.getSystemService(MediaSessionManager::class.java)
            val cn = ComponentName(appContext, NotificationReceiver::class.java)
            val picked = MediaSessionPicker.pick(mm.getActiveSessions(cn)) ?: return
            if (picked.packageName != MediaSessionMonitor.trackedPackage()) {
                Log.d(TAG, "rebindPreferredSession: ${picked.packageName}")
                MediaSessionMonitor.attach(picked)
            }
        } catch (e: Exception) {
            Log.e(TAG, "rebindPreferredSession failed: ${e.message}")
        }
    }

    fun forceRebindMedia() {
        if (!::appContext.isInitialized) return
        try {
            val mm = appContext.getSystemService(MediaSessionManager::class.java)
            val cn = ComponentName(appContext, NotificationReceiver::class.java)
            val picked = MediaSessionPicker.pick(mm.getActiveSessions(cn))
            MediaSessionMonitor.detach()
            if (picked != null) {
                Log.d(TAG, "forceRebindMedia: ${picked.packageName}")
                MediaSessionMonitor.attach(picked)
                MediaSessionMonitor.push()
            }
        } catch (e: Exception) {
            Log.e(TAG, "forceRebindMedia failed: ${e.message}")
        }
    }

    fun dispatchClientEvent(json: String) {
        Log.d("dispatchClientEvent", "${json.take(100)}...")
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

    private fun scheduleSyncMedia() {
        if (!::appContext.isInitialized) return
        CoroutineScope(Dispatchers.IO).launch {
            val delays = longArrayOf(0L, 300L, 1000L)
            for (wait in delays) {
                if (wait > 0L) delay(wait)
                if (syncMedia(appContext)) return@launch
            }
            Log.w(TAG, "syncMedia: no active session after retries")
        }
    }

    private fun syncMedia(context: Context): Boolean {
        return try {
            val mm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(context, NotificationReceiver::class.java)
            val controllers = mm.getActiveSessions(componentName)
            val activeController = MediaSessionPicker.pick(controllers)
            if (activeController == null) {
                Log.d(TAG, "syncMedia: no active media session yet")
                false
            } else {
                Log.d(TAG, "syncMedia: attaching ${activeController.packageName}")
                MediaSessionMonitor.attach(activeController)
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncMedia failed: ${e.message}")
            false
        }
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
        MediaSessionMonitor.detach()
        if (::server.isInitialized) {
            server.stop()
        }
        if (::client.isInitialized) {
            client.stop()
        }
        if (::appContext.isInitialized) {
            VoiceAssist.cancel(appContext)
            RideBridgeForegroundService.stop(appContext)
        }
    }
}
