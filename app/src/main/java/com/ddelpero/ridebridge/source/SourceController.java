package com.ddelpero.ridebridge.source;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.media.session.MediaSessionManager;
import android.media.session.MediaController;
import android.media.MediaMetadata;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;
import android.speech.RecognizerIntent;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.ddelpero.ridebridge.core.BluetoothManager;
import com.ddelpero.ridebridge.core.RideBridgeService;
import com.ddelpero.ridebridge.ui.NotificationReceiver;

public class SourceController {

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final RideBridgeService service;
    private final MediaSessionManager mediaSessionManager;
    private OnSourceDataReady sourceDataListener;
    private OnRemoteCommandReceived remoteCommandListener;
    private MediaController.Callback mediaControllerCallback;
    private BroadcastReceiver syncMediaReceiver;
    private int messageSequence = 0;

    public interface OnSourceDataReady {
        void onMediaDataReady(String mediaJson);
    }

    public interface OnRemoteCommandReceived {
        void onCommandReceived(String command);
    }

    public SourceController(RideBridgeService service, BluetoothManager bluetoothManager) {
        this.service = service;
        this.context = service;
        this.bluetoothManager = bluetoothManager;
        this.mediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    // Backward compatibility constructor for code that doesn't have service reference
    public SourceController(Context context, BluetoothManager bluetoothManager) {
        this.service = null;
        this.context = context;
        this.bluetoothManager = bluetoothManager;
        this.mediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    private void log(String message) {
        if (service != null) {
            service.log(message);
        } else {
            Log.d("RideBridge", message);
        }
    }

    public void setSourceDataListener(OnSourceDataReady listener) {
        this.sourceDataListener = listener;
        log("SOURCE: sourceDataListener set to: " + (listener != null ? listener.getClass().getName() : "null"));
    }

    public void setRemoteCommandListener(OnRemoteCommandReceived listener) {
        this.remoteCommandListener = listener;
    }

    public void start() {
        log("SOURCE: Starting source controller (phone/sender mode)...");
        bluetoothManager.setServiceActive(true);

        // Register callback for playback state changes once
        mediaControllerCallback = new MediaController.Callback() {
            @Override
            public void onPlaybackStateChanged(android.media.session.PlaybackState state) {
                if (state != null) {
                    // This fires precisely when YT Music flips from 2 (Paused) to 3 (Playing)
                    log("SOURCE: Internal playback state updated: " + state.getState());
                    syncMediaData();
                }
            }
        };

        // Register broadcast receiver for SYNC_MEDIA notifications
        syncMediaReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                log("SOURCE: SYNC_MEDIA broadcast received");
                syncMediaData();
            }
        };

        IntentFilter filter = new IntentFilter("com.ddelpero.SYNC_MEDIA");
        context.registerReceiver(syncMediaReceiver, filter, Context.RECEIVER_EXPORTED);
        log("SOURCE: Registered broadcast receiver for SYNC_MEDIA");

        // Initial sync on startup
        syncMediaData();
    }

    public void stop() {
        log("SOURCE: Stopping source controller...");
        bluetoothManager.setServiceActive(false);
        unregisterMediaCallback();

        // Unregister broadcast receiver
        if (syncMediaReceiver != null) {
            try {
                context.unregisterReceiver(syncMediaReceiver);
                log("SOURCE: Unregistered broadcast receiver");
            } catch (Exception e) {
                log("SOURCE: Error unregistering receiver: " + e.getMessage());
            }
            syncMediaReceiver = null;
        }
    }

