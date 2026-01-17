package com.ddelpero.ridebridge.source;

import android.content.Context;
import android.content.ComponentName;
import android.media.session.MediaSessionManager;
import android.media.session.MediaController;
import android.media.MediaMetadata;
import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.List;

import com.ddelpero.ridebridge.core.BluetoothManager;
import com.ddelpero.ridebridge.ui.NotificationReceiver;

public class SourceController {

    private final Context context;
    private final BluetoothManager bluetoothManager;
    private final MediaSessionManager mediaSessionManager;
    private OnSourceDataReady sourceDataListener;
    private OnRemoteCommandReceived remoteCommandListener;
    private MediaController.Callback mediaControllerCallback;

    public interface OnSourceDataReady {
        void onMediaDataReady(String mediaJson);
    }

    public interface OnRemoteCommandReceived {
        void onCommandReceived(String command);
    }

    public SourceController(Context context, BluetoothManager bluetoothManager) {
        this.context = context;
        this.bluetoothManager = bluetoothManager;
        this.mediaSessionManager = (MediaSessionManager) context.getSystemService(Context.MEDIA_SESSION_SERVICE);
    }

    public void setSourceDataListener(OnSourceDataReady listener) {
        this.sourceDataListener = listener;
    }

    public void setRemoteCommandListener(OnRemoteCommandReceived listener) {
        this.remoteCommandListener = listener;
    }

    public void start() {
        Log.d("RideBridge", "SOURCE: Starting source controller (phone/sender mode)...");
        bluetoothManager.setServiceActive(true);
        syncMediaData();
    }

    public void stop() {
        Log.d("RideBridge", "SOURCE: Stopping source controller...");
        bluetoothManager.setServiceActive(false);
        unregisterMediaCallback();
    }

    public void syncMediaData() {
        Log.i("RideBridge", "SOURCE: syncMediaData execution started");

        try {
            ComponentName cn = new ComponentName("com.ddelpero.ridebridge", "com.ddelpero.ridebridge.ui.NotificationReceiver");
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);
            Log.d("RideBridge", "SOURCE: Found " + controllers.size() + " media controllers");

            if (controllers != null && !controllers.isEmpty()) {
                Log.i("RideBridge", "SOURCE: Found " + controllers.size() + " active sessions");
                MediaController player = controllers.get(0);
                MediaMetadata meta = player.getMetadata();
                String encodedImage = "";

                // Register callback for playback state changes
                unregisterMediaCallback();
                mediaControllerCallback = new MediaController.Callback() {
                    @Override
                    public void onPlaybackStateChanged(android.media.session.PlaybackState state) {
                        if (state != null) {
                            Log.d("RideBridge", "SOURCE: Internal playback state updated: " + state.getState());
                            syncMediaData();
                        }
                    }
                };
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
                        encodedImage = Base64.encodeToString(b, Base64.DEFAULT);
                        Log.d("RideBridge", "SOURCE: Album art encoded successfully");
                    }

                    long position = (state != null) ? state.getPosition() : 0;
                    long duration = (meta != null) ? meta.getLong(MediaMetadata.METADATA_KEY_DURATION) : 0;

                    JSONObject json = new JSONObject();
                    json.put("type", "media");
                    json.put("artist", meta.getString(MediaMetadata.METADATA_KEY_ARTIST));
                    json.put("track", meta.getString(MediaMetadata.METADATA_KEY_TITLE));
                    json.put("playing", isPlaying);
                    json.put("albumArt", encodedImage);
                    json.put("position", position);
                    json.put("duration", duration);
                    json.put("speed", (state != null) ? state.getPlaybackSpeed() : 0f);

                    String payload = json.toString();
                    Log.d("RideBridge", "SOURCE: Preparing to send: " + payload);

                    bluetoothManager.sendMessage(payload, this::handleRemoteControl);

                    if (sourceDataListener != null) {
                        sourceDataListener.onMediaDataReady(payload);
                    }

                } else {
                    Log.d("RideBridge", "SOURCE: Player found, but no metadata (is music playing?)");
                }
            } else {
                Log.i("RideBridge", "SOURCE: No active media sessions found.");
            }

        } catch (SecurityException e) {
            Log.e("RideBridge", "SOURCE: syncMediaData SecurityException: " + e.getMessage());
        } catch (Exception e) {
            Log.e("RideBridge", "SOURCE: syncMediaData error: " + e.getMessage());
        }
    }

    private void handleRemoteControl(String command) {
        Log.d("RideBridge", "SOURCE: Received remote command: " + command);

        if (remoteCommandListener != null) {
            remoteCommandListener.onCommandReceived(command);
        }

        // Execute the command on the media controller
        try {
            ComponentName cn = new ComponentName("com.ddelpero.ridebridge", "com.ddelpero.ridebridge.ui.NotificationReceiver");
            List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);

            if (controllers != null && !controllers.isEmpty()) {
                MediaController.TransportControls controls = controllers.get(0).getTransportControls();
                Log.d("RideBridge", "SOURCE: Executing " + command);

                if (command.startsWith("SEEK:")) {
                    long seekPos = Long.parseLong(command.split(":")[1]);
                    Log.d("RideBridge", "SOURCE: Seeking to " + seekPos);
                    controls.seekTo(seekPos);
                } else {
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
            }
        } catch (Exception e) {
            Log.e("RideBridge", "SOURCE: Control Error: " + e.getMessage());
        }
    }

    private void unregisterMediaCallback() {
        // This will be called when stopping or re-registering
        if (mediaControllerCallback != null) {
            try {
                ComponentName cn = new ComponentName("com.ddelpero.ridebridge", "com.ddelpero.ridebridge.ui.NotificationReceiver");
                List<MediaController> controllers = mediaSessionManager.getActiveSessions(cn);
                if (controllers != null && !controllers.isEmpty()) {
                    controllers.get(0).unregisterCallback(mediaControllerCallback);
                }
            } catch (Exception e) {
                Log.w("RideBridge", "SOURCE: Error unregistering callback: " + e.getMessage());
            }
        }
    }
}
