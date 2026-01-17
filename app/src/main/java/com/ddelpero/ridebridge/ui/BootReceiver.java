package com.ddelpero.ridebridge.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.ddelpero.ridebridge.core.RideBridgeService;

public class BootReceiver extends BroadcastReceiver {
    
    private static final String TAG = "RideBridge";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "BOOT: Device boot completed");
            
            // Check if auto-start is enabled
            SharedPreferences prefs = context.getSharedPreferences("RideBridgePrefs", Context.MODE_PRIVATE);
            boolean autoStart = prefs.getBoolean("auto_start", false);
            
            if (autoStart) {
                Log.d(TAG, "BOOT: Auto-start enabled - starting RideBridgeService");
                Intent serviceIntent = new Intent(context, RideBridgeService.class);
                context.startService(serviceIntent);
            } else {
                Log.d(TAG, "BOOT: Auto-start disabled");
            }
        }
    }
}
