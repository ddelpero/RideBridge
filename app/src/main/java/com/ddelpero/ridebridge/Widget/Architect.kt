package com.ddelpero.ridebridge.Widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.RemoteViews
import com.ddelpero.ridebridge.R
import com.ddelpero.ridebridge.core.MediaState

// This isn't used anymore
object Architect {
    /**
     * This function builds the "blueprint" of your UI.
     * It can be used by the AppWidgetManager (for the dashboard)
     * OR by your MainActivity (for the tablet preview).
     */
    fun buildRemoteViews(context: Context, state: MediaState): RemoteViews {
        // 1. Reference your existing XML
        val views = RemoteViews(context.packageName, R.layout.widget_ridebridge)

        val intent = Intent() // Empty intent
        val pi = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        views.setOnClickPendingIntent(R.id.widget_root, pi)

        // 2. Map the data to the IDs in that XML
        views.setTextViewText(R.id.widget_track, state.track)
        views.setTextViewText(R.id.widget_artist, state.artist)

        var playButtonRes = android.R.drawable.ic_media_play
        if (state.isPlaying) {
            playButtonRes = android.R.drawable.ic_media_pause
        }
        views.setImageViewResource(R.id.widget_play_pause, playButtonRes)

        val playPauseIntent = Intent(context, RideBridgeWidgetProvider::class.java).apply {
            action = RideBridgeWidgetProvider.ACTION_PLAY_PAUSE
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1, // Unique ID for this specific button
            playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_play_pause, pendingIntent)

        // 3. Handle the album art logic once for both places
        // Use the pre-decoded bitmap. If null, use default art.
        if (state.albumArtBitmap != null) {
            views.setImageViewBitmap(R.id.widget_album_art, state.albumArtBitmap)
        }
        // if (state.isConnected == false){
        //     // Only reset to default if the track actually changed to avoid flickering
        //     views.setImageViewResource(R.id.widget_album_art, 0)
        // }

        val statusIcon = if (state.isConnected) {
            R.drawable.ic_circle_green // Make sure this exists in your res/drawable
        } else {
            R.drawable.ic_circle_red
        }

        views.setImageViewResource(R.id.widget_connection_status, statusIcon)



        return views
    }

    fun decodeBase64(input: String?): Bitmap? {
        if (input.isNullOrEmpty()) return null

        return try {
            // 1. Convert the Base64 string into a byte array
            val decodedBytes = Base64.decode(input, Base64.DEFAULT)

            // 2. Turn those bytes into an Android Bitmap
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            android.util.Log.e("BitmapUtils", "Failed to decode base64: ${e.message}")
            null
        }
    }
}