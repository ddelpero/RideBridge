package com.ddelpero.ridebridge.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.graphics.Color;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.widget.LinearLayout;
import android.content.IntentFilter;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.content.ComponentName;
import android.content.Context;
import org.json.JSONObject;
import java.util.List;

import android.content.SharedPreferences;

import com.ddelpero.ridebridge.R;
import com.ddelpero.ridebridge.core.BluetoothManager;
import com.ddelpero.ridebridge.core.BluetoothManager.OnMessageReceived;



public class MainActivity extends AppCompatActivity {

    // 1. Class-level declarations (the "fields")
    private boolean isServiceRunning = false;
    private BluetoothManager bm = new BluetoothManager();
    private TextView statusLabel;
    private TextView logView;
    private Button btnStart;
    private SwitchCompat roleSwitch;
    private View statusIndicator;

    private MediaSessionManager mm;

//    private void saveSettings() {
//        SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
//        prefs.edit()
//                .putBoolean("is_tablet", roleSwitch.isChecked())
//                .putBoolean("auto_start", autoStartCheckBox.isChecked()) // Assuming you add a checkbox
//                .apply();
//    }

    private final android.content.BroadcastReceiver mediaSyncReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(Context context, android.content.Intent intent) {
            android.util.Log.d("RideBridge", "MAIN: Received broadcast, triggering syncMedia()...");
            // Add a tiny delay (200ms) to let the Android System update the MediaSession
            // before we try to read the new track name.
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                syncMedia();
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

        // 2. Link variables to the XML IDs
        statusLabel = findViewById(R.id.statusLabel);
        logView = findViewById(R.id.logView);
        btnStart = findViewById(R.id.btnStart);
        roleSwitch = findViewById(R.id.roleSwitch);
        statusIndicator = findViewById(R.id.statusIndicator);

        // Initialize the manager here
        mm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

        if (btnStart != null) {
            android.util.Log.d("RideBridge", "SENDER_DEBUG: Button found. Object ID: " + System.identityHashCode(btnStart));
        } else {
            android.util.Log.e("RideBridge", "SENDER_DEBUG: Button NOT found!");
        }

        registerReceiver(mediaSyncReceiver,
                new android.content.IntentFilter("com.ddelpero.SYNC_MEDIA"),
                Context.RECEIVER_EXPORTED);

        // Force the OS to recognize and bind to the service
        ComponentName componentName = new ComponentName(this, com.ddelpero.ridebridge.ui.NotificationReceiver.class);
        getPackageManager().setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
        );

//        // AUTOSTART LOGIC
//        roleSwitch.setChecked(true); // Default to Receiver
//        statusLabel.setText("CURRENT MODE: RECEIVER (AUTO)");
//        statusLabel.setTextColor(Color.GREEN);
//        statusIndicator.setBackgroundColor(Color.GREEN);
//        logView.setText("SYSTEM: Autostarting Server...");
//
//        // Start listening immediately
//        bm.startEmulatorListener(data -> {
//            runOnUiThread(() -> logView.setText(data));
//        }, "TABLET");

        // 3. Switch Listener: Changes UI immediately when toggled
        LinearLayout playbackControls = findViewById(R.id.playbackControls);

        roleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                statusLabel.setText("CURRENT MODE: RECEIVER (TABLET)");
                statusLabel.setTextColor(Color.GREEN);
                playbackControls.setVisibility(View.VISIBLE); // Show buttons on Tablet

