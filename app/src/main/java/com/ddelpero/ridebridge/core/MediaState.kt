package com.ddelpero.ridebridge.core

// In your .core package
data class MediaState(
    val isConnected: Boolean = false,
    val track: String = "No Title",
    val artist: String = "Unknown Artist",
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val playbackSpeed: Float = 0.0f,
    val albumArt: String? = null, // Base64 string
    val albumArtBitmap: android.graphics.Bitmap? = null

){
    override fun toString(): String{
        return "MediaState(track='$track', artist='$artist', isPlaying=$isPlaying, " +
                "position=$position, duration=$duration, playbackSpeed=$playbackSpeed, " +
                "albumArt=${if (albumArt != null) "[Image Data]" else "null"})"
    }
}