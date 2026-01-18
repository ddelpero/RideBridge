package com.ddelpero.ridebridge.core;

import android.os.Build;
import android.util.Log;

/**
 * Utility to detect if running on emulator
 */
public class EmulatorDetector {
    
    public static boolean isEmulator() {
        // Check multiple indicators
        boolean isEmulated = (Build.FINGERPRINT.startsWith("generic") ||
                Build.FINGERPRINT.startsWith("unknown") ||
                Build.MODEL.contains("google_sdk") ||
                Build.MODEL.contains("Emulator") ||
                Build.MODEL.contains("Android SDK") ||
                Build.MANUFACTURER.contains("Genymotion") ||
                (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")) ||
                "Qemu".equals(Build.HARDWARE) ||
                "goldfish".equals(Build.HARDWARE) ||
                Build.DEVICE.contains("nox") ||
                Build.DEVICE.contains("vbox"));
        
        Log.d("RideBridge", "EMULATOR_DETECTOR: fingerprint=" + Build.FINGERPRINT + 
            ", model=" + Build.MODEL + 
            ", hardware=" + Build.HARDWARE + 
            ", isEmulator=" + isEmulated);
        
        return isEmulated;
    }
}
