package com.ddelpero.ridebridge.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.ddelpero.ridebridge.core.MediaState
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

object MediaDataHelper {
    private const val TAG = "MediaDataHelper"
    private const val MAX_ART_PX = 256
    private const val JPEG_QUALITY = 60

    fun extractState(controller: MediaController?): MediaState {
        if (controller == null) return MediaState()

        val metadata = controller.metadata
        val state = controller.playbackState
        val bitmap = bitmapFromMetadata(metadata)

        return MediaState(
            track = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown",
            artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown",
            isPlaying = state?.state == PlaybackState.STATE_PLAYING,
            position = state?.position ?: 0L,
            duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            playbackSpeed = state?.playbackSpeed ?: 1.0f,
            albumArt = encodeBitmapToBase64(bitmap)
        )
    }

    fun hasEmbeddedArt(controller: MediaController?): Boolean {
        val metadata = controller?.metadata ?: return false
        return bitmapFromMetadata(metadata) != null || !artUri(metadata).isNullOrEmpty()
    }

    fun artUri(controller: MediaController?): String? = artUri(controller?.metadata)

    fun artUri(metadata: MediaMetadata?): String? {
        if (metadata == null) return null
        return listOf(
            MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
            MediaMetadata.METADATA_KEY_ART_URI,
            MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI
        ).firstNotNullOfOrNull { key ->
            metadata.getString(key)?.takeIf { it.isNotBlank() }
        }
    }

    fun encodeArtFromUri(context: Context, uriString: String): String {
        return try {
            val uri = Uri.parse(uriString)
            val stream = when (uri.scheme?.lowercase()) {
                "http", "https" -> {
                    val conn = URL(uriString).openConnection() as HttpURLConnection
                    conn.connectTimeout = 3000
                    conn.readTimeout = 3000
                    conn.instanceFollowRedirects = true
                    conn.inputStream
                }
                else -> context.contentResolver.openInputStream(uri)
            } ?: return ""
            stream.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return ""
                encodeBitmapToBase64(bitmap)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load art URI: ${e.message}")
            ""
        }
    }

    private fun bitmapFromMetadata(metadata: MediaMetadata?): Bitmap? {
        if (metadata == null) return null
        return metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap?): String {
        if (bitmap == null) return ""
        val scaled = scaleBitmap(bitmap, MAX_ART_PX)
        val outputStream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        if (scaled !== bitmap) {
            scaled.recycle()
        }
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val largest = max(bitmap.width, bitmap.height)
        if (largest <= maxSize || largest <= 0) return bitmap
        val scale = maxSize.toFloat() / largest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    }
}
