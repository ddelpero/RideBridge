package com.ddelpero.ridebridge.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.ddelpero.ridebridge.communication.Manager
import org.json.JSONObject

/**
 * Headless speech session owned by [com.ddelpero.ridebridge.service.RideBridgeForegroundService].
 * Pause music, beep on A2DP, then SCO for the mic. Does not start its own FGS.
 */
class VoiceSession(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var recognizer: SpeechRecognizer? = null
    private var routedDevice = false
    private var lastStartElapsed = 0L
    private var stopping = false
    private var resumeAfterListen = false
    private var readyForSpeech = false
    private val readyWatchdog = Runnable {
        if (stopping || readyForSpeech || recognizer == null) return@Runnable
        Log.w(TAG, "Recognizer never ready; aborting listen")
        stopListening(error = true)
    }

    val isListening: Boolean
        get() = recognizer != null

    fun toggle() {
        if (recognizer != null || stopping) {
            stopListening(error = false)
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastStartElapsed < DEBOUNCE_MS) {
            Log.d(TAG, "Ignoring debounced voice start")
            return
        }
        lastStartElapsed = now
        beginListen()
    }

    fun cancel() {
        if (recognizer != null || stopping) {
            stopListening(error = false)
        }
    }

    fun release() {
        stopListening(error = false, silent = true)
    }

    private fun beginListen() {
        stopping = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "RECORD_AUDIO not granted")
            VoiceFeedback.beepError()
            publishIdle()
            return
        }
        VoiceFeedback.beepStart()
        handler.postDelayed({
            if (stopping) return@postDelayed
            VoiceFeedback.stopTone()
            pauseIfPlaying()
            startRecognizerOnSco()
        }, BEEP_MS + 80L)
    }

    private fun startRecognizerOnSco() {
        routeHeadset()
        val created = createRecognizer()
        if (created == null) {
            Log.e(TAG, "SpeechRecognizer unavailable")
            unrouteHeadset()
            VoiceFeedback.beepError()
            maybeResume(null)
            Manager.forceRebindMedia()
            publishIdle()
            return
        }
        recognizer = created
        created.setRecognitionListener(listener)
        readyForSpeech = false
        try {
            created.startListening(recognizeIntent())
            Log.d(TAG, "Listening")
            handler.removeCallbacks(readyWatchdog)
            handler.postDelayed(readyWatchdog, READY_WATCHDOG_MS)
        } catch (e: Exception) {
            Log.e(TAG, "startListening failed: ${e.message}")
            tearDownRecognizer()
            unrouteHeadset()
            VoiceFeedback.beepError()
            maybeResume(null)
            Manager.forceRebindMedia()
            publishIdle()
        }
    }

    private fun pauseIfPlaying() {
        resumeAfterListen = false
        Manager.withController { controller ->
            val playing = controller.playbackState?.state == PlaybackState.STATE_PLAYING
            if (playing) {
                controller.transportControls.pause()
                resumeAfterListen = true
                Log.d(TAG, "Paused media for voice")
            }
        }
    }

    private fun maybeResume(intent: VoiceIntent?) {
        if (!resumeAfterListen) return
        if (intent is VoiceIntent.PlaySearch || intent is VoiceIntent.Pause) {
            resumeAfterListen = false
            return
        }
        resumeAfterListen = false
        Manager.withMediaControls { it.play() }
        Log.d(TAG, "Resumed media after voice")
    }

    private fun createRecognizer(): SpeechRecognizer? {
        return when {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            SpeechRecognizer.isRecognitionAvailable(context) ->
                SpeechRecognizer.createSpeechRecognizer(context)
            else -> null
        }
    }

    private fun recognizeIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    @SuppressLint("MissingPermission")
    private fun routeHeadset() {
        val am = audioManager ?: return
        try {
            val bt = am.availableCommunicationDevices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (bt != null) {
                routedDevice = am.setCommunicationDevice(bt)
                Log.d(TAG, "Communication device set type=${bt.type} ok=$routedDevice")
                return
            }
            @Suppress("DEPRECATION")
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            am.startBluetoothSco()
            @Suppress("DEPRECATION")
            am.isBluetoothScoOn = true
            routedDevice = true
        } catch (e: Exception) {
            Log.w(TAG, "Headset route failed: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun unrouteHeadset() {
        val am = audioManager ?: return
        try {
            am.clearCommunicationDevice()
            am.isBluetoothScoOn = false
            am.stopBluetoothSco()
            am.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(TAG, "Headset unroute failed: ${e.message}")
        }
        routedDevice = false
    }

    private fun tearDownRecognizer() {
        handler.removeCallbacks(readyWatchdog)
        readyForSpeech = false
        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private fun stopListening(error: Boolean, heard: String? = null, silent: Boolean = false) {
        if (stopping && heard == null) return
        stopping = true
        tearDownRecognizer()
        unrouteHeadset()
        val parsed = if (!heard.isNullOrBlank()) {
            VoiceCommandParser.parse(heard)
        } else {
            null
        }
        if (parsed != null) {
            VoiceExecutor.execute(context, parsed)
        }
        maybeResume(parsed)
        Manager.forceRebindMedia()
        publishIdle()
        stopping = false
        if (!silent) {
            if (error) VoiceFeedback.beepError() else VoiceFeedback.beepEnd()
        }
    }

    private fun publishListening() {
        Manager.dispatchClientEvent(
            JSONObject().put("type", "VOICE").put("state", "listening").toString()
        )
    }

    private fun publishIdle() {
        Manager.dispatchClientEvent(
            JSONObject().put("type", "VOICE").put("state", "idle").toString()
        )
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(TAG, "Ready for speech")
            readyForSpeech = true
            handler.removeCallbacks(readyWatchdog)
            publishListening()
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(TAG, "End of speech")
        }

        override fun onError(error: Int) {
            Log.w(TAG, "Speech error $error")
            if (stopping) return
            stopListening(error = true)
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            Log.d(TAG, "Heard: $text")
            if (stopping) return
            stopListening(error = false, heard = text)
        }

        override fun onPartialResults(partialResults: Bundle?) {}

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    companion object {
        private const val TAG = "VoiceSession"
        private const val DEBOUNCE_MS = 400L
        private const val BEEP_MS = 350L
        private const val READY_WATCHDOG_MS = 4000L
    }
}
