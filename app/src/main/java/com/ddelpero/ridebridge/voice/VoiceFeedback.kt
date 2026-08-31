package com.ddelpero.ridebridge.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

object VoiceFeedback {
    private const val TAG = "VoiceFeedback"
    private const val ATTRIBUTION_AUDIO = "audio"
    private val handler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var tone: ToneGenerator? = null
    private var audioContext: Context? = null

    fun init(context: Context) {
        if (audioContext == null) {
            audioContext = context.applicationContext.createAttributionContext(ATTRIBUTION_AUDIO)
            try {
                tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
            } catch (e: Exception) {
                Log.w(TAG, "ToneGenerator init failed: ${e.message}")
            }
        }
        if (tts != null) return
        tts = TextToSpeech(audioContext ?: context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale.US
                tts?.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
            } else {
                Log.w(TAG, "TTS init failed status=$status")
            }
        }
    }

    fun beepStart() = playTone(ToneGenerator.TONE_DTMF_A, 350)

    fun beepEnd() = playTone(ToneGenerator.TONE_DTMF_D, 300)

    fun beepError() = playTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 280)

    fun stopTone() {
        val run = Runnable { stopToneNow() }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            run.run()
        } else {
            handler.post(run)
        }
    }

    fun speakNothingFound() {
        handler.post {
            if (ttsReady) {
                tts?.speak("Nothing found.", TextToSpeech.QUEUE_FLUSH, null, "notfound")
            } else {
                Log.w(TAG, "TTS not ready; error beep instead")
                beepError()
            }
        }
    }

    private fun playTone(toneType: Int, durationMs: Int) {
        try {
            val gen = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 100).also { tone = it }
            gen.startTone(toneType, durationMs)
        } catch (e: Exception) {
            Log.w(TAG, "Beep failed: ${e.message}")
        }
    }

    private fun stopToneNow() {
        try {
            tone?.stopTone()
        } catch (e: Exception) {
            Log.w(TAG, "Tone stop failed: ${e.message}")
        }
    }
}
