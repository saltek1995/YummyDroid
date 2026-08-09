package me.yummydroid.app.data

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

const val DEFAULT_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND = 5
const val MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND = 1
const val MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND = 50
const val DOWNLOAD_SPEED_LIMIT_WARNING_THRESHOLD_MB_PER_SECOND = 10
const val MIN_INTERFACE_SCALE_PERCENT = 50
const val MAX_INTERFACE_SCALE_PERCENT = 200
const val DEFAULT_INTERFACE_SCALE_PERCENT = 100
private const val BYTES_PER_MEGABYTE = 1024L * 1024L

data class AppSettings(
    val defaultQuality: PreferredQuality = PreferredQuality.Auto,
    val decoderMode: PlayerDecoderMode = PlayerDecoderMode.Auto,
    val playerBufferPreset: PlayerBufferPreset = PlayerBufferPreset.Standard,
    val playerSpeed: PlayerSpeed = PlayerSpeed.Normal,
    val matchDisplayModeToVideo: Boolean = false,
    val skipOpeningsAndEndings: Boolean = true,
    val autoplayNextEpisode: Boolean = true,
    val autoMarkWatchingOnPlayback: Boolean = false,
    val autoMarkWatchedOnCompletedFinalEpisode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val autoCheckUpdates: Boolean = true,
    val downloadParallelism: Int = 1,
    val downloadSpeedLimitMegabytesPerSecond: Int = DEFAULT_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
    val allowMeteredDownloads: Boolean = false,
    val posterCardSize: PosterCardSize = PosterCardSize.Standard,
    val interfaceScale: InterfaceScale = InterfaceScale.Default,
    val contentLanguage: ContentLanguage = ContentLanguage.Russian,
    val siteDomains: List<String> = SiteDomainResolver.DEFAULT_SITE_DOMAINS,
    val savedBrowseFilters: BrowseFilters = BrowseFilters(),
) {
    val downloadSpeedLimitBytesPerSecond: Long
        get() = downloadSpeedLimitMegabytesPerSecond
            .coerceIn(MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND, MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND)
            .toLong() * BYTES_PER_MEGABYTE
}

enum class PreferredQuality(
    val title: String,
    val height: Int?,
) {
    Auto("Auto", null),
    P2160("2160p", 2160),
    P1440("1440p", 1440),
    P1080("1080p", 1080),
    P720("720p", 720),
    P576("576p", 576),
    P540("540p", 540),
    P480("480p", 480),
    P360("360p", 360),
    P240("240p", 240),
    P144("144p", 144);

    companion object {
        fun fromName(name: String): PreferredQuality? = entries.firstOrNull { it.name == name }
        fun fromHeight(height: Int?): PreferredQuality? = entries.firstOrNull { it.height == height }
    }
}

enum class PlayerDecoderMode(
    val title: String,
) {
    Auto("Auto"),
    Hardware("Hardware"),
    Software("Software");

    companion object {
        fun fromName(name: String): PlayerDecoderMode? = entries.firstOrNull { it.name == name }
    }
}

