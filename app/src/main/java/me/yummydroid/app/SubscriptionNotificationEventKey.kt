package me.yummydroid.app

import java.util.Locale
import me.yummydroid.app.data.SiteNotification

internal fun subscriptionNotificationEventKey(notification: SiteNotification): String {
    val animeKey = notification.objectId.takeIf { it > 0L }
        ?.let { "anime:$it" }
        ?: notification.clickUrl.animeIdFromNotificationUrl()?.let { "anime:$it" }
    val episodeKey = notification.episodeNumberFromNotificationText()?.let { "episode:$it" }
    if (animeKey != null && episodeKey != null) return "$animeKey|$episodeKey"

    return listOf(notification.title, notification.text)
        .joinToString("|")
        .lowercase(Locale.ROOT)
        .replace(IGNORED_NOTIFICATION_SOURCE_PATTERN, "")
        .replace(NOTIFICATION_KEY_SEPARATOR_PATTERN, " ")
        .trim()
}

private fun SiteNotification.episodeNumberFromNotificationText(): String? {
    val searchableText = "$title $text".lowercase(Locale.ROOT).replace(',', '.')
    return EPISODE_PATTERNS.firstNotNullOfOrNull { regex ->
        regex.find(searchableText)?.groupValues?.getOrNull(1)
    }
}

private fun String.animeIdFromNotificationUrl(): Long? {
    return ANIME_ID_URL_PATTERN.find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
}

private const val RU_PLAYER_KEY = "\u043f\u043b\u0435\u0435\u0440"
private const val RU_VOICE_KEY = "\u043e\u0437\u0432\u0443\u0447\u043a\u0430"
private const val RU_EPISODE_WORD_PATTERN =
    "\u0441\u0435\u0440(?:\u0438\u044f|\u0438\u0438|\u0438\u044e|\u0438\u0435\u0439)?|\u044d\u043f\u0438\u0437\u043e\u0434"

private val IGNORED_NOTIFICATION_SOURCE_PATTERN =
    Regex(
        """(?<![\p{L}\p{N}_])(?:cvh|kodik|alloha|aksor|sibnet|hls|mp4|$RU_PLAYER_KEY|$RU_VOICE_KEY)(?![\p{L}\p{N}_])""",
    )
private val NOTIFICATION_KEY_SEPARATOR_PATTERN = Regex("""[\s./|\u2022:_-]+""")
private val ANIME_ID_URL_PATTERN = Regex("""-(\d+)(?:[/#?]|$)""")
private val EPISODE_PATTERNS = listOf(
    Regex("(?:$RU_EPISODE_WORD_PATTERN|episode|ep\\.?)\\s*#?\\s*(\\d+(?:\\.\\d+)?)"),
    Regex("""#\s*(\d+(?:\.\d+)?)"""),
)
