package com.ddelpero.ridebridge.core;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.ddelpero.ridebridge.display.DisplayController;
import com.ddelpero.ridebridge.source.SourceController;
import com.ddelpero.ridebridge.widget.RideBridgeWidgetProvider;

public class RideBridgeService extends Service {
    
    private static final String TAG = "RideBridge";
    
    // Service components
    private BluetoothManager bluetoothManager;
    private DisplayController displayController;
    private SourceController sourceController;
    
    // Mode tracking
    private boolean isTabletMode = false;
    private boolean modeInitialized = false;
    
    // LiveData for UI observation
    private MutableLiveData<String> statusLiveData = new MutableLiveData<>();
    private MutableLiveData<String> logLiveData = new MutableLiveData<>();
    private MutableLiveData<MediaState> mediaStateLiveData = new MutableLiveData<>();
    
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
        statusLiveData.setValue("SERVICE: Initializing...");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        log("SERVICE: onStartCommand called");
        
        // Handle widget commands
        if (intent != null && intent.hasExtra("widget_command")) {
            String command = intent.getStringExtra("widget_command");
            log("SERVICE: Received widget command: " + command);
            handleWidgetCommand(command);
            return START_STICKY;
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
        
        // Start listening
        displayController.startListening();
    }
    
    @Override
    public void onDestroy() {
        log("SERVICE: onDestroy called");
        
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
