package com.ddelpero.ridebridge.core;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ddelpero.ridebridge.R;
import com.ddelpero.ridebridge.display.DisplayController;
import com.ddelpero.ridebridge.source.SourceController;
import com.ddelpero.ridebridge.widget.RideBridgeWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.Context;

public class RideBridgeService extends Service {
    
    private static final String TAG = "RideBridge";
    
    // Service components
    private BluetoothManager bluetoothManager;
    private DisplayController displayController;
    private SourceController sourceController;
    private ConnectionManager connectionManager;
    
    // Mode tracking
    private boolean isTabletMode = false;
    private boolean modeInitialized = false;
    
    // Widget command receiver
    private BroadcastReceiver widgetCommandReceiver;
    
    // LiveData for UI observation
    private MutableLiveData<String> statusLiveData = new MutableLiveData<>();
    private MutableLiveData<String> logLiveData = new MutableLiveData<>();
    private MutableLiveData<MediaState> mediaStateLiveData = new MutableLiveData<>();
    private MutableLiveData<ConnectionManager.ConnectionStatus> connectionStatusLiveData = new MutableLiveData<>();
    
    // Keep a buffer of recent logs for new observers
    private final java.util.LinkedList<String> logBuffer = new java.util.LinkedList<>();
    private static final int MAX_LOG_BUFFER_SIZE = 100;
    
    // Binder for UI clients to connect
    private final IBinder binder = new LocalBinder();
    
    public class LocalBinder extends Binder {
        public RideBridgeService getService() {
            return RideBridgeService.this;
        }
    }
    
    // Expose displayController to MainActivity
    public DisplayController getDisplayController() {
        return displayController;
    }
    
    // Expose bluetoothManager for sending test notifications
    public BluetoothManager getBluetoothManager() {
        return bluetoothManager;
    }
    
