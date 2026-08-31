package com.ddelpero.ridebridge.service

/**
 * Pure allow-list for which phone notifications RideBridge forwards to the tablet.
 * No Android types so it can be unit-tested.
 */
object NotifFilter {
    const val KIND_MSG = "msg"
    const val KIND_CALL = "call"
    const val KIND_EMERGENCY = "emergency"

    const val PRIORITY_MAX = 2

    private val MESSENGERS = setOf(
        "com.google.android.apps.messaging",
        "com.android.mms",
        "com.samsung.android.messaging",
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thoughtcrime.securesms",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.verizon.messaging.vzmsgs",
        "com.textra"
    )

    private val DIALERS = setOf(
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.incallui",
        "com.android.incallui",
        "com.android.server.telecom",
        "com.google.android.apps.googlevoice"
    )

    private val EMERGENCY_PACKAGES = setOf(
        "com.google.android.cellbroadcastreceiver",
        "com.android.cellbroadcastreceiver",
        "com.google.android.apps.safetyhub"
    )

    private val WEATHER_PACKAGES = setOf(
        "com.google.android.apps.weather",
        "com.weather.Weather",
        "com.accuweather.android"
    )

    private val SKIP_PACKAGES = setOf(
        "com.google.android.apps.youtube.music",
        "com.google.android.youtube",
        "com.spotify.music",
        "com.google.android.apps.maps",
        "com.waze"
    )

    fun kind(
        packageName: String,
        selfPackage: String,
        category: String?,
        hasMediaSession: Boolean,
        isGroupSummary: Boolean,
        importance: Int,
        priority: Int
    ): String? {
        val pkg = packageName.trim()
        if (pkg.isEmpty() || pkg == selfPackage) return null
        if (hasMediaSession) return null
        if (isGroupSummary) return null
        if (pkg in SKIP_PACKAGES) return null
        when (category) {
            "transport", "navigation", "service", "progress", "recommendation" -> return null
        }

        if (pkg in EMERGENCY_PACKAGES) return KIND_EMERGENCY
        if (pkg in WEATHER_PACKAGES && (
                category == "alarm" ||
                    priority >= PRIORITY_MAX ||
                    importance >= 4
            )
        ) {
            return KIND_EMERGENCY
        }

        if (category == "call" || pkg in DIALERS) return KIND_CALL
        if (category == "msg" || pkg in MESSENGERS) return KIND_MSG
        return null
    }
}
