package com.ddelpero.ridebridge.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.media.session.MediaController
import android.media.session.MediaSession
import androidx.core.os.BundleCompat
import com.ddelpero.ridebridge.communication.Manager

class NotificationReceiver : NotificationListenerService() {

    // override fun onNotificationPosted(sbn: StatusBarNotification?) {
    //     Log.d("RideBridge", "NOTIFICATION: Posted from ${sbn?.packageName}")
    //     val extras = sbn?.notification?.extras ?: return
    //     // val token = extras.getParcelable<MediaSession.Token>(android.app.Notification.EXTRA_MEDIA_SESSION)
    //     val token = BundleCompat.getParcelable(extras, android.app.Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
    //
    //     if (token != null) {
    //         val controller = MediaController(applicationContext, token)
    //         // val json = MediaDataHelper.getMediaJson(controller)
    //         val json = MediaDataHelper.extractState(controller)
    //
    //         // Send via decoupled intent
    //         val intent = Intent("com.ddelpero.SYNC_MEDIA").apply {
    //             putExtra("PAYLOAD", json)
    //         }
    //         sendBroadcast(intent)
    //     }
    // }

    override fun onCreate() {
        super.onCreate()
        Log.d("RideBridge", "NotificationReceiver SERVICE CREATED")
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("RideBridge", "NotificationReceiver BINDING SUCCESSFUL")
        Manager.onListenerReady(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        MediaSessionMonitor.detach()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Log.d("RideBridge", "NOTIFICATION: Posted from ${sbn?.packageName}")
        if (sbn == null) return
        attachMediaSession(sbn)
        NotifForwarder.onPosted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        NotifForwarder.onRemoved(sbn)
    }

    private fun attachMediaSession(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val token = BundleCompat.getParcelable(
            extras,
            Notification.EXTRA_MEDIA_SESSION,
            MediaSession.Token::class.java
        ) ?: return
        try {
            val mm = getSystemService(android.media.session.MediaSessionManager::class.java)
            val cn = android.content.ComponentName(this, NotificationReceiver::class.java)
            val sessions = mm.getActiveSessions(cn)
            val picked = MediaSessionPicker.pick(sessions)
                ?: MediaController(applicationContext, token)
            MediaSessionMonitor.attach(picked)
        } catch (e: Exception) {
            Log.e("RideBridge", "Failed to attach session: ${e.message}")
            MediaSessionMonitor.attach(MediaController(applicationContext, token))
        }
    }
}