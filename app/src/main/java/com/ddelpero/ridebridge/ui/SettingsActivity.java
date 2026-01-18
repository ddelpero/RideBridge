package com.ddelpero.ridebridge.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.lifecycle.Observer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.ddelpero.ridebridge.R;
import com.ddelpero.ridebridge.core.RideBridgeService;

public class SettingsActivity extends AppCompatActivity {
    
    private static final String TAG = "RideBridge";
    
    // UI Elements
    private SwitchCompat roleSwitch;
    private CheckBox autoStartCheckBox;
    private Button btnStart;
    private Button btnTestNotification;
    private TextView statusLabel;
    private TextView logView;
    private View statusIndicator;
    private TextView roleLabelText;
    private TextView currentTrackLabel;
    private ScrollView logScrollView;
    private LinearLayout notificationPreferencesSection;
    private LinearLayout notificationAppsList;
    
    // Service
    private RideBridgeService rideBridgeService;
    private boolean isBound = false;
    private boolean isServiceRunning = false;
    
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "SETTINGS: Connected to RideBridgeService");
            RideBridgeService.LocalBinder binder = (RideBridgeService.LocalBinder) service;
            rideBridgeService = binder.getService();
            isBound = true;
            
            // Observe service state
            observeServiceState();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "SETTINGS: Disconnected from RideBridgeService");
            isBound = false;
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);
        
        Log.d(TAG, "SETTINGS: Activity created");
        
        // Link UI elements
        roleSwitch = findViewById(R.id.roleSwitch);
        autoStartCheckBox = findViewById(R.id.autoStartCheckBox);
        btnStart = findViewById(R.id.btnStart);
        btnTestNotification = findViewById(R.id.btnTestNotification);
        statusLabel = findViewById(R.id.statusLabel);
        logView = findViewById(R.id.logView);
        statusIndicator = findViewById(R.id.statusIndicator);
        roleLabelText = findViewById(R.id.roleLabelText);
        currentTrackLabel = findViewById(R.id.currentTrackLabel);
        logScrollView = findViewById(R.id.logScrollView);
        notificationPreferencesSection = findViewById(R.id.notificationPreferencesSection);
        notificationAppsList = findViewById(R.id.notificationAppsList);
        
        // Load saved preferences
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        boolean isTabletMode = prefs.getBoolean("is_tablet", false);
        boolean autoStart = prefs.getBoolean("auto_start", false);
        
        // Set UI initial state
        roleSwitch.setChecked(isTabletMode);
        autoStartCheckBox.setChecked(autoStart);
        updateRoleLabel(isTabletMode);
        updateTestNotificationButtonVisibility(isTabletMode);
        
        // Request POST_NOTIFICATIONS permission on tablet if needed (Android 13+)
        if (isTabletMode && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(
                    this,
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    1001
                );
            }
        }
        
        // Role Switch Listener
        roleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d(TAG, "SETTINGS: Role switched to " + (isChecked ? "TABLET" : "PHONE"));
            updateRoleLabel(isChecked);
            updateTestNotificationButtonVisibility(isChecked);
            updateNotificationPreferencesVisibility(isChecked);
            saveRoleSettings(isChecked);
            
            // Switch service mode if bound
            if (isBound && rideBridgeService != null) {
                rideBridgeService.switchMode(isChecked);
            }
        });
        
        // Auto-Start Listener
        autoStartCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d(TAG, "SETTINGS: Auto-start set to " + isChecked);
            saveAutoStartSettings(isChecked);
        });
        
        // Build dynamic notification app list
        populateNotificationAppsList(prefs);
        
        // Initialize notification preferences visibility (only show in phone mode)
        updateNotificationPreferencesVisibility(isTabletMode);
        
        // Start Button
        btnStart.setOnClickListener(v -> {
            Log.d(TAG, "SETTINGS: Start/Stop button clicked");
            toggleService();
        });
        
        // Test Notification Button (only visible in phone mode)
        btnTestNotification.setOnClickListener(v -> {
            Log.d(TAG, "SETTINGS: Test notification button clicked");
            if (isBound && rideBridgeService != null) {
                // Send a test notification to the tablet via Bluetooth
                try {
                    org.json.JSONObject testNotif = new org.json.JSONObject();
                    testNotif.put("type", "notification");
                    testNotif.put("appPackage", "com.whatsapp");
                    testNotif.put("appName", "WhatsApp");
                    testNotif.put("sender", "Test Contact");
                    testNotif.put("message", "This is a test notification");
                    testNotif.put("timestamp", System.currentTimeMillis());
                    
                    rideBridgeService.getBluetoothManager().sendMessage(testNotif.toString(), null);
                    Log.d(TAG, "SETTINGS: Test notification sent to tablet");
                } catch (Exception e) {
                    Log.e(TAG, "SETTINGS: Error sending test notification: " + e.getMessage());
                }
            }
        });
        
        // Auto-start service if enabled
        if (autoStart) {
            Log.d(TAG, "SETTINGS: Auto-starting service");
            isServiceRunning = true;
            startService(new Intent(this, RideBridgeService.class));
            Intent serviceIntent = new Intent(this, RideBridgeService.class);
            bindService(serviceIntent, serviceConnection, 0);
            updateServiceButton();
        }
    }
    
    private void updateRoleLabel(boolean isTabletMode) {
        roleLabelText.setText(isTabletMode ? "Tablet" : "Phone");
        statusLabel.setTextColor(isTabletMode ? Color.GREEN : Color.RED);
    }
    
    private void updateTestNotificationButtonVisibility(boolean isTabletMode) {
        // Only show test button in phone mode
        btnTestNotification.setVisibility(isTabletMode ? View.GONE : View.VISIBLE);
    }
    
    private void updateNotificationPreferencesVisibility(boolean isTabletMode) {
        // Only show notification preferences in phone mode
        notificationPreferencesSection.setVisibility(isTabletMode ? View.GONE : View.VISIBLE);
    }
    
    private void saveRoleSettings(boolean isTabletMode) {
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("is_tablet", isTabletMode).apply();
    }
    
    private void saveAutoStartSettings(boolean autoStart) {
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("auto_start", autoStart).apply();
    }
    
    private void populateNotificationAppsList(SharedPreferences prefs) {
        notificationAppsList.removeAllViews();
        
        // Priority apps to show first - these are hardcoded
        String[] priorityApps = {
            "com.android.phone",
            "com.android.messaging",
            "com.google.android.apps.messaging",
            "com.android.mms",
            "com.facebook.orca",
            "com.whatsapp"
        };
        
        java.util.Set<String> addedApps = new java.util.HashSet<>();
        android.content.pm.PackageManager pm = getPackageManager();
        
        // Add priority apps first (even if system apps)
        for (String packageName : priorityApps) {
            try {
                android.content.pm.ApplicationInfo app = pm.getApplicationInfo(packageName, 0);
                String appLabel = pm.getApplicationLabel(app).toString();
                addedApps.add(packageName);
                addCheckboxForApp(packageName, appLabel, prefs);
                Log.d(TAG, "SETTINGS: Added priority app: " + appLabel + " (" + packageName + ")");
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                Log.d(TAG, "SETTINGS: Priority app not found: " + packageName);
            }
        }
        
        // Get all installed packages
        java.util.List<android.content.pm.ApplicationInfo> apps = pm.getInstalledApplications(0);
        Log.d(TAG, "SETTINGS: Found " + apps.size() + " total installed apps");
        
        // Debug: log ALL non-system apps
        for (android.content.pm.ApplicationInfo app : apps) {
            if ((app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0) {
                Log.d(TAG, "SETTINGS: Non-system app available: " + app.packageName);
            }
        }
        
        // Add other apps (EXCLUDE system apps for this part)
        for (android.content.pm.ApplicationInfo app : apps) {
            String packageName = app.packageName;
            if (addedApps.contains(packageName)) continue;
            
            // Skip system apps - only show user-installed apps in the dynamic list
            if ((app.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            
            // Show ALL non-system apps (user can decide which ones to enable)
            try {
                String appLabel = pm.getApplicationLabel(app).toString();
                addedApps.add(packageName);
                addCheckboxForApp(packageName, appLabel, prefs);
                Log.d(TAG, "SETTINGS: Added user app: " + appLabel + " (" + packageName + ")");
            } catch (Exception e) {
                Log.w(TAG, "Error getting label for " + packageName);
            }
        }
        
        Log.d(TAG, "SETTINGS: Total apps added to notification list: " + addedApps.size());
    }
    
    private void addCheckboxForApp(String packageName, String appLabel, SharedPreferences prefs) {
        CheckBox checkbox = new CheckBox(this);
        checkbox.setText(appLabel);
        checkbox.setTextColor(getResources().getColor(android.R.color.darker_gray));
        checkbox.setTextSize(13);
        checkbox.setChecked(prefs.getBoolean("notify_" + packageName, false));
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = 5;
        checkbox.setLayoutParams(params);
        
        checkbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("notify_" + packageName, isChecked).apply();
            Log.d(TAG, "SETTINGS: " + appLabel + " notifications " + (isChecked ? "enabled" : "disabled"));
        });
        
        notificationAppsList.addView(checkbox);
    }
    
    private void toggleService() {
        Intent serviceIntent = new Intent(this, RideBridgeService.class);
        
        if (isServiceRunning) {
            Log.d(TAG, "SETTINGS: Stopping service");
            stopService(serviceIntent);
            isServiceRunning = false;
        } else {
            Log.d(TAG, "SETTINGS: Starting service");
            startService(serviceIntent);
            // Also bind to it so we can observe logs
            bindService(serviceIntent, serviceConnection, 0);
            isServiceRunning = true;
        }
        
        updateServiceButton();
    }
    
    private void updateServiceButton() {
        btnStart.setText(isServiceRunning ? "Stop Service" : "Start Service");
    }
    
    private void observeServiceState() {
        if (rideBridgeService == null) return;
        
        // Set initial service state to running since we just bound
        isServiceRunning = true;
        updateServiceButton();
        
        // Observe status
        rideBridgeService.getStatusLiveData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String status) {
                Log.d(TAG, "SETTINGS: Status updated: " + status);
                statusLabel.setText(status);
                isServiceRunning = true; // Service is running if we're getting updates
                updateServiceButton();
                statusIndicator.setBackgroundColor(
                    status.contains("Initializing") ? Color.YELLOW :
                    status.contains("listening") || status.contains("syncing") ? Color.GREEN :
                    Color.RED
                );
            }
        });
        
        // Observe logs
        rideBridgeService.getLogLiveData().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String log) {
                logView.setText(log);
                
                // Auto-scroll to top
                logScrollView.post(() -> logScrollView.fullScroll(ScrollView.FOCUS_UP));
            }
        });
        
        // Observe media state
        rideBridgeService.getMediaStateLiveData().observe(this, new Observer<RideBridgeService.MediaState>() {
            @Override
            public void onChanged(RideBridgeService.MediaState mediaState) {
                if (mediaState != null) {
                    currentTrackLabel.setText(mediaState.artist + " - " + mediaState.track);
                }
            }
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        
        Log.d(TAG, "SETTINGS: Activity destroyed");
    }
}