                // Tablet Logic: Send strings when buttons are clicked
                findViewById(R.id.btnPlay).setOnClickListener(v -> bm.sendCommandToPhone("PLAY"));
                findViewById(R.id.btnPause).setOnClickListener(v -> bm.sendCommandToPhone("PAUSE"));
                findViewById(R.id.btnNext).setOnClickListener(v -> bm.sendCommandToPhone("NEXT"));
                findViewById(R.id.btnPrev).setOnClickListener(v -> bm.sendCommandToPhone("PREV"));
            } else {
                statusLabel.setText("CURRENT MODE: SENDER (PHONE)");
                statusLabel.setTextColor(Color.RED);
                playbackControls.setVisibility(View.GONE); // Hide buttons on Phone
            }
        });



        // 4. Button Listener: Actually triggers the BluetoothManager
        btnStart.setOnClickListener(v -> {
            android.util.Log.d("RideBridge", "BUTTON_CLICK_PROCESSED"); // ADD THIS LINE HERE
            checkSystemListenerStatus();
            // 1. Identify EXACTLY which button instance and thread are running
            int viewHash = System.identityHashCode(v);
            long threadId = Thread.currentThread().getId();
            String timestamp = String.valueOf(System.currentTimeMillis());

            android.util.Log.d("RideBridge", "DEBUG PROOF: Click Detected!");
            android.util.Log.d("RideBridge", "DEBUG PROOF: [View ID: " + viewHash + "] [Thread ID: " + threadId + "] [Time: " + timestamp + "]");

            // 2. Check the state BEFORE we do anything
            android.util.Log.d("RideBridge", "DEBUG PROOF: isServiceRunning is currently: " + isServiceRunning);

//            if (isServiceRunning) {
//                android.util.Log.w("RideBridge", "DEBUG PROOF: Execution BLOCKED by boolean check.");
//                return;
//            }

            // 3. Dump the stack trace to see WHAT triggered this click
            // This will show up in Logcat and tell us if it's a UI click,
            // an automated event, or a layout re-draw.
            java.lang.StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (int i = 0; i < Math.min(stackTrace.length, 10); i++) {
                android.util.Log.v("RideBridge", "STACK TRACE [" + i + "]: " + stackTrace[i].toString());
            }

            isServiceRunning = true;

            boolean isDisplay = roleSwitch.isChecked();
            if (isDisplay) {
                statusLabel.setText("Status: Online (Listening...)"); // Add this
                logView.setText("Waiting for data from Phone...");

                bm.startEmulatorListener(data -> {
                    runOnUiThread(() -> {
                        statusLabel.setText("Status: Online (Connected)");
                        logView.setText("RECEIVED: " + data);
                    });
                }, "TABLET_RECEIVER");
            } else {
                statusLabel.setText("Status: Sender Active");
                logView.setText("Searching for Media Sessions...");

//                // 2. Start the listener so the Phone can hear Tablet commands
//                bm.startEmulatorListener(data -> {
//                    if (data.startsWith("CONTROL:")) {
//                        handleRemoteControl(data.split(":")[1]);
//                    }
//                }, "PHONE_SENDER");

//                // 3. Sync initial media (the delay ensures the socket is ready)
//                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
//                    syncMedia();
//                }, 500);

                android.util.Log.d("RideBridge", "DEBUG: Sender mode initiated. Calling syncMedia()...");
                bm.setServiceActive(true);
                syncMedia();

                // Add a check to see if logView was updated by syncMedia
//                if (logView.getText().toString().equals("Searching for Media Sessions...")) {
//                    logView.setText("SYSTEM: Search timed out or failed.");
//                }
            }

        });
    }

    private void handleRemoteControl(String command) {
        // We must run this on the Main UI Thread because MediaControllers
        // prefer being accessed there.
        runOnUiThread(() -> {
            try {
                MediaSessionManager mm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
                List<MediaController> controllers = mm.getActiveSessions(new ComponentName(this, NotificationReceiver.class));

                if (controllers != null && !controllers.isEmpty()) {
                    MediaController.TransportControls controls = controllers.get(0).getTransportControls();
                    android.util.Log.d("RideBridge", "PHONE: Executing " + command);

                    switch (command) {
                        case "PLAY":  controls.play(); break;
                        case "PAUSE": controls.pause(); break;
                        case "NEXT":  controls.skipToNext(); break;
                        case "PREV":  controls.skipToPrevious(); break;
                    }
                }
            } catch (Exception e) {
                android.util.Log.e("RideBridge", "PHONE: Control Error: " + e.getMessage());
            }
        });
    }

    private void syncMedia() {
        android.util.Log.i("RideBridge", "SENDER: syncMedia execution started"); // TRACE 1
        try {
//            MediaSessionManager mm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName("com.ddelpero.ridebridge", "com.ddelpero.ridebridge.ui.NotificationReceiver");
//            List<MediaController> controllers = mm.getActiveSessions(new ComponentName(this, NotificationReceiver.class));
            List<MediaController> controllers = mm.getActiveSessions(cn);
            android.util.Log.d("RideBridge", "SENDER: Found " + controllers.size() + " controllers");

            if (controllers != null && !controllers.isEmpty()) {
                android.util.Log.i("RideBridge", "SENDER: Found " + controllers.size() + " sessions"); // TRACE 2
                MediaController player = controllers.get(0);
                MediaMetadata meta = player.getMetadata();

                // Get Playback State (Playing vs Paused)
                android.media.session.PlaybackState state = player.getPlaybackState();
                boolean isPlaying = (state != null && state.getState() == android.media.session.PlaybackState.STATE_PLAYING);

                if (meta != null) {
                    JSONObject json = new JSONObject();
                    json.put("type", "media");
                    json.put("artist", meta.getString(MediaMetadata.METADATA_KEY_ARTIST));
                    json.put("track", meta.getString(MediaMetadata.METADATA_KEY_TITLE));
                    json.put("playing", isPlaying); // Added playing status

                    String payload = json.toString();
                    android.util.Log.d("RideBridge", "SENDER: Preparing to send: " + payload);
                    bm.sendMessage(payload, command -> {
                        // This is the implementation of OnMessageReceived
                        handleRemoteControl(command);}
                    );
                } else {
                    android.util.Log.d("RideBridge", "SENDER: Player found, but no metadata (is music playing?)");
                }
            } else {
                android.util.Log.i("RideBridge", "SENDER: No active media sessions found."); // TRACE 3
                logView.setText("SYSTEM: No Music Playing");
            }
        } catch (SecurityException e) {
            android.util.Log.e("RideBridge", "SENDER: syncMedia error: " + e.getMessage());
        } catch (Exception e) {
            android.util.Log.e("RideBridge", "SENDER: syncMedia error: " + e.getMessage());
        }
    }

//    public static class NotificationReceiver extends android.service.notification.NotificationListenerService {
//        // This is a stub that allows the app to appear in the Settings list.
//        // In the next step, we can move the media logic here for automatic syncing.
//    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Stop listening when the app is closed
        try {
            unregisterReceiver(mediaSyncReceiver);
        } catch (Exception e) {
            // Already unregistered
        }
    }
}