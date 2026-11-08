package com.yesmin.ai.automation

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Only works after the user grants Notification access in Android settings.
 * Notifications are read on demand from the active list; nothing is stored or uploaded
 * unless the user asks Yesmin to summarize them.
 */
class YesminNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        super.onListenerDisconnected()
    }

    /** Short text lines for current notifications, newest first. */
    fun snapshot(limit: Int = 15): List<String> =
        runCatching { activeNotifications.toList() }.getOrDefault(emptyList())
            .filter { it.packageName != packageName }
            .sortedByDescending { it.postTime }
            .take(limit)
            .map { describe(it) }

    private fun describe(sbn: StatusBarNotification): String {
        val ex = sbn.notification.extras
        val title = ex.getCharSequence("android.title")?.toString().orEmpty()
        val text = ex.getCharSequence("android.text")?.toString().orEmpty()
        val app = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        return "$app: $title - $text".trim()
    }

    companion object {
        @Volatile var instance: YesminNotificationListener? = null
        val isConnected get() = instance != null
    }
}
