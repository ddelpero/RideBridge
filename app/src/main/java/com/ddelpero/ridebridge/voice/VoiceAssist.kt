package com.ddelpero.ridebridge.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat

object VoiceAssist {
    private const val TAG = "VoiceAssist"
    const val CHANNEL_FOREGROUND = "ridebridge_connected"
    private const val CHANNEL_VOICE_FSI = "ridebridge_voice_fsi"
    const val FOREGROUND_NOTIFICATION_ID = 2001
    private const val FSI_ID_BASE = 2100

    @Volatile private var fsiSeq = 0
    @Volatile private var lastFsiId = FSI_ID_BASE

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FOREGROUND,
                "RideBridge connection",
                NotificationManager.IMPORTANCE_LOW
            )
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_VOICE_FSI,
                "Play via Assistant",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Hands play search to Google Assistant"
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    fun buildPersistentNotification(context: Context) =
        NotificationCompat.Builder(context, CHANNEL_FOREGROUND)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("RideBridge")
            .setContentText("Connected to tablet")
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    @Synchronized
    fun playQuery(context: Context, query: String) {
        val app = context.applicationContext
        ensureChannels(app)
        dismissVoiceNotification(app)
        val phrase = assistantPhrase(query)
        fsiSeq += 1
        lastFsiId = FSI_ID_BASE + (fsiSeq % 20)
        Log.d(TAG, "PlaySearch FSI id=$lastFsiId '$phrase'")
        val launch = VoiceAssistActivity.intent(app, query).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_HISTORY
            )
        }
        val fullScreen = PendingIntent.getActivity(
            app,
            lastFsiId,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(app, CHANNEL_VOICE_FSI)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("RideBridge")
            .setContentText("Playing $phrase")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setAutoCancel(true)
            .build()
        app.getSystemService(NotificationManager::class.java)
            ?.notify(lastFsiId, notification)
    }

    @Synchronized
    fun cancel(context: Context) {
        dismissVoiceNotification(context.applicationContext)
        Log.d(TAG, "Cancelled play FSI")
    }

    fun dismissVoiceNotification(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.cancel(lastFsiId)
        for (i in 0 until 20) {
            nm.cancel(FSI_ID_BASE + i)
        }
    }

    fun assistantPhrase(query: String): String {
        val q = query.trim()
        val body = if (q.startsWith("play ", ignoreCase = true)) q else "play $q"
        return if (body.contains("youtube music", ignoreCase = true)) body
        else "$body on youtube music"
    }
}
