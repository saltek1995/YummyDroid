package me.yummydroid.app.data

import android.content.Context

data class AppSettings(
    val defaultQuality: PreferredQuality = PreferredQuality.Auto,
    val decoderMode: PlayerDecoderMode = PlayerDecoderMode.Auto,
    val playerSpeed: PlayerSpeed = PlayerSpeed.Normal,
    val videoScaleMode: VideoScaleMode = VideoScaleMode.Fit,
    val autoplayNextEpisode: Boolean = true,
    val autoMarkWatchingOnPlayback: Boolean = false,
    val autoMarkWatchedOnCompletedFinalEpisode: Boolean = false,
    val appTheme: AppTheme = AppTheme.Yummy,
    val posterCardSize: PosterCardSize = PosterCardSize.Standard,
    val contentLanguage: ContentLanguage = ContentLanguage.Russian,
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

enum class PlayerSpeed(
    val title: String,
    val value: Float,
) {
    X075("0.75x", 0.75f),
    Normal("1x", 1f),
    X125("1.25x", 1.25f),
    X15("1.5x", 1.5f),
    X175("1.75x", 1.75f),
    X2("2x", 2f);

    companion object {
        fun fromName(name: String): PlayerSpeed? = entries.firstOrNull { it.name == name }
    }
}

enum class VideoScaleMode(
    val title: String,
) {
    Fit("По размеру"),
    Fill("Заполнить"),
    Zoom("Увеличить");

    companion object {
        fun fromName(name: String): VideoScaleMode? = entries.firstOrNull { it.name == name }
    }
}

enum class AppTheme(
    val title: String,
) {
    Yummy("Yummy"),
    Graphite("Графит"),
    Ocean("Океан"),
    Sakura("Сакура"),
    Mint("Мята");

    companion object {
        fun fromName(name: String): AppTheme? = entries.firstOrNull { it.name == name }
    }
}

enum class PosterCardSize(
    val title: String,
    val minWidthDp: Int,
) {
    Compact("Компактные", 148),
    Standard("Стандартные", 176),
    Large("Крупные", 212);

    companion object {
        fun fromName(name: String): PosterCardSize? = entries.firstOrNull { it.name == name }
    }
}

enum class ContentLanguage(
    val title: String,
    val apiCode: String,
) {
    Russian("Русский", "ru"),
    English("English", "en"),
    Ukrainian("Українська", "uk");

    companion object {
        fun fromName(name: String): ContentLanguage? = entries.firstOrNull { it.name == name }
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
            playerSpeed = prefs.getString(KEY_PLAYER_SPEED, null)
                ?.let(PlayerSpeed::fromName)
                ?: PlayerSpeed.Normal,
            videoScaleMode = prefs.getString(KEY_VIDEO_SCALE_MODE, null)
                ?.let(VideoScaleMode::fromName)
                ?: VideoScaleMode.Fit,
            autoplayNextEpisode = prefs.getBoolean(KEY_AUTOPLAY_NEXT_EPISODE, true),
            autoMarkWatchingOnPlayback = prefs.getBoolean(KEY_AUTO_MARK_WATCHING_ON_PLAYBACK, false),
            autoMarkWatchedOnCompletedFinalEpisode =
                prefs.getBoolean(KEY_AUTO_MARK_WATCHED_ON_COMPLETED_FINAL_EPISODE, false),
            appTheme = prefs.getString(KEY_APP_THEME, null)
                ?.let(AppTheme::fromName)
                ?: AppTheme.Yummy,
            posterCardSize = prefs.getString(KEY_POSTER_CARD_SIZE, null)
                ?.let(PosterCardSize::fromName)
                ?: PosterCardSize.Standard,
            contentLanguage = prefs.getString(KEY_CONTENT_LANGUAGE, null)
                ?.let(ContentLanguage::fromName)
                ?: ContentLanguage.Russian,
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
            .putString(KEY_PLAYER_SPEED, normalizedSettings.playerSpeed.name)
            .putString(KEY_VIDEO_SCALE_MODE, normalizedSettings.videoScaleMode.name)
            .putBoolean(KEY_AUTOPLAY_NEXT_EPISODE, normalizedSettings.autoplayNextEpisode)
            .putBoolean(KEY_AUTO_MARK_WATCHING_ON_PLAYBACK, normalizedSettings.autoMarkWatchingOnPlayback)
            .putBoolean(
                KEY_AUTO_MARK_WATCHED_ON_COMPLETED_FINAL_EPISODE,
                normalizedSettings.autoMarkWatchedOnCompletedFinalEpisode,
            )
            .putString(KEY_APP_THEME, normalizedSettings.appTheme.name)
            .putString(KEY_POSTER_CARD_SIZE, normalizedSettings.posterCardSize.name)
            .putString(KEY_CONTENT_LANGUAGE, normalizedSettings.contentLanguage.name)
            .putString(KEY_SITE_DOMAINS, normalizedSettings.siteDomains.joinToString("\n"))
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "yummydroid_settings"
        const val KEY_DEFAULT_QUALITY = "default_quality"
        const val KEY_DECODER_MODE = "decoder_mode"
        const val KEY_PLAYER_SPEED = "player_speed"
        const val KEY_VIDEO_SCALE_MODE = "video_scale_mode"
        const val KEY_AUTOPLAY_NEXT_EPISODE = "autoplay_next_episode"
        const val KEY_AUTO_MARK_WATCHING_ON_PLAYBACK = "auto_mark_watching_on_playback"
        const val KEY_AUTO_MARK_WATCHED_ON_COMPLETED_FINAL_EPISODE = "auto_mark_watched_on_completed_final_episode"
        const val KEY_APP_THEME = "app_theme"
        const val KEY_POSTER_CARD_SIZE = "poster_card_size"
        const val KEY_CONTENT_LANGUAGE = "content_language"
        const val KEY_SITE_DOMAINS = "site_domains"
    }
}

internal fun AppSettings.normalized(): AppSettings {
    return copy(
        siteDomains = siteDomains.normalizedSiteBaseUrls()
            .ifEmpty { SiteDomainResolver.DEFAULT_SITE_DOMAINS },
    )
}