    public void syncMediaData() {
        log("SOURCE: syncMediaData execution started");

        try {
            ComponentName cn = new ComponentName("com.ddelpero.ridebridge", "com.ddelpero.ridebridge.ui.NotificationReceiver");
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);
            log("SOURCE: Found " + controllers.size() + " media controllers");

            if (controllers != null && !controllers.isEmpty()) {
                log("SOURCE: Found " + controllers.size() + " active sessions");
                MediaController player = controllers.get(0);
                MediaMetadata meta = player.getMetadata();
                String encodedImage = "";

                // Unregister and re-register to prevent duplicate callbacks
                player.unregisterCallback(mediaControllerCallback);
                player.registerCallback(mediaControllerCallback);

                // Get Playback State (Playing vs Paused)
                android.media.session.PlaybackState state = player.getPlaybackState();
                boolean isPlaying = (state != null && state.getState() == android.media.session.PlaybackState.STATE_PLAYING);

                if (meta != null) {
                    // Try to get the bitmap
                    Bitmap art = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                    if (art == null) {
                        art = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
                    }

                    if (art != null) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        art.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                        byte[] b = baos.toByteArray();
                        encodedImage = Base64.encodeToString(b, Base64.NO_WRAP);
                        log("SOURCE: Album art encoded successfully");
                    }

                    long position = (state != null) ? state.getPosition() : 0;
                    long duration = (meta != null) ? meta.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;

                    JSONObject json = new JSONObject();
                    json.put("type", "media");
                    json.put("seq", ++messageSequence);
                    json.put("artist", meta.getString(MediaMetadata.METADATA_KEY_ARTIST));
                    json.put("track", meta.getString(MediaMetadata.METADATA_KEY_TITLE));
                    json.put("playing", isPlaying);
                    json.put("albumArt", encodedImage);
                    json.put("position", position);
                    json.put("duration", duration);
                    json.put("speed", (state != null) ? state.getPlaybackSpeed() : 0f);

                    String payload = json.toString();

                    log("SOURCE: About to send message via BT");
                    bluetoothManager.sendMessage(payload, this::handleRemoteControl);
                    log("SOURCE: sendMessage returned");

                    log("SOURCE: About to call sourceDataListener, listener is " + (sourceDataListener == null ? "NULL" : "SET"));
                    if (sourceDataListener != null) {
                        log("SOURCE: Calling listener now...");
                        log("SOURCE: About to invoke listener.onMediaDataReady with payload length=" + payload.length());
                        sourceDataListener.onMediaDataReady(payload);
                        log("SOURCE: Listener.onMediaDataReady() returned successfully");
                        log("SOURCE: Listener call completed");
                    } else {
                        log("SOURCE: sourceDataListener is null!");
                    }

                } else {
                    log("SOURCE: Player found, but no metadata (is music playing?)");
                    if (sourceDataListener != null) {
                        sourceDataListener.onMediaDataReady("");
                    }
                }
            } else {
                log("SOURCE: No active media sessions found.");
            }

        } catch (SecurityException e) {
            Log.e("RideBridge", "SOURCE: syncMediaData SecurityException: " + e.getMessage());
        } catch (Exception e) {
            Log.e("RideBridge", "SOURCE: syncMediaData error: " + e.getMessage());
        }
    }

    private void handleRemoteControl(String command) {
        log("SOURCE: Received remote command: " + command);

        if (remoteCommandListener != null) {
            remoteCommandListener.onCommandReceived(command);
        }

        // Execute the command on the media controller
        try {
            log("SOURCE: Looking for active media sessions...");
            ComponentName cn = new ComponentName(context.getPackageName(), "com.ddelpero.ridebridge.ui.NotificationReceiver");
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);

            if (controllers != null && !controllers.isEmpty()) {
                log("SOURCE: Found " + controllers.size() + " active media sessions");

                // Use the first available (active) media controller
                MediaController controller = controllers.get(0);
                MediaController.TransportControls controls = controller.getTransportControls();
                log("SOURCE: Got transport controls, executing: " + command);

                if (command.startsWith("SEEK:")) {
                    long seekPos = Long.parseLong(command.split(":")[1]);
                    log("SOURCE: Seeking to " + seekPos);
                    controls.seekTo(seekPos);
                } else {
                    switch (command) {
                        case "PLAY":
                            log("SOURCE: Calling play()");
                            controls.play();
                            break;
                        case "PAUSE":
                            log("SOURCE: Calling pause()");
                            controls.pause();
                            break;
                        case "NEXT":
                            log("SOURCE: Calling skipToNext()");
                            controls.skipToNext();
                            break;
                        case "PREV":
                            log("SOURCE: Calling skipToPrevious()");
                            controls.skipToPrevious();
                            break;
                        case "VOICE":
                            Intent assistantIntent = new Intent(Intent.ACTION_VOICE_COMMAND);
                            assistantIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            context.startActivity(assistantIntent);
                            log("SOURCE: Launching voice assistant");
                            break;
                    }
                }
            } else {
                log("SOURCE: No active media sessions found! NotificationReceiver may not be enabled.");
            }
        } catch (Exception e) {
            log("SOURCE: Control Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void unregisterMediaCallback() {
        // Called during stop - unregister callback to clean up
        if (mediaControllerCallback != null) {
            try {
                ComponentName cn = new ComponentName("com.ddelpero.ridebridge", "com.ddelpero.ridebridge.ui.NotificationReceiver");
                List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);
                if (controllers != null && !controllers.isEmpty()) {
                    controllers.get(0).unregisterCallback(mediaControllerCallback);
                    Log.d("RideBridge", "SOURCE: Unregistered callback from media controller");
                }
            } catch (Exception e) {
                Log.w("RideBridge", "SOURCE: Error unregistering callback: " + e.getMessage());
            }
        }
    }
}
