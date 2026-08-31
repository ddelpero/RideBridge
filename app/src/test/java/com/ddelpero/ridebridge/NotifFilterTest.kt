package com.ddelpero.ridebridge

import com.ddelpero.ridebridge.service.NotifFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotifFilterTest {
    private val self = "com.ddelpero.ridebridge"

    private fun kind(
        pkg: String,
        category: String? = null,
        media: Boolean = false,
        summary: Boolean = false,
        importance: Int = 3,
        priority: Int = 0
    ) = NotifFilter.kind(pkg, self, category, media, summary, importance, priority)

    @Test
    fun skipsOwnPackage() {
        assertNull(kind(self, category = "msg"))
    }

    @Test
    fun skipsMediaSession() {
        assertNull(kind("com.whatsapp", category = "msg", media = true))
    }

    @Test
    fun skipsYoutubeMusic() {
        assertNull(kind("com.google.android.apps.youtube.music", category = "transport"))
    }

    @Test
    fun skipsGroupSummary() {
        assertNull(kind("com.whatsapp", category = "msg", summary = true))
    }

    @Test
    fun allowsWhatsAppAndMessages() {
        assertEquals(NotifFilter.KIND_MSG, kind("com.whatsapp"))
        assertEquals(NotifFilter.KIND_MSG, kind("com.google.android.apps.messaging"))
        assertEquals(NotifFilter.KIND_MSG, kind("com.unknown.app", category = "msg"))
    }

    @Test
    fun allowsCalls() {
        assertEquals(NotifFilter.KIND_CALL, kind("com.google.android.dialer"))
        assertEquals(NotifFilter.KIND_CALL, kind("com.unknown.app", category = "call"))
    }

    @Test
    fun allowsWirelessEmergencyAlerts() {
        assertEquals(
            NotifFilter.KIND_EMERGENCY,
            kind("com.google.android.cellbroadcastreceiver")
        )
    }

    @Test
    fun skipsRoutineWeatherButAllowsAlarm() {
        assertNull(kind("com.google.android.apps.weather", category = "status", priority = 0))
        assertEquals(
            NotifFilter.KIND_EMERGENCY,
            kind("com.google.android.apps.weather", category = "alarm", priority = 2)
        )
    }

    @Test
    fun skipsSilentUnknown() {
        assertNull(kind("com.random.app", importance = 1, priority = -2))
    }
}
