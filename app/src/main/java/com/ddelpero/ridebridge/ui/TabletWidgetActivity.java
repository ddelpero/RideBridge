package com.ddelpero.ridebridge.ui;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.ddelpero.ridebridge.R;
import com.ddelpero.ridebridge.core.RideBridgeService;

import java.util.Locale;

public class TabletWidgetActivity extends AppCompatActivity {
    
    private static final String TAG = "RideBridge";
    
    // UI Elements
    private ImageView imgAlbumArt;
    private TextView txtTrackName;
    private TextView txtArtistName;
    private TextView txtCurrentTime;
    private TextView txtTotalTime;
    private SeekBar mediaSeekBar;
    private ImageButton btnPlayPause;
    private ImageButton btnPrev;
    private ImageButton btnNext;
    private ImageButton btnSettings;
    
    // Service
    private RideBridgeService rideBridgeService;
    private boolean isBound = false;
    private boolean isPlaying = false;
    private long totalDuration = 0;
    
    // Progress update
    private Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressRunnable;
    
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "TABLET_WIDGET: Connected to RideBridgeService");
            RideBridgeService.LocalBinder binder = (RideBridgeService.LocalBinder) service;
            rideBridgeService = binder.getService();
            isBound = true;
            
            // Observe service state
            observeServiceState();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "TABLET_WIDGET: Disconnected from RideBridgeService");
            isBound = false;
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tablet_widget);
        
        Log.d(TAG, "TABLET_WIDGET: Activity created");
        
        // Link UI elements
        imgAlbumArt = findViewById(R.id.imgAlbumArt);
        txtTrackName = findViewById(R.id.txtTrackName);
        txtArtistName = findViewById(R.id.txtArtistName);
        txtCurrentTime = findViewById(R.id.txtCurrentTime);
        txtTotalTime = findViewById(R.id.txtTotalTime);
        mediaSeekBar = findViewById(R.id.mediaSeekBar);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnSettings = findViewById(R.id.btnSettings);
        
        // Set up seek bar listener
        mediaSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateTimeLabels(progress, totalDuration);
                }
            }
            
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                int progress = seekBar.getProgress();
                Log.d(TAG, "TABLET_WIDGET: User seeked to " + progress);
                if (isBound && rideBridgeService != null) {
                    // Send seek command through display controller
                }
            }
        });
        
        // Play/Pause button
        btnPlayPause.setOnClickListener(v -> {
            if (isPlaying) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
            }
            isPlaying = !isPlaying;
        });
        
        // Previous button
        btnPrev.setOnClickListener(v -> {
            Log.d(TAG, "TABLET_WIDGET: Previous button clicked");
        });
        
        // Next button
        btnNext.setOnClickListener(v -> {
            Log.d(TAG, "TABLET_WIDGET: Next button clicked");
        });
        
        // Settings button
        btnSettings.setOnClickListener(v -> {
            Log.d(TAG, "TABLET_WIDGET: Settings button clicked");
            Intent intent = new Intent(TabletWidgetActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        
        // Set up progress ticker
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPlaying) {
                    int currentPosition = mediaSeekBar.getProgress();
                    updateTimeLabels(currentPosition, totalDuration);
                    mediaSeekBar.setProgress(currentPosition + 1000); // Increment by 1 second
                }
                progressHandler.postDelayed(this, 1000);
            }
        };
        
        // Bind to service
        Intent serviceIntent = new Intent(this, RideBridgeService.class);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        startService(serviceIntent);
    }
    
    private void observeServiceState() {
        if (rideBridgeService == null) return;
        
        // Observe media state
        rideBridgeService.getMediaStateLiveData().observe(this, new Observer<RideBridgeService.MediaState>() {
            @Override
            public void onChanged(RideBridgeService.MediaState mediaState) {
                if (mediaState != null) {
                    Log.d(TAG, "TABLET_WIDGET: Media state updated");
                    
                    txtTrackName.setText(mediaState.track);
                    txtArtistName.setText(mediaState.artist);
                    
                    if (mediaState.albumArt != null) {
                        imgAlbumArt.setImageBitmap(mediaState.albumArt);
                    }
                    
                    totalDuration = mediaState.duration;
                    mediaSeekBar.setMax((int) totalDuration);
                    mediaSeekBar.setProgress((int) mediaState.position);
                    
                    isPlaying = mediaState.isPlaying;
                    if (isPlaying) {
                        btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                        progressHandler.post(progressRunnable);
                    } else {
                        btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                        progressHandler.removeCallbacks(progressRunnable);
                    }
                    
                    updateTimeLabels((int) mediaState.position, totalDuration);
                }
            }
        });
    }
    
    private void updateTimeLabels(int currentMs, long totalMs) {
        txtCurrentTime.setText(formatTime(currentMs));
        txtTotalTime.setText(formatTime((int) totalMs));
    }
    
    private String formatTime(long ms) {
        int seconds = (int) (ms / 1000);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if (isPlaying) {
            progressHandler.post(progressRunnable);
        }
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        progressHandler.removeCallbacks(progressRunnable);
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        
        progressHandler.removeCallbacks(progressRunnable);
        
        Log.d(TAG, "TABLET_WIDGET: Activity destroyed");
    }
}
