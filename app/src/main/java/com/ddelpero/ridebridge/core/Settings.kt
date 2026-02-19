package com.ddelpero.ridebridge.core

import android.content.Context
import android.util.Log
import androidx.core.content.edit // Necessary for the clean edit block

object Settings {
    private const val PREFS_NAME = "RideBridgePrefs"
    private const val KEY_IS_TABLET = "is_tablet"
    private const val KEY_IS_AUTO_START = "is_auto_start"
    private const val KEY_IS_EMULATOR = "is_emulator"

    fun saveRole(context: Context, isTablet: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_IS_TABLET, isTablet)
        }
        Log.d("RideBridge", "Role Saved: " + isTablet)
    }

    fun saveAutoStart(context: Context, isAutoStart: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_IS_AUTO_START, isAutoStart)
        }
        Log.d("RideBridge", "AutoStart Saved: " + isAutoStart)
    }

    fun saveEmulator(context: Context, isEmulator: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            putBoolean(KEY_IS_EMULATOR, isEmulator)
        }
        Log.d("RideBridge", "Emulator Saved: " + isEmulator)
    }

    // Retrieve the setting
    fun isTablet(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isTablet = prefs.getBoolean(KEY_IS_TABLET, false)
        Log.d("RideBridge", "Role loading: " + isTablet)
        return isTablet
    }

    fun isEmulator(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEmulator = prefs.getBoolean(KEY_IS_EMULATOR, true)
        Log.d("RideBridge", "Emulator loading: " + isEmulator)
        return isEmulator
    }

    fun isAutoStart(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isAutoStart = prefs.getBoolean(KEY_IS_AUTO_START, false)
        Log.d("RideBridge", "Role loading: " + isAutoStart)
        return isAutoStart
    }
}