package com.ddelpero.ridebridge.notifications;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.ddelpero.ridebridge.core.RideBridgeService;
import com.ddelpero.ridebridge.core.BluetoothManager;

public class RideBridgeNotificationListener extends NotificationListenerService {
    
    private static final String TAG = "RideBridge_Notif";
    private RideBridgeService service;
    private BluetoothManager bluetoothManager;
    
    // Apps to monitor
    private static final String[] MONITORED_APPS = {
        "com.android.phone",           // Phone calls
        "com.android.mms",             // SMS
        "com.facebook.orca",           // Facebook Messenger
        "com.whatsapp"                 // WhatsApp
    };
    
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        
        String packageName = sbn.getPackageName();
        if (!shouldMonitor(packageName)) return;
        
        Notification notification = sbn.getNotification();
        if (notification == null) return;
        
        // Extract notification data
        String sender = extractSender(notification, packageName);
        String message = extractMessage(notification);
        String appName = extractAppName(packageName);
        
        Log.d(TAG, "Notification from " + appName + ": " + sender + " - " + message);
        
        // Check if this app is enabled for forwarding
        if (isAppEnabled(packageName)) {
            NotificationData data = new NotificationData(packageName, appName, sender, message);
            sendNotification(data);
        }
    }
    
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Optional: Handle notification removal
    }
    
    private boolean shouldMonitor(String packageName) {
        for (String app : MONITORED_APPS) {
            if (app.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
    
    private String extractSender(Notification notification, String packageName) {
        // Try to get title from notification extras
        if (notification.extras != null) {
            String title = notification.extras.getString(Notification.EXTRA_TITLE);
            if (title != null && !title.isEmpty()) {
                return title;
            }
        }
        return "Unknown";
    }
    
    private String extractMessage(Notification notification) {
        // Try to get text from notification extras
        if (notification.extras != null) {
            String text = notification.extras.getString(Notification.EXTRA_TEXT);
            if (text != null && !text.isEmpty()) {
                return text;
            }
        }
        return "";
    }
    
    private String extractAppName(String packageName) {
        switch (packageName) {
            case "com.android.phone": return "Phone";
            case "com.android.mms": return "Messages";
            case "com.facebook.orca": return "Messenger";
            case "com.whatsapp": return "WhatsApp";
            default: return packageName;
        }
    }
    
    private boolean isAppEnabled(String packageName) {
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", Context.MODE_PRIVATE);
        return prefs.getBoolean("notify_" + packageName, false);
    }
    
    private void sendNotification(NotificationData data) {
        try {
            // Send via broadcast to service (similar to widget commands)
            android.content.Intent intent = new android.content.Intent("com.ddelpero.ridebridge.FORWARD_NOTIFICATION");
            intent.putExtra("appPackage", data.appPackage);
            intent.putExtra("appName", data.appName);
            intent.putExtra("sender", data.sender);
            intent.putExtra("message", data.message);
            intent.putExtra("timestamp", data.timestamp);
            sendBroadcast(intent);
            
            Log.d(TAG, "Notification forwarded: " + data.appName);
        } catch (Exception e) {
            Log.e(TAG, "Error sending notification: " + e.getMessage());
        }
    }
}
