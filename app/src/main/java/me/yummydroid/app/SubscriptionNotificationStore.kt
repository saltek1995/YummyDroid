package me.yummydroid.app

import android.content.Context
import androidx.core.content.edit
import java.util.Locale
import me.yummydroid.app.data.SiteNotification

class SubscriptionNotificationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isInitialized(): Boolean = prefs.getBoolean(KEY_INITIALIZED, false)

    fun markInitialized() {
        prefs.edit {
            putBoolean(KEY_INITIALIZED, true)
        }
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

    private fun seenIds(): Set<String> = prefs.getStringSet(KEY_SEEN_IDS, emptySet()).orEmpty()

    private fun seenEvents(): Set<String> = prefs.getStringSet(KEY_SEEN_EVENTS, emptySet()).orEmpty()

    fun eventKey(notification: SiteNotification): String {
        val animeKey = notification.objectId.takeIf { it > 0L }
            ?.let { "anime:$it" }
            ?: notification.clickUrl.animeIdFromNotificationUrl()?.let { "anime:$it" }
        val episodeKey = notification.episodeNumberFromNotificationText()
            ?.let { "episode:$it" }
        if (animeKey != null && episodeKey != null) {
            return "$animeKey|$episodeKey"
        }

        return listOf(notification.title, notification.text)
            .joinToString("|")
            .lowercase(Locale.ROOT)
            .replace(Regex("""\b(cvh|kodik|alloha|aksor|sibnet|hls|mp4|плеер|озвучка)\b"""), "")
            .replace(Regex("""[\s./|•:_-]+"""), " ")
            .trim()
    }

    private fun SiteNotification.episodeNumberFromNotificationText(): String? {
        val text = "$title $text".lowercase(Locale.ROOT).replace(',', '.')
        return listOf(
            Regex("""(?:сер(?:ия|ии|ию|ией)?|эпизод|episode|ep\.?)\s*#?\s*(\d+(?:\.\d+)?)"""),
            Regex("""#\s*(\d+(?:\.\d+)?)"""),
        ).firstNotNullOfOrNull { regex ->
            regex.find(text)?.groupValues?.getOrNull(1)
        }
    }

    private fun String.animeIdFromNotificationUrl(): Long? {
        return Regex("""-(\d+)(?:[/#?]|$)""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
    }

    private companion object {
        const val PREFS_NAME = "yummydroid_subscription_notifications"
        const val KEY_INITIALIZED = "initialized"
        const val KEY_SEEN_IDS = "seen_ids"
        const val KEY_SEEN_EVENTS = "seen_events"
        const val MAX_SEEN_ITEMS = 300
    }
}
