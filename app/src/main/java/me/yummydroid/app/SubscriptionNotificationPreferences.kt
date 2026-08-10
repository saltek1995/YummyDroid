package me.yummydroid.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import me.yummydroid.app.data.SiteNotification

class SubscriptionNotificationStore internal constructor(
    private val prefs: SharedPreferences,
    private val currentTimeMs: () -> Long,
) {
    constructor(context: Context) : this(
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        currentTimeMs = System::currentTimeMillis,
    )

    fun isInitialized(): Boolean = prefs.getBoolean(KEY_INITIALIZED, false)

    fun markInitialized() {
        prefs.edit { putBoolean(KEY_INITIALIZED, true) }
    }

    fun shouldRunCheck(minSpacingMs: Long): Boolean {
        val lastCheckAt = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        val now = currentTimeMs()
        return lastCheckAt <= 0L || now < lastCheckAt || now - lastCheckAt >= minSpacingMs
    }

    fun markCheckRun() {
        prefs.edit { putLong(KEY_LAST_CHECK_AT, currentTimeMs()) }
    }

    fun isSeen(notification: SiteNotification): Boolean {
        return notification.id.toString() in seenIds() || eventKey(notification) in seenEvents()
    }

    fun markSeen(notifications: List<SiteNotification>) {
        if (notifications.isEmpty()) return
        val updatedIds = (seenIds().toList() + notifications.map { it.id.toString() })
            .takeLast(MAX_SEEN_ITEMS)
            .toSet()
        val updatedEvents = (seenEvents().toList() + notifications.map(::eventKey))
            .takeLast(MAX_SEEN_ITEMS)
            .toSet()
        prefs.edit {
            putStringSet(KEY_SEEN_IDS, updatedIds)
            putStringSet(KEY_SEEN_EVENTS, updatedEvents)
            putBoolean(KEY_INITIALIZED, true)
        }
    }

    internal fun saveUnreadShadeItems(notifications: List<SiteNotification>) {
        val json = notifications.unreadNotificationShadeItemsJson(MAX_STORED_UNREAD_ITEMS)
        prefs.edit {
            if (json == null) remove(KEY_UNREAD_SHADE_ITEMS) else putString(KEY_UNREAD_SHADE_ITEMS, json)
        }
    }

    internal fun clearUnreadShadeItems() {
        prefs.edit { remove(KEY_UNREAD_SHADE_ITEMS) }
    }

    internal fun unreadShadeItems(): List<NotificationShadeItem> {
        return decodeNotificationShadeItems(prefs.getString(KEY_UNREAD_SHADE_ITEMS, null))
    }

    fun eventKey(notification: SiteNotification): String {
        return subscriptionNotificationEventKey(notification)
    }

    private fun seenIds(): Set<String> = prefs.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty()

    private fun seenEvents(): Set<String> = prefs.getStringSet(KEY_SEEN_EVENTS, emptySet()).orEmpty()

    private companion object {
        const val PREFS_NAME = "yummydroid_subscription_notifications"
        const val KEY_INITIALIZED = "initialized"
        const val KEY_SEEN_IDS = "seen_ids"
        const val KEY_SEEN_EVENTS = "seen_events"
        const val KEY_LAST_CHECK_AT = "last_check_at"
        const val KEY_UNREAD_SHADE_ITEMS = "unread_shade_items"
        const val MAX_SEEN_ITEMS = 300
        const val MAX_STORED_UNREAD_ITEMS = 20
    }
}
