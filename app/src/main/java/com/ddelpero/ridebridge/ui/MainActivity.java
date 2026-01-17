package com.ddelpero.ridebridge.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.graphics.Color;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.SeekBar;

import java.util.Locale;

import com.ddelpero.ridebridge.R;
import com.ddelpero.ridebridge.core.BluetoothManager;
import com.ddelpero.ridebridge.core.RideBridgeService;
import com.ddelpero.ridebridge.display.DisplayController;
import com.ddelpero.ridebridge.source.SourceController;


public class MainActivity extends AppCompatActivity {

    // 1. Class-level declarations (the "fields")
    private boolean isServiceRunning = false;
    private BluetoothManager bluetoothManager = new BluetoothManager();
    private DisplayController displayController;
    private SourceController sourceController;
    
    private TextView statusLabel;
    private TextView logView;
    private Button btnStart;
    private SwitchCompat roleSwitch;
    private CheckBox autoStartCheckBox;
    private View statusIndicator;

    private boolean isCurrentlyPlaying = false; // Track state for the toggle

    private long lastPosition = 0;
    private long totalDuration = 0;
    private float playbackSpeed = 0;
    private long lastUpdateTime = 0;

    // Create a handler to "tick" the seekbar
    private final Handler progressHandler = new Handler(Looper.getMainLooper());

    private void saveRoleSettings() {
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("is_tablet", roleSwitch.isChecked()).apply();
        android.util.Log.d("RideBridge", "Role Saved: " + roleSwitch.isChecked());
    }

    private void saveAutoStartSettings() {
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("auto_start", autoStartCheckBox.isChecked()).apply();
        android.util.Log.d("RideBridge", "AutoStart Saved: " + autoStartCheckBox.isChecked());
    }

