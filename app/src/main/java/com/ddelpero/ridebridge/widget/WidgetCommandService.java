package com.ddelpero.ridebridge.widget;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.ddelpero.ridebridge.core.RideBridgeService;

public class WidgetCommandService extends IntentService {

    private static final String TAG = "RideBridge";

    public WidgetCommandService() {
        super("WidgetCommandService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent == null) return;
        
        String command = intent.getStringExtra("command");
        Log.d(TAG, "WIDGET: WidgetCommandService received command: " + command);
        
        // Send the command to the RideBridgeService
        Intent serviceIntent = new Intent(this, RideBridgeService.class);
        serviceIntent.putExtra("widget_command", command);
        startService(serviceIntent);
    }
}
