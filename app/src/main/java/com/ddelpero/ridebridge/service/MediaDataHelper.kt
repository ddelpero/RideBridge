package com.ddelpero.ridebridge.service

import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Base64
import com.ddelpero.ridebridge.core.MediaState
import java.io.ByteArrayOutputStream

object MediaDataHelper {
    // fun getMediaJson(controller: MediaController?): String {
    //     if (controller == null) return """{"type":"NONE"}"""
    //
    //     val metadata = controller.metadata
    //     val state = controller.playbackState
    //
    //     // Extract Metadata
    //     val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
    //     val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown"
    //     val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
    //
    //     // Handle Album Art (Convert Bitmap to Base64)
    //     val albumArtBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
    //         ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
    //     val albumArtBase64 = encodeBitmapToBase64(albumArtBitmap)
    //
    //     // Extract Playback State
    //     val isPlaying = state?.state == PlaybackState.STATE_PLAYING
    //     val position = state?.position ?: 0L
    //     val speed = state?.playbackSpeed ?: 0.0f
    //
    //     return """
    //         {
    //             "type": "MEDIA",
    //             "track": "$title",
    //             "artist": "$artist",
    //             "isPlaying": $isPlaying,
    //             "position": $position,
    //             "duration": $duration,
    //             "playbackSpeed": $speed,
    //             "albumArt": "$albumArtBase64"
    //         }
    //     """.trimIndent()
    // }

    fun extractState(controller: MediaController?): MediaState {
        if (controller == null) return MediaState()

        val metadata = controller.metadata
        val state = controller.playbackState

        return MediaState(
            track = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown",
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown",
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            position = state?.position ?: 0L,
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            playbackSpeed = state?.playbackSpeed ?: 1.0f,
            albumArt = encodeBitmapToBase64(metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART))
        )
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap?): String {
        if (bitmap == null) return ""
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream) // Compress to 70% quality
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}