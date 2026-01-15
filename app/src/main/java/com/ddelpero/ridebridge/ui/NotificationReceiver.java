package com.ddelpero.ridebridge.ui;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class NotificationReceiver extends NotificationListenerService {
    // The OS needs to be able to call this empty constructor
    public NotificationReceiver() {
    }

    @Override
    public void onNotificationPosted(android.service.notification.StatusBarNotification sbn) {
        // This triggers whenever ANY app posts a notification
        // We check if it has "EXTRA_MEDIA_SESSION" to confirm it's a music player
        if (sbn.getNotification().extras.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION)) {
            android.util.Log.d("RideBridge", "NOTIFICATION: Media change detected from " + sbn.getPackageName());

            // Send a local broadcast to tell the MainActivity to sync
            android.content.Intent intent = new android.content.Intent("com.ddelpero.SYNC_MEDIA");
            sendBroadcast(intent);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Leave empty for now
    }
}