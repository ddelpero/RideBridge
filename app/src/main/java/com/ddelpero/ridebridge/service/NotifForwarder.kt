package com.ddelpero.ridebridge.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ddelpero.ridebridge.communication.Manager
import org.json.JSONObject
import java.util.Collections

object NotifForwarder {
    private const val TAG = "NotifForwarder"
    private const val MAX_CHARS = 80

    private val forwardedKeys = Collections.synchronizedSet(mutableSetOf<String>())
    @Volatile private var lastFingerprint: String? = null

    fun onPosted(listener: NotificationListenerService, sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val hasMedia = extras.containsKey(Notification.EXTRA_MEDIA_SESSION)
        val flags = sbn.notification.flags
        val isGroupSummary = flags and Notification.FLAG_GROUP_SUMMARY != 0
        val category = sbn.notification.category
        val importance = rankingImportance(listener, sbn)
        val kind = NotifFilter.kind(
            packageName = sbn.packageName.orEmpty(),
            selfPackage = listener.packageName,
            category = category,
            hasMediaSession = hasMedia,
            isGroupSummary = isGroupSummary,
            importance = importance,
            priority = sbn.notification.priority
        ) ?: return

        val title = extraText(extras, Notification.EXTRA_TITLE)
            .ifBlank { extraText(extras, Notification.EXTRA_CONVERSATION_TITLE) }
        val text = extraText(extras, Notification.EXTRA_BIG_TEXT)
            .ifBlank { extraText(extras, Notification.EXTRA_TEXT) }
            .ifBlank { extraText(extras, Notification.EXTRA_SUB_TEXT) }
        if (title.isBlank() && text.isBlank()) return

        val fingerprint = "${sbn.key}|$title|$text"
        if (fingerprint == lastFingerprint) return
        lastFingerprint = fingerprint

        val app = appLabel(listener, sbn.packageName)
        val ongoing = sbn.isOngoing || kind == NotifFilter.KIND_CALL
        val json = JSONObject()
            .put("type", "NOTIF")
            .put("action", "show")
            .put("key", sbn.key)
            .put("app", app)
            .put("title", clip(title))
            .put("text", clip(text))
            .put("category", kind)
            .put("ongoing", ongoing)
            .toString()
        forwardedKeys.add(sbn.key)
        Log.d(TAG, "show $kind ${sbn.packageName} '$title'")
        Manager.dispatchClientEvent(json)
    }

    fun onRemoved(sbn: StatusBarNotification?) {
        val key = sbn?.key ?: return
        if (!forwardedKeys.remove(key)) return
        if (lastFingerprint?.startsWith("$key|") == true) lastFingerprint = null
        val json = JSONObject()
            .put("type", "NOTIF")
            .put("action", "clear")
            .put("key", key)
            .toString()
        Log.d(TAG, "clear $key")
        Manager.dispatchClientEvent(json)
    }

    private fun rankingImportance(
        listener: NotificationListenerService,
        sbn: StatusBarNotification
    ): Int {
        return try {
            val ranking = NotificationListenerService.Ranking()
            if (listener.currentRanking.getRanking(sbn.key, ranking)) {
                ranking.importance
            } else {
                priorityAsImportance(sbn.notification.priority)
            }
        } catch (_: Exception) {
            priorityAsImportance(sbn.notification.priority)
        }
    }

    private fun priorityAsImportance(priority: Int): Int = when {
        priority >= Notification.PRIORITY_HIGH -> NotificationManager.IMPORTANCE_HIGH
        priority <= Notification.PRIORITY_MIN -> NotificationManager.IMPORTANCE_MIN
        else -> NotificationManager.IMPORTANCE_DEFAULT
    }

    private fun extraText(extras: android.os.Bundle, key: String): String {
        val value = extras.getCharSequence(key) ?: extras.getString(key) ?: return ""
        return value.toString().replace('\n', ' ').trim()
    }

    private fun appLabel(context: Context, packageName: String): String {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName.substringAfterLast('.')
        }
    }

    private fun clip(value: String): String {
        if (value.length <= MAX_CHARS) return value
        return value.take(MAX_CHARS - 1).trimEnd() + "…"
    }
}
