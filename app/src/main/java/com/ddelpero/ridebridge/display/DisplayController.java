package com.ddelpero.ridebridge.display;

import android.util.Log;
import android.graphics.Bitmap;
import android.util.Base64;
import org.json.JSONObject;

import com.ddelpero.ridebridge.core.BluetoothManager;
import com.ddelpero.ridebridge.core.RideBridgeService;

public class DisplayController {

    private final BluetoothManager bluetoothManager;
    private final RideBridgeService service;
    private OnDisplayDataReceived displayListener;
    private OnCommandSend commandSendListener;
    private OnRawDataReceived rawDataListener;

    public interface OnDisplayDataReceived {
        void onMediaDataReceived(MediaData mediaData);
    }

    public interface OnCommandSend {
        void onSendCommand(String command);
    }
    
    public interface OnRawDataReceived {
        void onRawDataReceived(String rawJson);
    }

    public static class MediaData {
        public String track;
        public String artist;
        public boolean isPlaying;
        public Bitmap albumArt;
        public long position;
        public long duration;
        public float playbackSpeed;

        public MediaData() {
            this.track = "Unknown Title";
            this.artist = "Unknown Artist";
            this.isPlaying = false;
            this.position = 0;
            this.duration = 0;
            this.playbackSpeed = 0;
        }
    }

    public DisplayController(RideBridgeService service, BluetoothManager bluetoothManager) {
        this.service = service;
        this.bluetoothManager = bluetoothManager;
    }

    // Backward compatibility constructor for code that doesn't have service reference
    public DisplayController(BluetoothManager bluetoothManager) {
        this.service = null;
        this.bluetoothManager = bluetoothManager;
    }

    private void log(String message) {
        if (service != null) {
            service.log(message);
        } else {
            Log.d("RideBridge", message);
        }
    }

    public void setDisplayDataListener(OnDisplayDataReceived listener) {
        this.displayListener = listener;
    }

    public void setCommandSendListener(OnCommandSend listener) {
        this.commandSendListener = listener;
    }
    
    public void setRawDataListener(OnRawDataReceived listener) {
        this.rawDataListener = listener;
    }

    public void startListening() {
        log("DISPLAY: Starting listener for tablet mode...");

        bluetoothManager.startEmulatorListener(data -> {
            log("DISPLAY: Raw data received: " + data);
            
            // Notify raw data listener
            if (rawDataListener != null) {
                rawDataListener.onRawDataReceived(data);
            }
            
            try {
                JSONObject json = new JSONObject(data);
                MediaData mediaData = parseMediaData(json);

                log("DISPLAY: Parsed media - track=" + mediaData.track + ", artist=" + mediaData.artist);
                
                // Update widget
                if (service != null) {
                    service.updateWidget(mediaData);
                }
                
                if (displayListener != null) {
                    log("DISPLAY: Calling displayListener callback");
                    displayListener.onMediaDataReceived(mediaData);
                }
            } catch (Exception e) {
                log("DISPLAY: Error parsing media data: " + e.getMessage());
                e.printStackTrace();
            }
        }, "TABLET_RECEIVER");
    }

    private MediaData parseMediaData(JSONObject json) {
        MediaData data = new MediaData();

        try {
            data.track = json.optString("track", "Unknown Title");
            data.artist = json.optString("artist", "Unknown Artist");
            data.isPlaying = json.optBoolean("playing", false);
            data.position = json.optLong("position", 0);
            data.duration = json.optLong("duration", 0);
            // CRITICAL: If not playing, force speed to 0 so the progress ticker doesn't advance
            data.playbackSpeed = data.isPlaying ? (float) json.optDouble("speed", 1.0) : 0.0f;

            // Decode album art if present
            String encodedImage = json.optString("albumArt", "");
            if (!encodedImage.isEmpty()) {
                try {
                    byte[] decodedBytes = Base64.decode(encodedImage, Base64.DEFAULT);
                    data.albumArt = android.graphics.BitmapFactory.decodeByteArray(
                            decodedBytes, 0, decodedBytes.length
                    );
                    log("DISPLAY: Album art decoded successfully");
                } catch (Exception e) {
                    log("DISPLAY: Failed to decode album art: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            log("DISPLAY: Error parsing JSON: " + e.getMessage());
        }

        return data;
    }

    public void sendPlayCommand() {
        log("DISPLAY: Sending PLAY command");
        bluetoothManager.sendCommandToPhone("PLAY");
        if (commandSendListener != null) {
            commandSendListener.onSendCommand("PLAY");
        }
    }

    public void sendPauseCommand() {
        log("DISPLAY: Sending PAUSE command");
        bluetoothManager.sendCommandToPhone("PAUSE");
        if (commandSendListener != null) {
            commandSendListener.onSendCommand("PAUSE");
        }
    }

    public void sendNextCommand() {
        log("DISPLAY: Sending NEXT command");
        bluetoothManager.sendCommandToPhone("NEXT");
        if (commandSendListener != null) {
            commandSendListener.onSendCommand("NEXT");
        }
    }

    public void sendPreviousCommand() {
        log("DISPLAY: Sending PREVIOUS command");
        bluetoothManager.sendCommandToPhone("PREV");
        if (commandSendListener != null) {
            commandSendListener.onSendCommand("PREV");
        }
    }

    public void sendSeekCommand(long positionMs) {
        String command = "SEEK:" + positionMs;
        log("DISPLAY: Sending SEEK command: " + command);
        bluetoothManager.sendCommandToPhone(command);
        if (commandSendListener != null) {
            commandSendListener.onSendCommand(command);
        }
    }
}
