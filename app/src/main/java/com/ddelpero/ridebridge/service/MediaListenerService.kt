package com.ddelpero.ridebridge.service

import android.app.Notification
import android.content.ComponentName
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.media.session.MediaController
import android.media.session.MediaSession
import androidx.core.os.BundleCompat
import com.ddelpero.ridebridge.communication.Manager
import com.ddelpero.ridebridge.core.MediaManager

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
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        Log.d("RideBridge", "NOTIFICATION: Posted from ${sbn?.packageName}")
        val extras = sbn?.notification?.extras ?: return
        val token = BundleCompat.getParcelable(extras, Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)

        if (token != null) {
            val controller = MediaController(applicationContext, token)

            // 1. Get the data object
            val state = MediaDataHelper.extractState(controller)

            // 2. Convert to JSON String immediately
            val jsonPayload = MediaManager.formatMediaJson(state)

            Manager.dispatchClientEvent(jsonPayload)
            // // 3. Broadcast it as a simple String
            // val intent = Intent("com.ddelpero.INBOUND_EVENT").apply {
            //     putExtra("type", "MEDIA")
            //     putExtra("PAYLOAD", jsonPayload)
            //     setPackage(packageName)
            // }
            // sendBroadcast(intent)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Leave empty for now
    }
}