package com.ddelpero.ridebridge.service

import android.media.session.MediaController
import android.media.session.PlaybackState

object MediaSessionPicker {
    private val ASSISTANT_PACKAGES = setOf(
        "com.google.android.googlequicksearchbox",
        "com.google.android.googlequicksearchbox:search",
        "com.google.android.as",
        "com.google.android.apps.googleassistant"
    )

    fun pick(controllers: List<MediaController>): MediaController? {
        if (controllers.isEmpty()) return null
        val usable = controllers.filter { !isAssistant(it) }
        val pool = usable.ifEmpty { controllers }
        pool.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }?.let { return it }
        return pool.maxByOrNull { it.playbackState?.lastPositionUpdateTime ?: 0L }
            ?: controllers.firstOrNull()
    }

    fun isAssistant(controller: MediaController): Boolean {
        val pkg = controller.packageName
        return pkg in ASSISTANT_PACKAGES || pkg.startsWith("com.google.android.googlequicksearchbox")
    }
}
