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
    private TextView statusLabel;
    private TextView logView;
    private View statusIndicator;
    private TextView roleLabelText;
    private TextView currentTrackLabel;
    private ScrollView logScrollView;
    
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
        statusLabel = findViewById(R.id.statusLabel);
        logView = findViewById(R.id.logView);
        statusIndicator = findViewById(R.id.statusIndicator);
        roleLabelText = findViewById(R.id.roleLabelText);
        currentTrackLabel = findViewById(R.id.currentTrackLabel);
        logScrollView = findViewById(R.id.logScrollView);
        
        // Load saved preferences
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        boolean isTabletMode = prefs.getBoolean("is_tablet", false);
        boolean autoStart = prefs.getBoolean("auto_start", false);
        
        // Set UI initial state
        roleSwitch.setChecked(isTabletMode);
        autoStartCheckBox.setChecked(autoStart);
        updateRoleLabel(isTabletMode);
        
        // Role Switch Listener
        roleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            Log.d(TAG, "SETTINGS: Role switched to " + (isChecked ? "TABLET" : "PHONE"));
            updateRoleLabel(isChecked);
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
        
        // Start Button
        btnStart.setOnClickListener(v -> {
            Log.d(TAG, "SETTINGS: Start/Stop button clicked");
            toggleService();
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
    
    private void saveRoleSettings(boolean isTabletMode) {
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("is_tablet", isTabletMode).apply();
    }
    
    private void saveAutoStartSettings(boolean autoStart) {
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("auto_start", autoStart).apply();
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
