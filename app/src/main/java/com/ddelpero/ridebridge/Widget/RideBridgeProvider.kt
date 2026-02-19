package com.ddelpero.ridebridge.Widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import com.ddelpero.ridebridge.R
import com.ddelpero.ridebridge.communication.Manager
import com.ddelpero.ridebridge.core.MediaManager

class RideBridgeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        update(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent) // This handles calling onUpdate() automatically

        when (intent.action) {
            ACTION_PLAY_PAUSE -> {
                Log.d("WidgetClick", "Play/Pause pressed")
                Manager.sendCommandToClient("PLAY")
            }

            ACTION_NEXT_TRACK -> {
                Log.d("WidgetClick", "Next pressed")
                Manager.sendCommandToClient("NEXT")
            }

            ACTION_PREV_TRACK -> {
                Log.d("WidgetClick", "Previous pressed")
                Manager.sendCommandToClient("PREV")
            }

            ACTION_VOICE_ASSIST -> {
                Log.d("WidgetClick", "Voice Assist")
                Manager.sendCommandToClient("VOICE_ASSIST")
            }
        }
    }

    companion object {

        const val ACTION_PLAY_PAUSE = "com.ddelpero.ridebridge.ACTION_PLAY_PAUSE"
        const val ACTION_PREV_TRACK = "com.ddelpero.ridebridge.ACTION_PREV_TRACK"
        const val ACTION_NEXT_TRACK = "com.ddelpero.ridebridge.ACTION_NEXT_TRACK"
        const val ACTION_VOICE_ASSIST = "com.ddelpero.ridebridge.ACTION_VOICE_ASSIST"

        // Helper to simplify the call from your Manager
        fun triggerUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(
                    context,
                    RideBridgeWidgetProvider::class.java
                )
            )
            if (ids.isNotEmpty()) {
                update(context, manager, ids)
            }
        }


        private fun setWidgetClick(
            context: Context,
            views: RemoteViews,
            viewId: Int,
            actionName: String,
            requestCode: Int
        ) {
            val intent = Intent(context, RideBridgeWidgetProvider::class.java).apply {
                action = actionName
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(viewId, pendingIntent)
        }

        fun update(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.widget_ridebridge)

                // Pull data from Managers
                val state = MediaManager.liveData.value
                val isConnected = Manager.connectionStatus.value ?: false

                val statusIcon =
                    if (isConnected) R.drawable.ic_circle_green else R.drawable.ic_circle_red
                views.setImageViewResource(R.id.widget_connection_status, statusIcon)

                // 3. Update Media Fields
                if (state != null) {
                    views.setTextViewText(R.id.widget_track, state.track)
                    views.setTextViewText(R.id.widget_artist, state.artist)

                    // Update Play/Pause icon
                    val playIcon =
                        if (state.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                    views.setImageViewResource(R.id.widget_play_pause, playIcon)

                    setWidgetClick(
                        context,
                        views,
                        R.id.widget_play_pause,
                        RideBridgeWidgetProvider.ACTION_PLAY_PAUSE,
                        1
                    )
                    setWidgetClick(
                        context,
                        views,
                        R.id.widget_next,
                        RideBridgeWidgetProvider.ACTION_NEXT_TRACK,
                        2
                    )
                    setWidgetClick(
                        context,
                        views,
                        R.id.widget_prev,
                        RideBridgeWidgetProvider.ACTION_PREV_TRACK,
                        3
                    )
                    setWidgetClick(
                        context,
                        views,
                        R.id.widget_voice_command,
                        RideBridgeWidgetProvider.ACTION_VOICE_ASSIST,
                        4
                    )


                    // Update Album Art (MediaManager already decoded this to a Bitmap)
                    state.albumArtBitmap?.let { bitmap ->
                        views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                    }
                }

                // 4. Tell the manager to refresh the specific widget ID
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}

