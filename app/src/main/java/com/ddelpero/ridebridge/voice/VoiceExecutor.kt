package com.ddelpero.ridebridge.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ddelpero.ridebridge.communication.Manager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object VoiceExecutor {
    private const val TAG = "VoiceExecutor"

    fun execute(context: Context, intent: VoiceIntent) {
        Log.d(TAG, "execute $intent")
        when (intent) {
            VoiceIntent.Play -> Manager.withMediaControls { it.play() }
            VoiceIntent.Pause -> Manager.withMediaControls { it.pause() }
            VoiceIntent.Next -> Manager.withMediaControls { it.skipToNext() }
            VoiceIntent.Previous -> Manager.withMediaControls { it.skipToPrevious() }
            is VoiceIntent.PlaySearch -> VoiceAssist.playQuery(context, intent.query)
            is VoiceIntent.Call -> placeCall(context, intent.nameOrNumber)
            is VoiceIntent.Navigate -> geocodeAndSend(context, intent.query)
            VoiceIntent.Unknown -> Log.w(TAG, "Unrecognized voice command")
        }
    }

    @SuppressLint("MissingPermission")
    private fun placeCall(context: Context, nameOrNumber: String) {
        val number = resolveNumber(context, nameOrNumber)
        if (number.isNullOrBlank()) {
            Log.w(TAG, "No phone number for '$nameOrNumber'")
            VoiceFeedback.speakNothingFound()
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "CALL_PHONE not granted")
            return
        }
        try {
            val telecom = context.getSystemService(TelecomManager::class.java)
            telecom.placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), Bundle())
            Log.d(TAG, "Placing call to $number")
        } catch (e: Exception) {
            Log.e(TAG, "placeCall failed: ${e.message}")
        }
    }

    private fun resolveNumber(context: Context, nameOrNumber: String): String? {
        val digits = nameOrNumber.filter { it.isDigit() || it == '+' }
        val hasLetters = nameOrNumber.any { it.isLetter() }
        if (!hasLetters && digits.length >= 3) return digits
        return lookupContact(context, nameOrNumber) ?: digits.takeIf { it.length >= 3 }
    }

    private fun lookupContact(context: Context, name: String): String? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "READ_CONTACTS not granted")
            return null
        }
        val query = name.trim()
        if (query.isEmpty()) return null
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
                arrayOf("%$query%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numIdx >= 0 && cursor.moveToFirst()) cursor.getString(numIdx) else null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Contact lookup failed: ${e.message}")
            null
        }
    }

    private fun geocodeAndSend(context: Context, query: String) {
        if (query.equals("gas station", ignoreCase = true)) {
            Log.d(TAG, "NAV nearby fuel (tablet)")
            Manager.dispatchClientEvent(
                JSONObject().put("type", "NAV").put("nearby", "fuel").toString()
            )
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val hit = PlaceSearch.find(context, query)
            if (hit == null) {
                Log.w(TAG, "No geocode results for '$query'")
                VoiceFeedback.speakNothingFound()
                return@launch
            }
            val json = JSONObject()
                .put("type", "NAV")
                .put("label", hit.label)
                .put("lat", hit.lat)
                .put("lng", hit.lng)
            Log.d(TAG, "NAV ${hit.label} ${hit.lat},${hit.lng}")
            Manager.dispatchClientEvent(json.toString())
        }
    }
}
