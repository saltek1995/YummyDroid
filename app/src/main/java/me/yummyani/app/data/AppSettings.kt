package me.yummyani.app.data

import android.content.Context

data class AppSettings(
    val defaultQuality: PreferredQuality = PreferredQuality.Auto,
    val decoderMode: PlayerDecoderMode = PlayerDecoderMode.Auto,
    val autoplayNextEpisode: Boolean = true,
    val autoMarkWatchingOnPlayback: Boolean = false,
    val autoMarkWatchedOnCompletedFinalEpisode: Boolean = false,
    val siteDomains: List<String> = SiteDomainResolver.DEFAULT_SITE_DOMAINS,
)

enum class PreferredQuality(
    val title: String,
    val height: Int?,
) {
    Auto("Авто", null),
    P1080("1080p", 1080),
    P720("720p", 720),
    P480("480p", 480),
    P360("360p", 360),
    P240("240p", 240),
    P144("144p", 144);

    companion object {
        fun fromName(name: String): PreferredQuality? = entries.firstOrNull { it.name == name }
    }
}

enum class PlayerDecoderMode(
    val title: String,
) {
    Auto("Авто"),
    Hardware("Аппаратный"),
    Software("Программный");

    companion object {
        fun fromName(name: String): PlayerDecoderMode? = entries.firstOrNull { it.name == name }
    }
}

class AppSettingsStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): AppSettings {
        return AppSettings(
            defaultQuality = prefs.getString(KEY_DEFAULT_QUALITY, null)
                ?.let(PreferredQuality::fromName)
                ?: PreferredQuality.Auto,
            decoderMode = prefs.getString(KEY_DECODER_MODE, null)
                ?.let(PlayerDecoderMode::fromName)
                ?: PlayerDecoderMode.Auto,
            autoplayNextEpisode = prefs.getBoolean(KEY_AUTOPLAY_NEXT_EPISODE, true),
            autoMarkWatchingOnPlayback = prefs.getBoolean(KEY_AUTO_MARK_WATCHING_ON_PLAYBACK, false),
            autoMarkWatchedOnCompletedFinalEpisode =
                prefs.getBoolean(KEY_AUTO_MARK_WATCHED_ON_COMPLETED_FINAL_EPISODE, false),
            siteDomains = prefs.getString(KEY_SITE_DOMAINS, null)
                ?.lineSequence()
                ?.toList()
                ?.normalizedSiteBaseUrls()
                ?.ifEmpty { SiteDomainResolver.DEFAULT_SITE_DOMAINS }
                ?: SiteDomainResolver.DEFAULT_SITE_DOMAINS,
        ).normalized()
    }

    fun save(settings: AppSettings) {
        val normalizedSettings = settings.normalized()
        prefs.edit()
            .putString(KEY_DEFAULT_QUALITY, normalizedSettings.defaultQuality.name)
            .putString(KEY_DECODER_MODE, normalizedSettings.decoderMode.name)
            .putBoolean(KEY_AUTOPLAY_NEXT_EPISODE, normalizedSettings.autoplayNextEpisode)
            .putBoolean(KEY_AUTO_MARK_WATCHING_ON_PLAYBACK, normalizedSettings.autoMarkWatchingOnPlayback)
            .putBoolean(
                KEY_AUTO_MARK_WATCHED_ON_COMPLETED_FINAL_EPISODE,
                normalizedSettings.autoMarkWatchedOnCompletedFinalEpisode,
            )
            .putString(KEY_SITE_DOMAINS, normalizedSettings.siteDomains.joinToString("\n"))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "yummyani_settings"
        const val KEY_DEFAULT_QUALITY = "default_quality"
        const val KEY_DECODER_MODE = "decoder_mode"
        const val KEY_AUTOPLAY_NEXT_EPISODE = "autoplay_next_episode"
        const val KEY_AUTO_MARK_WATCHING_ON_PLAYBACK = "auto_mark_watching_on_playback"
        const val KEY_AUTO_MARK_WATCHED_ON_COMPLETED_FINAL_EPISODE = "auto_mark_watched_on_completed_final_episode"
        const val KEY_SITE_DOMAINS = "site_domains"
    }
}

internal fun AppSettings.normalized(): AppSettings {
    return copy(
        siteDomains = siteDomains.normalizedSiteBaseUrls()
            .ifEmpty { SiteDomainResolver.DEFAULT_SITE_DOMAINS },
    )
}