enum class PlayerBufferPreset(
    val title: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int,
    val prepareFallbackThresholdMs: Long,
    val switchFallbackThresholdMs: Long,
) {
    Compact(
        title = "Compact",
        minBufferMs = 15_000,
        maxBufferMs = 30_000,
        playbackBufferMs = 1_000,
        rebufferMs = 2_000,
        prepareFallbackThresholdMs = 6_000L,
        switchFallbackThresholdMs = 2_500L,
    ),
    Standard(
        title = "Standard",
        minBufferMs = 35_000,
        maxBufferMs = 70_000,
        playbackBufferMs = 1_500,
        rebufferMs = 3_000,
        prepareFallbackThresholdMs = 10_000L,
        switchFallbackThresholdMs = 3_000L,
    ),
    Large(
        title = "Large",
        minBufferMs = 70_000,
        maxBufferMs = 140_000,
        playbackBufferMs = 2_000,
        rebufferMs = 4_000,
        prepareFallbackThresholdMs = 16_000L,
        switchFallbackThresholdMs = 4_000L,
    ),
    Maximum(
        title = "Maximum",
        minBufferMs = 120_000,
        maxBufferMs = 240_000,
        playbackBufferMs = 2_500,
        rebufferMs = 5_000,
        prepareFallbackThresholdMs = 24_000L,
        switchFallbackThresholdMs = 5_000L,
    );

    companion object {
        fun fromName(name: String): PlayerBufferPreset? = entries.firstOrNull { it.name == name }
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

enum class PosterCardSize(
    val title: String,
    val minWidthDp: Int,
) {
    Compact("Compact", 148),
    Standard("Standard", 176),
    Large("Large", 212);

    companion object {
        fun fromName(name: String): PosterCardSize? = entries.firstOrNull { it.name == name }
    }
}

enum class ContentLanguage(
    val title: String,
    val apiCode: String,
) {
    Russian("Russian", "ru"),
    English("English", "en"),
    Ukrainian("Ukrainian", "uk");

    companion object {
        fun fromName(name: String): ContentLanguage? = entries.firstOrNull { it.name == name }
    }

    val locale: Locale
        get() = Locale.forLanguageTag(apiCode)
}

data class InterfaceScale(
    val percent: Int,
) {
    val title: String
        get() = "$percent%"

    val multiplier: Float
        get() = percent / 100f

    companion object {
        val Default = InterfaceScale(DEFAULT_INTERFACE_SCALE_PERCENT)

        fun fromPercent(percent: Int): InterfaceScale {
            return InterfaceScale(percent.coerceIn(MIN_INTERFACE_SCALE_PERCENT, MAX_INTERFACE_SCALE_PERCENT))
        }

        fun fromPersistedValue(value: Any?): InterfaceScale? {
            return when (value) {
                is Int -> fromPercent(value)
                is Long -> fromPercent(value.toInt())
                is String -> fromPersistedString(value)
                else -> null
            }
        }

        private fun fromPersistedString(value: String): InterfaceScale? {
            val trimmed = value.trim()
            val percent = trimmed
                .removePrefix("Percent")
                .removeSuffix("%")
                .toIntOrNull()
                ?: return null
            return fromPercent(percent)
        }
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
            playerBufferPreset = prefs.getString(KEY_PLAYER_BUFFER_PRESET, null)
                ?.let(PlayerBufferPreset::fromName)
                ?: PlayerBufferPreset.Standard,
            playerSpeed = prefs.getString(KEY_PLAYER_SPEED, null)
                ?.let(PlayerSpeed::fromName)
                ?: PlayerSpeed.Normal,
            matchDisplayModeToVideo = prefs.getBoolean(KEY_MATCH_DISPLAY_MODE_TO_VIDEO, false),
            skipOpeningsAndEndings = prefs.getBoolean(KEY_SKIP_OPENINGS_AND_ENDINGS, true),
            autoplayNextEpisode = prefs.getBoolean(KEY_AUTOPLAY_NEXT_EPISODE, true),
            autoMarkWatchingOnPlayback = prefs.getBoolean(KEY_AUTO_MARK_WATCHING_ON_PLAYBACK, false),
            autoMarkWatchedOnCompletedFinalEpisode =
                prefs.getBoolean(KEY_AUTO_MARK_WATCHED_ON_COMPLETED_FINAL_EPISODE, false),
            notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
            autoCheckUpdates = prefs.getBoolean(KEY_AUTO_CHECK_UPDATES, true),
            downloadParallelism = prefs.getInt(KEY_DOWNLOAD_PARALLELISM, 1).coerceIn(1, 4),
            downloadSpeedLimitMegabytesPerSecond = prefs.getInt(
                KEY_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
                DEFAULT_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
            ).coerceIn(MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND, MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND),
            allowMeteredDownloads = prefs.getBoolean(KEY_ALLOW_METERED_DOWNLOADS, false),
            posterCardSize = prefs.getString(KEY_POSTER_CARD_SIZE, null)
                ?.let(PosterCardSize::fromName)
                ?: PosterCardSize.Standard,
            interfaceScale = readInterfaceScale(),
            contentLanguage = prefs.getString(KEY_CONTENT_LANGUAGE, null)
                ?.let(ContentLanguage::fromName)
                ?: ContentLanguage.Russian,
            siteDomains = prefs.getString(KEY_SITE_DOMAINS, null)
                ?.lineSequence()
                ?.toList()
                ?.normalizedSiteBaseUrls()
                ?.ifEmpty { SiteDomainResolver.DEFAULT_SITE_DOMAINS }
                ?: SiteDomainResolver.DEFAULT_SITE_DOMAINS,
            savedBrowseFilters = prefs.getJsonOrNull<BrowseFilters>(KEY_BROWSE_FILTERS)
                ?: BrowseFilters(),
        ).normalized()
    }

    fun save(settings: AppSettings) {
        val normalizedSettings = settings.normalized()
        prefs.edit {
            putString(KEY_DEFAULT_QUALITY, normalizedSettings.defaultQuality.name)
            putString(KEY_DECODER_MODE, normalizedSettings.decoderMode.name)
            putString(KEY_PLAYER_BUFFER_PRESET, normalizedSettings.playerBufferPreset.name)
            putString(KEY_PLAYER_SPEED, normalizedSettings.playerSpeed.name)
            putBoolean(KEY_MATCH_DISPLAY_MODE_TO_VIDEO, normalizedSettings.matchDisplayModeToVideo)
            putBoolean(KEY_SKIP_OPENINGS_AND_ENDINGS, normalizedSettings.skipOpeningsAndEndings)
            putBoolean(KEY_AUTOPLAY_NEXT_EPISODE, normalizedSettings.autoplayNextEpisode)
            putBoolean(KEY_AUTO_MARK_WATCHING_ON_PLAYBACK, normalizedSettings.autoMarkWatchingOnPlayback)
            putBoolean(
                KEY_AUTO_MARK_WATCHED_ON_COMPLETED_FINAL_EPISODE,
                normalizedSettings.autoMarkWatchedOnCompletedFinalEpisode,
            )
            putBoolean(KEY_NOTIFICATIONS_ENABLED, normalizedSettings.notificationsEnabled)
            putBoolean(KEY_AUTO_CHECK_UPDATES, normalizedSettings.autoCheckUpdates)
            putInt(KEY_DOWNLOAD_PARALLELISM, normalizedSettings.downloadParallelism.coerceIn(1, 4))
            putInt(
                KEY_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
                normalizedSettings.downloadSpeedLimitMegabytesPerSecond.coerceIn(
                    MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
                    MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
                ),
            )
            putBoolean(KEY_ALLOW_METERED_DOWNLOADS, normalizedSettings.allowMeteredDownloads)
            remove(KEY_APP_THEME)
            putString(KEY_POSTER_CARD_SIZE, normalizedSettings.posterCardSize.name)
            putInt(KEY_INTERFACE_SCALE, normalizedSettings.interfaceScale.percent)
            putString(KEY_CONTENT_LANGUAGE, normalizedSettings.contentLanguage.name)
            putString(KEY_SITE_DOMAINS, normalizedSettings.siteDomains.joinToString("\n"))
            putString(KEY_BROWSE_FILTERS, normalizedSettings.savedBrowseFilters.encodeAppJson())
        }
    }

    fun readInterfaceScale(): InterfaceScale {
        return InterfaceScale.fromPersistedValue(prefs.all[KEY_INTERFACE_SCALE])
            ?: InterfaceScale.Default
    }

    private companion object {
        const val PREFS_NAME = "yummydroid_settings"
        const val KEY_DEFAULT_QUALITY = "default_quality"
        const val KEY_DECODER_MODE = "decoder_mode"
        const val KEY_PLAYER_BUFFER_PRESET = "player_buffer_preset"
        const val KEY_PLAYER_SPEED = "player_speed"
        const val KEY_MATCH_DISPLAY_MODE_TO_VIDEO = "match_display_mode_to_video"
        const val KEY_SKIP_OPENINGS_AND_ENDINGS = "skip_openings_and_endings"
        const val KEY_AUTOPLAY_NEXT_EPISODE = "autoplay_next_episode"
        const val KEY_AUTO_MARK_WATCHING_ON_PLAYBACK = "auto_mark_watching_on_playback"
        const val KEY_AUTO_MARK_WATCHED_ON_COMPLETED_FINAL_EPISODE = "auto_mark_watched_on_completed_final_episode"
        const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
        const val KEY_DOWNLOAD_PARALLELISM = "download_parallelism"
        const val KEY_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND = "download_speed_limit_mb_per_second"
        const val KEY_ALLOW_METERED_DOWNLOADS = "allow_metered_downloads"
        const val KEY_APP_THEME = "app_theme"
        const val KEY_POSTER_CARD_SIZE = "poster_card_size"
        const val KEY_INTERFACE_SCALE = "interface_scale"
        const val KEY_CONTENT_LANGUAGE = "content_language"
        const val KEY_SITE_DOMAINS = "site_domains"
        const val KEY_BROWSE_FILTERS = "browse_filters"
    }
}

fun AppSettings.normalized(): AppSettings {
    return copy(
        downloadParallelism = downloadParallelism.coerceIn(1, 4),
        downloadSpeedLimitMegabytesPerSecond = downloadSpeedLimitMegabytesPerSecond.coerceIn(
            MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
            MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
        ),
        interfaceScale = InterfaceScale.fromPercent(interfaceScale.percent),
        siteDomains = siteDomains.normalizedSiteBaseUrls()
            .ifEmpty { SiteDomainResolver.DEFAULT_SITE_DOMAINS },
    )
}