    private final android.content.BroadcastReceiver mediaSyncReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            android.util.Log.d("RideBridge", "MAIN: Received broadcast, triggering source sync...");
            // Add a tiny delay (200ms) to let the Android System update the MediaSession
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                if (sourceController != null) {
                    sourceController.syncMediaData();
                }
            }, 200);
        }
    };

    private void checkSystemListenerStatus() {
        String packageName = getPackageName();
        String flat = android.provider.Settings.Secure.getString(getContentResolver(),
                "enabled_notification_listeners");

        android.util.Log.d("RideBridge_DIAG", "1. Current Package: " + packageName);
        android.util.Log.d("RideBridge_DIAG", "2. OS Enabled List: " + flat);

        boolean isEnabled = flat != null && flat.contains(packageName);
        android.util.Log.d("RideBridge_DIAG", "3. Is my app in the OS list? " + isEnabled);

        // Check if the service is actually running/bound
        android.app.ActivityManager manager = (android.app.ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (android.app.ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (com.ddelpero.ridebridge.ui.NotificationReceiver.class.getName().equals(service.service.getClassName())) {
                android.util.Log.d("RideBridge_DIAG", "4. Service State: RUNNING");
                return;
            }
        }
        android.util.Log.e("RideBridge_DIAG", "4. Service State: NOT RUNNING (The OS has not started your service)");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Check if app was launched by user or auto-start
        boolean isAutoStart = getIntent().getBooleanExtra("auto_start", false);
        
        // 2. Link variables to the XML IDs
        statusLabel = findViewById(R.id.statusLabel);
        logView = findViewById(R.id.logView);
        btnStart = findViewById(R.id.btnStart);
        roleSwitch = findViewById(R.id.roleSwitch);
        autoStartCheckBox = findViewById(R.id.autoStartCheckBox);
        statusIndicator = findViewById(R.id.statusIndicator);

        // Don't initialize controllers yet - they're created on-demand
        // based on which mode the user selects (phone vs tablet)

        registerReceiver(mediaSyncReceiver,
                new IntentFilter("com.ddelpero.SYNC_MEDIA"),
                Context.RECEIVER_EXPORTED);

        // Force the OS to recognize and bind to the service
        ComponentName componentName = new ComponentName(this, NotificationReceiver.class);
        getPackageManager().setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );

        //Load settings
        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);


        // 3. Switch Listener: Changes UI immediately when toggled
        LinearLayout playbackControls = findViewById(R.id.playbackControls);

        roleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                statusLabel.setText("CURRENT MODE: RECEIVER (TABLET)");
                statusLabel.setTextColor(Color.GREEN);
                playbackControls.setVisibility(View.VISIBLE); // Show buttons on Tablet

                // Tablet Logic: Send strings when buttons are clicked
                ImageButton btnPlayPause = findViewById(R.id.btnPlayPause);
                btnPlayPause.setOnClickListener(v -> {
                    // Toggle logic based on the last known state
                    if (isCurrentlyPlaying) {
                        displayController.sendPauseCommand();
                    } else {
                        displayController.sendPlayCommand();
                    }
                });

                findViewById(R.id.btnNext).setOnClickListener(v -> displayController.sendNextCommand());
                findViewById(R.id.btnPrev).setOnClickListener(v -> displayController.sendPreviousCommand());

            } else {
                statusLabel.setText("CURRENT MODE: SENDER (PHONE)");
                statusLabel.setTextColor(Color.RED);
                playbackControls.setVisibility(View.GONE); // Hide buttons on Phone
            }
            saveRoleSettings();
        });
        roleSwitch.setChecked(prefs.getBoolean("is_tablet", false));

        autoStartCheckBox.setChecked(prefs.getBoolean("auto_start", false));

        autoStartCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            saveAutoStartSettings();
        });

        // 4. Button Listener: Delegates to appropriate controller
        btnStart.setOnClickListener(v -> {
            android.util.Log.d("RideBridge", "BUTTON_CLICK_PROCESSED");
            checkSystemListenerStatus();
            
            int viewHash = System.identityHashCode(v);
            long threadId = Thread.currentThread().getId();
            String timestamp = String.valueOf(System.currentTimeMillis());

            android.util.Log.d("RideBridge", "DEBUG PROOF: Click Detected!");
            android.util.Log.d("RideBridge", "DEBUG PROOF: [View ID: " + viewHash + "] [Thread ID: " + threadId + "] [Time: " + timestamp + "]");
            android.util.Log.d("RideBridge", "DEBUG PROOF: isServiceRunning is currently: " + isServiceRunning);

            java.lang.StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (int i = 0; i < Math.min(stackTrace.length, 10); i++) {
                android.util.Log.v("RideBridge", "STACK TRACE [" + i + "]: " + stackTrace[i].toString());
            }

            isServiceRunning = true;

            boolean isTabletMode = roleSwitch.isChecked();
            if (isTabletMode) {
                initializeTabletMode();
            } else {
                initializePhoneMode();
            }
        });

        // Handle auto-start: Start service without showing UI
        if (isAutoStart && prefs.getBoolean("auto_start", false)) {
            android.util.Log.d("RideBridge", "MAIN: Auto-start detected - starting service silently");
            Intent serviceIntent = new Intent(this, RideBridgeService.class);
            serviceIntent.putExtra("auto_start", true);
            startService(serviceIntent);
            // Close the activity so only the service runs in background
            finish();
        } else if (prefs.getBoolean("auto_start", false)) {
            // Auto-start preference is enabled but app was opened manually
            // Start immediately without delay
            android.util.Log.d("RideBridge", "MAIN: Starting service (auto-start enabled)");
            btnStart.performClick();
        }
    }

    private void initializeTabletMode() {
        android.util.Log.d("RideBridge", "MAIN: Initializing TABLET (Display) mode");
        
        // LAZY INITIALIZATION: Only create DisplayController when entering tablet mode
        if (displayController == null) {
            displayController = new DisplayController(bluetoothManager);
            
            // Set up display controller listener NOW that we're using it
            displayController.setDisplayDataListener(mediaData -> {
                runOnUiThread(() -> {
                    updateDisplayUI(mediaData);
                });
            });
        }
        
        statusLabel.setText("Status: Online (Listening...)");
        logView.setText("Waiting for data from Phone...");

        SeekBar seekBar = findViewById(R.id.mediaSeekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateTimeLabels(progress, totalDuration);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Optional: Pause the local ticker while dragging
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                android.util.Log.d("RideBridge", "MAIN: User seeked to " + progress);
                displayController.sendSeekCommand(progress);
            }
        });

        progressHandler.post(progressRunnable);
        displayController.startListening();
    }

    private void initializePhoneMode() {
        android.util.Log.d("RideBridge", "MAIN: Initializing PHONE (Source) mode");
        
        // Phone mode is now handled entirely by RideBridgeService
        // Do NOT create SourceController here - it conflicts with service's instance
        
        statusLabel.setText("Status: Sender Active");
        logView.setText("RideBridgeService is managing phone mode...");
    }

    private void updateDisplayUI(DisplayController.MediaData mediaData) {
        android.util.Log.d("RideBridge", "MAIN: Updating display UI with media data");
        
        isCurrentlyPlaying = mediaData.isPlaying;

        // 1. Update the Play/Pause Icon
        if (roleSwitch.isChecked()) {
            ImageButton btnPlayPause = findViewById(R.id.btnPlayPause);
            if (isCurrentlyPlaying) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            }
        }

        // 2. Update Album Art
        if (mediaData.albumArt != null) {
            ImageView imgArt = findViewById(R.id.imgAlbumArt);
            imgArt.setImageBitmap(mediaData.albumArt);
        } else {
            ImageView imgArt = findViewById(R.id.imgAlbumArt);
            imgArt.setImageResource(android.R.drawable.ic_dialog_info);
        }

        // 3. Update Seekbar Data
        updateSeekBarData(mediaData.position, mediaData.duration, mediaData.playbackSpeed);

        // 4. Update Text Labels
        statusLabel.setText("Status: Online (Connected)");
        logView.setText(mediaData.track + "\n" + mediaData.artist);
    }

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            // 1. Calculate the current position
            long currentPos;
            if (playbackSpeed > 0 && totalDuration > 0) {
                // It's playing: calculate movement based on time elapsed
                long timeDelta = SystemClock.elapsedRealtime() - lastUpdateTime;
                currentPos = lastPosition + (long) (timeDelta * playbackSpeed);
                if (currentPos > totalDuration) currentPos = totalDuration;
            } else {
                // It's paused: stay exactly where the Phone last told us
                currentPos = lastPosition;
            }

            // 2. ALWAYS update the UI with the result
            SeekBar seekBar = findViewById(R.id.mediaSeekBar);
            if (seekBar != null) {
                seekBar.setProgress((int) currentPos);
                updateTimeLabels(currentPos, totalDuration);
            }

            progressHandler.postDelayed(this, 500);
        }
    };

    // Call this when JSON is received
    private void updateSeekBarData(long pos, long dur, float speed) {
        this.lastPosition = pos;
        this.totalDuration = dur;
        this.playbackSpeed = speed;
        this.lastUpdateTime = SystemClock.elapsedRealtime();

        SeekBar seekBar = findViewById(R.id.mediaSeekBar);
        seekBar.setMax((int) dur);
        seekBar.setProgress((int) pos);
    }

    private void updateTimeLabels(long currentMs, long totalMs) {
        TextView txtCurrent = findViewById(R.id.txtCurrentTime);
        TextView txtTotal = findViewById(R.id.txtTotalTime);

        txtCurrent.setText(formatTime(currentMs));
        txtTotal.setText(formatTime(totalMs));
    }

    private String formatTime(long millis) {
        int seconds = (int) (millis / 1000) % 60;
        int minutes = (int) ((millis / (1000 * 60)) % 60);
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop listening when the app is closed
        try {
            unregisterReceiver(mediaSyncReceiver);
        } catch (Exception e) {
            // Already unregistered
        }
        
        // Clean up controllers
        if (sourceController != null) {
            sourceController.stop();
        }
    }
}
