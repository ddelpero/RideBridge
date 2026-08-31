package com.ddelpero.ridebridge.voice

import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager

class VoiceAssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        VoiceAssist.dismissVoiceNotification(this)
        val query = intent.getStringExtra(EXTRA_QUERY).orEmpty()
        val phrase = VoiceAssist.assistantPhrase(query)
        if (!launchGooglePlay(phrase)) {
            Log.w(TAG, "Google play intent unresolved for '$phrase'")
        }
        finish()
    }

    private fun launchGooglePlay(phrase: String): Boolean {
        if (phrase.isBlank()) return false
        val intents = listOf(
            Intent(Intent.ACTION_WEB_SEARCH).apply {
                setPackage(GOOGLE)
                putExtra(SearchManager.QUERY, phrase)
            },
            Intent(GOOGLE_SEARCH).apply {
                setPackage(GOOGLE)
                putExtra(SearchManager.QUERY, phrase)
                putExtra("query", phrase)
            }
        )
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            if (intent.resolveActivity(packageManager) == null) continue
            try {
                Log.d(TAG, "Starting ${intent.action} '$phrase'")
                startActivity(intent)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "${intent.action} failed: ${e.message}")
            }
        }
        return false
    }

    companion object {
        private const val TAG = "VoiceAssistActivity"
        const val EXTRA_QUERY = "query"
        private const val GOOGLE = "com.google.android.googlequicksearchbox"
        private const val GOOGLE_SEARCH = "com.google.android.googlequicksearchbox.GOOGLE_SEARCH"

        fun intent(context: Context, query: String): Intent {
            return Intent(context, VoiceAssistActivity::class.java).putExtra(EXTRA_QUERY, query)
        }
    }
}
