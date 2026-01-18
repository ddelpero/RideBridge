package com.ddelpero.ridebridge.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

import com.ddelpero.ridebridge.R;
import com.ddelpero.ridebridge.core.RideBridgeService;
import com.ddelpero.ridebridge.display.DisplayController;

public class RideBridgeWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "RideBridge_Widget";
    public static final String ACTION_WIDGET_COMMAND = "com.ddelpero.ridebridge.WIDGET_COMMAND";
    public static final String COMMAND_KEY = "command";
    
    // Cache current playing state to determine which command to send
    private static boolean lastKnownPlayingState = false;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate called with " + appWidgetIds.length + " widgets");
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateWidget(Context context, DisplayController.MediaData mediaData) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        ComponentName componentName = new ComponentName(context, RideBridgeWidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(componentName);
        
        // Update the cached playing state
        lastKnownPlayingState = mediaData.isPlaying;
        Log.d(TAG, "Cached playing state: " + lastKnownPlayingState);
        
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_ridebridge);
            
            // Update track info
            views.setTextViewText(R.id.widget_track, mediaData.track != null ? mediaData.track : "");
            views.setTextViewText(R.id.widget_artist, mediaData.artist != null ? mediaData.artist : "");
            
            // Format and update time displays
            String currentTimeStr = formatTime(mediaData.position);
            String totalTimeStr = formatTime(mediaData.duration);
            views.setTextViewText(R.id.widget_current_time, currentTimeStr);
            views.setTextViewText(R.id.widget_total_time, totalTimeStr);
            
            // Update progress - CRITICAL: only update if duration is valid
            if (mediaData.duration > 0) {
                int progress = (int) ((mediaData.position * 100) / mediaData.duration);
                views.setProgressBar(R.id.widget_progress, 100, Math.max(0, Math.min(100, progress)), false);
                Log.d(TAG, "Widget progress: " + progress + "% (pos=" + mediaData.position + ", dur=" + mediaData.duration + ")");
            } else {
                views.setProgressBar(R.id.widget_progress, 100, 0, false);
            }
            
            // Update play/pause button - CRITICAL: toggle icon based on playing state
            int playButtonRes = mediaData.isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;
            views.setImageViewResource(R.id.widget_play_pause, playButtonRes);
            Log.d(TAG, "Widget play button: " + (mediaData.isPlaying ? "PAUSE" : "PLAY"));
            
            // Update album art if available
            if (mediaData.albumArt != null) {
                views.setImageViewBitmap(R.id.widget_album_art, mediaData.albumArt);
            }
            
            // Set button handlers - determine which command to send based on current state
            views.setOnClickPendingIntent(R.id.widget_prev, getPendingIntent(context, "PREV"));
            views.setOnClickPendingIntent(R.id.widget_play_pause, 
                getPendingIntent(context, mediaData.isPlaying ? "PAUSE" : "PLAY"));
            views.setOnClickPendingIntent(R.id.widget_next, getPendingIntent(context, "NEXT"));

            // Voice Command
            views.setOnClickPendingIntent(R.id.widget_voice_command, getPendingIntent(context, "VOICE"));
            
            appWidgetManager.updateAppWidget(appWidgetId, views);
            Log.d(TAG, "Widget " + appWidgetId + " updated: " + mediaData.track + " | Playing: " + mediaData.isPlaying);
        }
    }
    
    private static String formatTime(long millis) {
        if (millis <= 0) return "0:00";
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes + ":" + String.format("%02d", secs);
    }

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_ridebridge);

        // Set up button click handlers
        views.setOnClickPendingIntent(R.id.widget_prev, getPendingIntent(context, "PREV"));
        views.setOnClickPendingIntent(R.id.widget_play_pause, getPendingIntent(context, lastKnownPlayingState ? "PAUSE" : "PLAY"));
        views.setOnClickPendingIntent(R.id.widget_next, getPendingIntent(context, "NEXT"));

        // Update with default values
        views.setTextViewText(R.id.widget_track, "Not playing");
        views.setTextViewText(R.id.widget_artist, "");
        views.setProgressBar(R.id.widget_progress, 100, 0, false);

        appWidgetManager.updateAppWidget(appWidgetId, views);
        Log.d(TAG, "Widget updated: " + appWidgetId);
    }

    private static PendingIntent getPendingIntent(Context context, String command) {
        Intent intent = new Intent(context, RideBridgeWidgetProvider.class);
        intent.setAction(ACTION_WIDGET_COMMAND);
        intent.putExtra(COMMAND_KEY, command);
        Log.d(TAG, "Creating PendingIntent for command: " + command);
        return PendingIntent.getBroadcast(context, command.hashCode(), intent, 
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        
        if (ACTION_WIDGET_COMMAND.equals(intent.getAction())) {
            String command = intent.getStringExtra(COMMAND_KEY);
            Log.d(TAG, "Widget command received: " + command);
            
            // Send broadcast to the already-running service
            Intent broadcastIntent = new Intent("com.ddelpero.ridebridge.WIDGET_COMMAND");
            broadcastIntent.putExtra("command", command);
            context.sendBroadcast(broadcastIntent);
        }
    }

    @Override
    public void onEnabled(Context context) {
        Log.d(TAG, "Widget provider enabled");
    }

    @Override
    public void onDisabled(Context context) {
        Log.d(TAG, "Widget provider disabled");
    }
}
