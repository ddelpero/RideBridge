package com.ddelpero.ridebridge.core

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.MutableLiveData
import com.ddelpero.ridebridge.Widget.Architect

object MediaManager {
    // 1. For the Phone UI (Dashboard/MainActivity)
    val liveData = MutableLiveData<MediaState>()

    var currentState: MediaState? = null
    private var lastTrackName: String? = null // Defined here locally
    private var lastSentHadArt: Boolean = false
    private var currentBitmap: Bitmap? = null
    private var lastIsPlaying: Boolean? = null

    // 2. For the Tablet (The Socket)
    private var dataTransport: ((String) -> Unit)? = null
    var onStateChanged: ((MediaState) -> Unit)? = null

    fun setTransport(callback: (String) -> Unit) {
        dataTransport = callback
    }

    fun resetTrackCache() {
        lastTrackName = null
        lastSentHadArt = false
    }

    fun formatMediaJson(state: MediaState): String {
        val art = state.albumArt.orEmpty()
        val trackChanged = state.track != lastTrackName
        val includeArt = art.isNotEmpty() && (trackChanged || !lastSentHadArt)
        lastTrackName = state.track
        if (trackChanged && art.isEmpty()) {
            lastSentHadArt = false
        } else if (includeArt) {
            lastSentHadArt = true
        }
        currentState = state
        liveData.postValue(state)
        return org.json.JSONObject().apply {
            put("type", "MEDIA")
            put("track", state.track)
            put("artist", state.artist)
            put("isPlaying", state.isPlaying)
            put("position", state.position)
            put("duration", state.duration)
            put("playbackSpeed", state.playbackSpeed)
            put("albumArt", if (includeArt) art else "")
        }.toString()
    }

    private var lastTrack: String? = null

    fun updateFromJson(jsonString: String, isConnected: Boolean) {
        try {
            val json = org.json.JSONObject(jsonString)
            val type = json.optString("type")
            if (type == "NAV" || type == "VOICE" || type == "NOTIF") return

            val track = json.optString("track", "Unknown")
            val artist = json.optString("artist", "Unknown")
            val isPlaying = json.optBoolean("isPlaying", false)
            val artString = json.optString("albumArt", null)

            // Only decode Bitmap if the track actually changed
            if (track != lastTrackName) {
                currentBitmap = Architect.decodeBase64(artString)
            } else {
                currentBitmap = null
            }

            val state = MediaState(
                isConnected = isConnected,
                track = track,
                artist = artist,
                isPlaying = isPlaying,
                position = json.optLong("position", 0L),
                duration = json.optLong("duration", 0L),
                playbackSpeed = json.optDouble("playbackSpeed", 1.0).toFloat(),
                albumArt = artString,
                albumArtBitmap = currentBitmap
            )

            // Save to the variable the Provider will look for
            currentState = state

            // 3. ALWAYS update the local UI (Dashboard/Settings)
            // This handles the smooth 300ms seek bar and timer updates
            // onStateChanged?.invoke(state)

            // 4. SMART Update for Remote Widget
            // Only trigger the system broadcast if visual metadata changed.
            // This skips the 300ms "position" spam, preventing the 8192 socket error.
            // if (track != lastTrackName || isPlaying != lastIsPlaying) {
            //     lastTrackName = track
            //     lastIsPlaying = isPlaying
            //     onStateChanged?.invoke(state)
            //     // Trigger the home screen widget update
            //     // RideBridgeWidgetProvider.triggerUpdate(context)
            // }
            liveData.postValue(state)

        } catch (e: Exception) {
            android.util.Log.e("MediaManager", "Failed to parse: ${e.message}")
        }
    }

    fun send(payload: String) {
        Log.d("MediaManager", "Forwarding JSON to Client")
        dataTransport?.invoke(payload)
    }
}

