package com.ddelpero.ridebridge.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.graphics.Color;
import android.graphics.BitmapFactory;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;
import android.widget.ImageView;

import android.widget.LinearLayout;
import android.content.IntentFilter;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.content.ComponentName;
import android.content.Context;

import android.graphics.Bitmap;

import org.json.JSONObject;

import java.util.List;
import java.io.ByteArrayOutputStream;

import android.util.Base64;

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
    private CheckBox autoStartCheckBox;
    private View statusIndicator;

    private boolean isCurrentlyPlaying = false; // Track state for the toggle

    private MediaSessionManager mm;

    private void saveRoleSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("is_tablet", roleSwitch.isChecked()).apply();
        android.util.Log.d("RideBridge", "Role Saved: " + roleSwitch.isChecked());
    }

    private void saveAutoStartSettings() {
        android.content.SharedPreferences prefs = getSharedPreferences("RideBridgePrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("auto_start", autoStartCheckBox.isChecked()).apply();
        android.util.Log.d("RideBridge", "AutoStart Saved: " + autoStartCheckBox.isChecked());
    }

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
        autoStartCheckBox = findViewById(R.id.autoStartCheckBox);
        statusIndicator = findViewById(R.id.statusIndicator);

        // Initialize the manager here
        mm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);

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
                        bm.sendCommandToPhone("PAUSE");
                    } else {
                        bm.sendCommandToPhone("PLAY");
                    }
                });

                findViewById(R.id.btnNext).setOnClickListener(v -> bm.sendCommandToPhone("NEXT"));
                findViewById(R.id.btnPrev).setOnClickListener(v -> bm.sendCommandToPhone("PREV"));
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
//                        statusLabel.setText("Status: Online (Connected)");
//                        logView.setText("RECEIVED: " + data);
                        try {
                            org.json.JSONObject json = new org.json.JSONObject(data);

                            // 1. Update the Play/Pause Icon
                            if (json.has("playing")) {
                                isCurrentlyPlaying = json.getBoolean("playing");
                                android.widget.ImageButton btnPlayPause = findViewById(R.id.btnPlayPause);
                                if (isCurrentlyPlaying) {
                                    btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                                } else {
                                    btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                                }
                            }

                            if (json.has("albumArt")) {
                                String encodedImage = json.getString("albumArt");
                                ImageView imgArt = findViewById(R.id.imgAlbumArt);

                                if (!encodedImage.isEmpty()) {
                                    byte[] decodedString = Base64.decode(encodedImage, Base64.DEFAULT);
                                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);

                                    runOnUiThread(() -> imgArt.setImageBitmap(decodedByte));
                                } else {
                                    // Set a default "no art" icon if empty
                                    runOnUiThread(() -> imgArt.setImageResource(android.R.drawable.ic_menu_gallery));
                                }
                            }

                            // 2. Update Text Labels
                            statusLabel.setText("Status: Online (Connected)");
                            String track = json.optString("track", "Unknown Title");
                            String artist = json.optString("artist", "Unknown Artist");
                            logView.setText(track + "\n" + artist);

                        } catch (Exception e) {
                            // Fallback if the data isn't JSON
                            logView.setText("RECEIVED: " + data);
                        }
                    });
                }, "TABLET_RECEIVER");
            } else {
                statusLabel.setText("Status: Sender Active");
                logView.setText("Searching for Media Sessions...");


                android.util.Log.d("RideBridge", "DEBUG: Sender mode initiated. Calling syncMedia()...");
                bm.setServiceActive(true);
                syncMedia();
            }

        });

        if (prefs.getBoolean("auto_start", false)) {
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                android.util.Log.d("RideBridge", "MAIN: Executing Auto-Start");
                btnStart.performClick();
            }, 1000);
        }
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
                        case "PLAY":
                            controls.play();
                            break;
                        case "PAUSE":
                            controls.pause();
                            break;
                        case "NEXT":
                            controls.skipToNext();
                            break;
                        case "PREV":
                            controls.skipToPrevious();
                            break;
                    }


                }
            } catch (Exception e) {
                android.util.Log.e("RideBridge", "PHONE: Control Error: " + e.getMessage());
            }
        });
    }

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(android.media.session.PlaybackState state) {
            if (state != null) {
                // This fires precisely when YT Music flips from 2 (Paused) to 3 (Playing)
                android.util.Log.d("RideBridge", "SENDER: Internal state updated: " + state.getState());
                syncMedia();
            }
        }
    };

    private void syncMedia() {
        android.util.Log.i("RideBridge", "SENDER: syncMedia execution started"); // TRACE 1
        try {
            ComponentName cn = new ComponentName("com.ddelpero.ridebridge", "com.ddelpero.ridebridge.ui.NotificationReceiver");
            List<MediaController> controllers = mm.getActiveSessions(cn);
            android.util.Log.d("RideBridge", "SENDER: Found " + controllers.size() + " controllers");

            if (controllers != null && !controllers.isEmpty()) {
                android.util.Log.i("RideBridge", "SENDER: Found " + controllers.size() + " sessions"); // TRACE 2
                MediaController player = controllers.get(0);
                MediaMetadata meta = player.getMetadata();
                String encodedImage = "";

                player.unregisterCallback(controllerCallback); // Prevent duplicates
                player.registerCallback(controllerCallback);

                // Get Playback State (Playing vs Paused)
                android.media.session.PlaybackState state = player.getPlaybackState();

                boolean isPlaying = (state != null && state.getState() == android.media.session.PlaybackState.STATE_PLAYING);

                if (meta != null) {
                    // Try to get the bitmap
                    Bitmap art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    // Fallback: some apps use METADATA_KEY_ART
                    if (art == null) {
                        art = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
                    }

                    if (art != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        // Compress to JPEG to keep the JSON size manageable
                        art.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                        byte[] b = baos.toByteArray();
                        encodedImage = Base64.encodeToString(b, Base64.DEFAULT);
                    }

                    JSONObject json = new JSONObject();
                    json.put("type", "media");
                    json.put("artist", meta.getString(MediaMetadata.METADATA_KEY_ARTIST));
                    json.put("track", meta.getString(MediaMetadata.METADATA_KEY_TITLE));
                    json.put("playing", isPlaying); // Added playing status
                    json.put("albumArt", encodedImage);
                    String payload = json.toString();
                    android.util.Log.d("RideBridge", "SENDER: Preparing to send: " + payload);
                    bm.sendMessage(payload, command -> {
                                // This is the implementation of OnMessageReceived
                                handleRemoteControl(command);
                            }
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