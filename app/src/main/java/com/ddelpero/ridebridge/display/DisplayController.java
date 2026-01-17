package com.ddelpero.ridebridge.display;

import android.util.Log;
import android.graphics.Bitmap;
import android.util.Base64;
import org.json.JSONObject;

import com.ddelpero.ridebridge.core.BluetoothManager;

public class DisplayController {

    private final BluetoothManager bluetoothManager;
    private OnDisplayDataReceived displayListener;
    private OnCommandSend commandSendListener;

    public interface OnDisplayDataReceived {
        void onMediaDataReceived(MediaData mediaData);
    }

    public interface OnCommandSend {
        void onSendCommand(String command);
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

    public DisplayController(BluetoothManager bluetoothManager) {
        this.bluetoothManager = bluetoothManager;
    }

    public void setDisplayDataListener(OnDisplayDataReceived listener) {
        this.displayListener = listener;
    }

    public void setCommandSendListener(OnCommandSend listener) {
        this.commandSendListener = listener;
    }

    public void startListening() {
        Log.d("RideBridge", "DISPLAY: Starting listener for tablet mode...");

        bluetoothManager.startEmulatorListener(data -> {
            Log.d("RideBridge", "DISPLAY: Raw data received: " + data);
            try {
                JSONObject json = new JSONObject(data);
                MediaData mediaData = parseMediaData(json);

                if (displayListener != null) {
                    displayListener.onMediaDataReceived(mediaData);
                }
            } catch (Exception e) {
                Log.e("RideBridge", "DISPLAY: Error parsing media data: " + e.getMessage());
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
                    Log.d("RideBridge", "DISPLAY: Album art decoded successfully");
                } catch (Exception e) {
                    Log.w("RideBridge", "DISPLAY: Failed to decode album art: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            Log.e("RideBridge", "DISPLAY: Error parsing JSON: " + e.getMessage());
        }

        return data;
    }

    public void sendPlayCommand() {
        Log.d("RideBridge", "DISPLAY: Sending PLAY command");
        bluetoothManager.sendCommandToPhone("PLAY");
        if (commandSendListener != null) {
            commandSendListener.onSendCommand("PLAY");
        }
    }

    public void sendPauseCommand() {
        Log.d("RideBridge", "DISPLAY: Sending PAUSE command");
        bluetoothManager.sendCommandToPhone("PAUSE");
        if (commandSendListener != null) {
            commandSendListener.onSendCommand("PAUSE");
        }
    }

    public void sendNextCommand() {
        Log.d("RideBridge", "DISPLAY: Sending NEXT command");
        bluetoothManager.sendCommandToPhone("NEXT");
        if (commandSendListener != null) {
            commandSendListener.onSendCommand("NEXT");
        }
    }

    public void sendPreviousCommand() {
        Log.d("RideBridge", "DISPLAY: Sending PREVIOUS command");
        bluetoothManager.sendCommandToPhone("PREV");
        if (commandSendListener != null) {
            commandSendListener.onSendCommand("PREV");
        }
    }

    public void sendSeekCommand(long positionMs) {
        String command = "SEEK:" + positionMs;
        Log.d("RideBridge", "DISPLAY: Sending SEEK command: " + command);
        bluetoothManager.sendCommandToPhone(command);
        if (commandSendListener != null) {
            commandSendListener.onSendCommand(command);
        }
    }
}
