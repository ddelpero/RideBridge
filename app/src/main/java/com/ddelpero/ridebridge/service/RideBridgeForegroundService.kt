package com.ddelpero.ridebridge.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.ddelpero.ridebridge.voice.VoiceAssist
import com.ddelpero.ridebridge.voice.VoiceFeedback
import com.ddelpero.ridebridge.voice.VoiceSession
import java.util.concurrent.atomic.AtomicBoolean

class RideBridgeForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var voiceSession: VoiceSession? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running.set(true)
        VoiceAssist.ensureChannels(this)
        VoiceFeedback.init(this)
        val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        try {
            startForeground(
                VoiceAssist.FOREGROUND_NOTIFICATION_ID,
                VoiceAssist.buildPersistentNotification(this),
                types
            )
        } catch (e: Exception) {
            Log.e(TAG, "startForeground with microphone failed: ${e.message}")
            try {
                startForeground(
                    VoiceAssist.FOREGROUND_NOTIFICATION_ID,
                    VoiceAssist.buildPersistentNotification(this),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } catch (e2: Exception) {
                Log.e(TAG, "startForeground failed: ${e2.message}")
                running.set(false)
                stopSelf()
                return
            }
        }
        voiceSession = VoiceSession(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_VOICE_TOGGLE -> handler.post {
                if (voiceSession?.isListening != true) {
                    VoiceAssist.cancel(this)
                }
                voiceSession?.toggle()
            }
            ACTION_VOICE_CANCEL -> handler.post {
                VoiceAssist.cancel(this)
                voiceSession?.cancel()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceSession?.release()
        voiceSession = null
        running.set(false)
    }

    companion object {
        private const val TAG = "RideBridgeFgService"
        private const val ACTION_VOICE_TOGGLE = "com.ddelpero.ridebridge.VOICE_TOGGLE"
        private const val ACTION_VOICE_CANCEL = "com.ddelpero.ridebridge.VOICE_CANCEL"
        private val running = AtomicBoolean(false)

        fun start(context: Context) {
            val app = context.applicationContext
            try {
                ContextCompat.startForegroundService(
                    app,
                    Intent(app, RideBridgeForegroundService::class.java)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unable to start foreground service: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.applicationContext.stopService(
                    Intent(context.applicationContext, RideBridgeForegroundService::class.java)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unable to stop foreground service: ${e.message}")
            }
        }

        fun toggleVoice(context: Context) {
            val app = context.applicationContext
            if (!running.get()) {
                Log.w(TAG, "Voice tap ignored: connection service not running")
                return
            }
            try {
                app.startService(
                    Intent(app, RideBridgeForegroundService::class.java).setAction(ACTION_VOICE_TOGGLE)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unable to toggle voice: ${e.message}")
            }
        }

        fun cancelVoice(context: Context) {
            val app = context.applicationContext
            if (!running.get()) return
            try {
                app.startService(
                    Intent(app, RideBridgeForegroundService::class.java).setAction(ACTION_VOICE_CANCEL)
                )
            } catch (e: Exception) {
                Log.e(TAG, "Unable to cancel voice: ${e.message}")
            }
        }
    }
}
