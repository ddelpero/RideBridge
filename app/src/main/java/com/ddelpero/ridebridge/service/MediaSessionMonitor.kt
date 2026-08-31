package com.ddelpero.ridebridge.service

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ddelpero.ridebridge.communication.Manager
import com.ddelpero.ridebridge.core.MediaManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Holds a strong reference to [MediaController.Callback]. Android stores these
 * weakly, so a local/anonymous callback is silently dropped.
 */
object MediaSessionMonitor {
    private const val TAG = "MediaSessionMonitor"
    private const val POSITION_RESET_MS = 1500L
    private const val METADATA_REPUSH_MS = 400L

    private var trackedController: MediaController? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val metadataRepush = Runnable { push() }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (shouldPushPlayback(state)) {
                push()
            }
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            if (!MediaDataHelper.hasEmbeddedArt(trackedController)) {
                Manager.rebindPreferredSession()
            }
            push()
            mainHandler.removeCallbacks(metadataRepush)
            mainHandler.postDelayed(metadataRepush, METADATA_REPUSH_MS)
        }

        override fun onSessionDestroyed() {
            detach()
            Manager.rebindPreferredSession()
        }
    }

    private fun shouldPushPlayback(state: PlaybackState?): Boolean {
        val last = MediaManager.currentState ?: return true
        val playing = state?.state == PlaybackState.STATE_PLAYING
        if (last.isPlaying != playing) return true
        val metadata = trackedController?.metadata
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
        if (title != last.track) return true
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (duration != last.duration) return true
        val pos = state?.position ?: 0L
        return pos + POSITION_RESET_MS < last.position
    }

    @Synchronized
    fun attach(controller: MediaController?) {
        if (controller == null) return
        if (trackedController === controller) {
            push()
            return
        }
        detachLocked()
        trackedController = controller
        try {
            controller.registerCallback(callback, mainHandler)
            Log.d(TAG, "Attached media session ${controller.packageName}")
        } catch (e: Exception) {
            Log.e(TAG, "registerCallback failed: ${e.message}")
        }
        push()
    }

    @Synchronized
    fun attachIfBetter(controller: MediaController?) {
        if (controller == null) return
        val current = trackedController
        if (current === controller) {
            push()
            return
        }
        if (current != null &&
            MediaSessionPicker.isAssistant(current) &&
            !MediaSessionPicker.isAssistant(controller)
        ) {
            attach(controller)
            return
        }
        if (current == null) {
            attach(controller)
        }
    }

    fun trackedPackage(): String? = trackedController?.packageName

    @Synchronized
    fun detach() {
        detachLocked()
    }

    private fun detachLocked() {
        mainHandler.removeCallbacks(metadataRepush)
        try {
            trackedController?.unregisterCallback(callback)
        } catch (_: Exception) {
        }
        trackedController = null
    }

    @Synchronized
    fun push() {
        val controller = trackedController ?: return
        val state = MediaDataHelper.extractState(controller)
        Manager.dispatchClientEvent(MediaManager.formatMediaJson(state))
        if (state.albumArt.isNullOrEmpty()) {
            val uri = MediaDataHelper.artUri(controller)
            val context = Manager.appContextOrNull()
            if (!uri.isNullOrEmpty() && context != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val encoded = MediaDataHelper.encodeArtFromUri(context, uri)
                    if (encoded.isNotEmpty()) {
                        Manager.dispatchClientEvent(
                            MediaManager.formatMediaJson(state.copy(albumArt = encoded))
                        )
                    } else {
                        Manager.rebindPreferredSession()
                    }
                }
            } else {
                Manager.rebindPreferredSession()
            }
        }
    }
}