    // Expose connectionManager for UI control
    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }
    
    public LiveData<ConnectionManager.ConnectionStatus> getConnectionStatusLiveData() {
        return connectionStatusLiveData;
    }
    
    /**
     * Unified logging function that writes to both logcat and UI LiveData
     * Ensures consistency between what's shown in logcat and what's shown in the UI
     * Uses postValue() to support background threads and maintains a buffer for late observers
     */
    public void log(String message) {
        Log.d(TAG, message);
        
        // Add to buffer
        synchronized (logBuffer) {
            logBuffer.addFirst(message);
            if (logBuffer.size() > MAX_LOG_BUFFER_SIZE) {
                logBuffer.removeLast();
            }
        }
        
        // Post to LiveData - post the full buffer so new observers get history
        StringBuilder sb = new StringBuilder();
        synchronized (logBuffer) {
            for (String line : logBuffer) {
                sb.append(line).append("\n");
            }
        }
        logLiveData.postValue(sb.toString());
    }
    
    // State class for media information
    public static class MediaState {
        public String artist;
        public String track;
        public boolean isPlaying;
        public android.graphics.Bitmap albumArt;
        public long position;
        public long duration;
        public float playbackSpeed;
        
        public MediaState(String artist, String track, boolean isPlaying, 
                         android.graphics.Bitmap albumArt, long position, long duration, float speed) {
            this.artist = artist;
            this.track = track;
            this.isPlaying = isPlaying;
            this.albumArt = albumArt;
            this.position = position;
            this.duration = duration;
            this.playbackSpeed = speed;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        log("SERVICE: ===== RideBridgeService CREATED ===== [MARKER_001]");
        
        // Initialize components
        bluetoothManager = new BluetoothManager();
        connectionManager = new ConnectionManager(this, bluetoothManager);
        connectionManager.setStatusListener(status -> {
            log("SERVICE: Connection status: " + status);
            connectionStatusLiveData.postValue(status);
        });
        
        statusLiveData.setValue("SERVICE: Initializing...");
        
        // Register broadcast receiver for widget commands
        widgetCommandReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("com.ddelpero.ridebridge.WIDGET_COMMAND".equals(intent.getAction())) {
                    String command = intent.getStringExtra("command");
                    log("SERVICE: Widget command received via broadcast: " + command);
                    handleWidgetCommand(command);
                } else if ("com.ddelpero.ridebridge.FORWARD_NOTIFICATION".equals(intent.getAction())) {
                    String appPackage = intent.getStringExtra("appPackage");
                    String appName = intent.getStringExtra("appName");
                    String sender = intent.getStringExtra("sender");
                    String message = intent.getStringExtra("message");
                    long timestamp = intent.getLongExtra("timestamp", 0);
                    
                    log("SERVICE: Notification received from " + appName);
                    forwardNotificationViaBluetooth(appPackage, appName, sender, message, timestamp);
                }
            }
        };
        
        android.content.IntentFilter filter = new android.content.IntentFilter("com.ddelpero.ridebridge.WIDGET_COMMAND");
        filter.addAction("com.ddelpero.ridebridge.FORWARD_NOTIFICATION");
        registerReceiver(widgetCommandReceiver, filter, Context.RECEIVER_EXPORTED);
    }
    
    private void forwardNotificationViaBluetooth(String appPackage, String appName, String sender, String message, long timestamp) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("type", "notification");
            json.put("appPackage", appPackage);
            json.put("appName", appName);
            json.put("sender", sender);
            json.put("message", message);
            json.put("timestamp", timestamp);
            
            if (bluetoothManager != null) {
                log("SERVICE: Sending notification via Bluetooth: " + appName);
                bluetoothManager.sendMessage(json.toString(), null);
            } else {
                log("SERVICE: BluetoothManager not initialized, can't send notification");
            }
        } catch (Exception e) {
            log("SERVICE: Error sending notification: " + e.getMessage());
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("SERVICE: onStartCommand called");
        
        // Handle widget commands (legacy, for backward compatibility)
        if (intent != null && intent.hasExtra("widget_command")) {
            String command = intent.getStringExtra("widget_command");
            log("SERVICE: Received widget command: " + command);
            handleWidgetCommand(command);
            return START_STICKY;
        }
        
        // Check if this is an auto-start (if so, don't show UI)
        boolean isAutoStart = intent != null && intent.getBooleanExtra("auto_start", false);
        if (isAutoStart) {
            log("SERVICE: Auto-start detected, running silently");
        }
        
        // Only initialize mode once
        if (modeInitialized) {
            log("SERVICE: Mode already initialized, skipping");
            return START_STICKY;
        }
        
        // Load saved preferences
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        isTabletMode = prefs.getBoolean("is_tablet", false);
        
        log("SERVICE: Mode is " + (isTabletMode ? "TABLET (Receiver)" : "PHONE (Sender)"));
        
        // Start appropriate mode
        if (isTabletMode) {
            startTabletMode();
        } else {
            startPhoneMode();
        }
        
        // Initialize connection manager
        connectionManager.initializeConnection();
        
        modeInitialized = true;
        
        // Keep service persistent even if app is killed
        return START_STICKY;
    }
    
    private void handleWidgetCommand(String command) {
        log("SERVICE: Handling widget command: " + command);
        
        // Make sure we're in tablet mode
        if (!isTabletMode) {
            log("SERVICE: Not in tablet mode - cannot send command");
            return;
        }
        
        // Ensure DisplayController is initialized
        if (displayController == null) {
            log("SERVICE: DisplayController is null, attempting to initialize");
            try {
                startTabletMode();
                Thread.sleep(500); // Give it a moment to initialize
            } catch (Exception e) {
                log("SERVICE: Error initializing tablet mode: " + e.getMessage());
            }
        }
        
        if (displayController == null) {
            log("SERVICE: DisplayController still null after init attempt - cannot send command");
            return;
        }
        
        log("SERVICE: Sending command to DisplayController: " + command);
        switch (command) {
            case "PLAY":
                log("SERVICE: Calling displayController.sendPlayCommand()");
                displayController.sendPlayCommand();
                break;
            case "PAUSE":
                log("SERVICE: Calling displayController.sendPauseCommand()");
                displayController.sendPauseCommand();
                break;
            case "NEXT":
                log("SERVICE: Calling displayController.sendNextCommand()");
                displayController.sendNextCommand();
                break;
            case "PREV":
                log("SERVICE: Calling displayController.sendPreviousCommand()");
                displayController.sendPreviousCommand();
                break;
            case "VOICE":
                log("SERVICE: Calling displayController.sendVoiceCommand()");
                displayController.sendVoiceCommand();
                break;
            default:
                log("SERVICE: Unknown command: " + command);
        }
    }
    
    private void startPhoneMode() {
        log("SERVICE: Starting PHONE (Sender) mode");
        statusLiveData.setValue("SERVICE: Phone mode - syncing media");
        
        // Create and start source controller
        sourceController = new SourceController(this, bluetoothManager);
        
        // Listen for media data ready
        sourceController.setSourceDataListener(mediaJson -> {
            try {
                log("SERVICE: LISTENER CALLBACK INVOKED with payload length: " + mediaJson.length());
                // Log raw JSON data (strip albumArt for readability)
                String logJson = mediaJson.replaceAll("\"albumArt\":\"[^\"]*\"", "\"albumArt\":\"[base64...]\"");
                log("SERVICE: About to post JSON to UI: " + logJson.substring(0, Math.min(100, logJson.length())));
                log(logJson);
            } catch (Exception e) {
                log("SERVICE: ERROR IN LISTENER CALLBACK: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        // Listen for remote commands from tablet
        sourceController.setRemoteCommandListener(command -> {
            log("SERVICE: Remote command received: " + command);
        });
        
        // Start syncing
        sourceController.start();
    }
    
    private void startTabletMode() {
        log("SERVICE: Starting TABLET (Receiver) mode");
        statusLiveData.setValue("SERVICE: Tablet mode - listening for media");
        
        // Create and start display controller
        displayController = new DisplayController(this, bluetoothManager);
        
        // Listen for incoming media data
        // displayController.setDisplayDataListener(mediaData -> {
        //     // Update LiveData for UI observation (use postValue for background thread)
        //     mediaStateLiveData.postValue(new MediaState(
        //         mediaData.artist,
        //         mediaData.track,
        //         mediaData.isPlaying,
        //         mediaData.albumArt,
        //         mediaData.position,
        //         mediaData.duration,
        //         mediaData.playbackSpeed
        //     ));
        // });
        
        // Set listener to also get raw JSON data
        displayController.setRawDataListener(rawJson -> {
            // Log raw JSON data (strip albumArt for readability)
            String logJson = rawJson.replaceAll("\"albumArt\":\"[^\"]*\"", "\"albumArt\":\"[base64...]\"");
            log(logJson);
        });
        
        // Set notification listener to show system notifications
        displayController.setNotificationListener(notification -> {
            showSystemNotification(notification);
            log("SERVICE: Showing system notification: " + notification.appName);
        });
        
        // Start listening in background thread (blocking call)
        new Thread(() -> {
            displayController.startListening();
        }).start();
    }
    
    private void showSystemNotification(com.ddelpero.ridebridge.notifications.NotificationData notification) {
        // Check notification permission (Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS) 
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                log("SERVICE: POST_NOTIFICATIONS permission not granted, cannot show notification");
                return;
            }
        }
        
        // Create notification channel if needed (Android 8+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                "ridebridge_notifications",
                "RideBridge Notifications",
                android.app.NotificationManager.IMPORTANCE_HIGH
            );
            android.app.NotificationManager notificationManager = 
                (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }
        
        // Create notification
        androidx.core.app.NotificationCompat.Builder builder = 
            new androidx.core.app.NotificationCompat.Builder(this, "ridebridge_notifications")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(notification.sender)
                .setContentText(notification.message)
                .setSubText(notification.appName)
                .setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE);
        
        android.app.NotificationManager notificationManager = 
            (android.app.NotificationManager) getSystemService(android.content.Context.NOTIFICATION_SERVICE);
        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
    }
    
    @Override
    public void onDestroy() {
        log("SERVICE: onDestroy called");
        
        // Cleanup connection manager
        if (connectionManager != null) {
            connectionManager.cleanup();
        }
        
        // Unregister broadcast receiver
        if (widgetCommandReceiver != null) {
            try {
                unregisterReceiver(widgetCommandReceiver);
            } catch (Exception e) {
                log("SERVICE: Error unregistering receiver: " + e.getMessage());
            }
        }
        
        // Clean up
        if (sourceController != null) {
            sourceController.stop();
        }
        if (bluetoothManager != null) {
            bluetoothManager.setServiceActive(false);
        }
        
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        log("SERVICE: UI client bound to service");
        return binder;
    }
    
    // Public methods for UI to access
    public boolean isTabletMode() {
        return isTabletMode;
    }
    
    public LiveData<String> getStatusLiveData() {
        return statusLiveData;
    }
    
    public LiveData<String> getLogLiveData() {
        return logLiveData;
    }
    
    public LiveData<MediaState> getMediaStateLiveData() {
        return mediaStateLiveData;
    }
    
    public void updateWidget(DisplayController.MediaData mediaData) {
        if (mediaData == null) return;
        
        log("SERVICE: Updating widget with track=" + mediaData.track);
        RideBridgeWidgetProvider.updateWidget(this, mediaData);
    }
    
    public void switchMode(boolean tabletMode) {
        log("SERVICE: Switching to " + (tabletMode ? "TABLET" : "PHONE") + " mode");
        
        // Stop current mode
        if (sourceController != null) {
            sourceController.stop();
            sourceController = null;
        }
        if (displayController != null) {
            displayController = null;
        }
        
        // Update preference
        isTabletMode = tabletMode;
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("is_tablet", tabletMode).apply();
        
        // Reset initialization flag so new mode initializes
        modeInitialized = false;
        
        // Start new mode
        if (tabletMode) {
            startTabletMode();
        } else {
            startPhoneMode();
        }
        
        modeInitialized = true;
    }
}
