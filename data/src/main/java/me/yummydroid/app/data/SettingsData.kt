package me.yummydroid.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import java.io.File
import java.util.Locale
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// AppSettingsModel
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

// AppSettingsNormalization
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

// AppSettingsPreferences
internal interface AppSettingsPreferences {
    val all: Map<String, *>

    fun getString(key: String, defaultValue: String?): String?

    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun getInt(key: String, defaultValue: Int): Int

    fun edit(block: Editor.() -> Unit)

    interface Editor {
        fun putString(key: String, value: String?)

        fun putBoolean(key: String, value: Boolean)

        fun putInt(key: String, value: Int)

        fun remove(key: String)
    }
}

internal class SharedPreferencesAppSettingsPreferences(
    private val preferences: SharedPreferences,
) : AppSettingsPreferences {
    override val all: Map<String, *>
        get() = preferences.all

    override fun getString(key: String, defaultValue: String?): String? {
        return preferences.getString(key, defaultValue)
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return preferences.getBoolean(key, defaultValue)
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return preferences.getInt(key, defaultValue)
    }

    override fun edit(block: AppSettingsPreferences.Editor.() -> Unit) {
        preferences.edit {
            SharedPreferencesEditor(this).block()
        }
    }
}

private class SharedPreferencesEditor(
    private val editor: SharedPreferences.Editor,
) : AppSettingsPreferences.Editor {
    override fun putString(key: String, value: String?) {
        editor.putString(key, value)
    }

    override fun putBoolean(key: String, value: Boolean) {
        editor.putBoolean(key, value)
    }

    override fun putInt(key: String, value: Int) {
        editor.putInt(key, value)
    }

    override fun remove(key: String) {
        editor.remove(key)
    }
}

// AppSettingsPreferencesCodec
internal fun AppSettingsPreferences.readAppSettings(): AppSettings {
    return AppSettings(
        defaultQuality = getString(KEY_DEFAULT_QUALITY, null)
            ?.let(PreferredQuality::fromName)
            ?: PreferredQuality.Auto,
        decoderMode = getString(KEY_DECODER_MODE, null)
            ?.let(PlayerDecoderMode::fromName)
            ?: PlayerDecoderMode.Auto,
        playerBufferPreset = getString(KEY_PLAYER_BUFFER_PRESET, null)
            ?.let(PlayerBufferPreset::fromName)
            ?: PlayerBufferPreset.Standard,
        playerSpeed = getString(KEY_PLAYER_SPEED, null)
            ?.let(PlayerSpeed::fromName)
            ?: PlayerSpeed.Normal,
        matchDisplayModeToVideo = getBoolean(KEY_MATCH_DISPLAY_MODE_TO_VIDEO, false),
        skipOpeningsAndEndings = getBoolean(KEY_SKIP_OPENINGS_AND_ENDINGS, true),
        autoplayNextEpisode = getBoolean(KEY_AUTOPLAY_NEXT_EPISODE, true),
        autoMarkWatchingOnPlayback = getBoolean(KEY_AUTO_MARK_WATCHING_ON_PLAYBACK, false),
        autoMarkWatchedOnCompletedFinalEpisode =
            getBoolean(KEY_AUTO_MARK_WATCHED_ON_COMPLETED_FINAL_EPISODE, false),
        notificationsEnabled = getBoolean(KEY_NOTIFICATIONS_ENABLED, true),
        autoCheckUpdates = getBoolean(KEY_AUTO_CHECK_UPDATES, true),
        downloadParallelism = getInt(KEY_DOWNLOAD_PARALLELISM, 1).coerceIn(1, 4),
        downloadSpeedLimitMegabytesPerSecond = getInt(
            KEY_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
            DEFAULT_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
        ).coerceIn(MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND, MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND),
        allowMeteredDownloads = getBoolean(KEY_ALLOW_METERED_DOWNLOADS, false),
        posterCardSize = getString(KEY_POSTER_CARD_SIZE, null)
            ?.let(PosterCardSize::fromName)
            ?: PosterCardSize.Standard,
        interfaceScale = readInterfaceScalePreference(),
        contentLanguage = readContentLanguagePreference(),
        siteDomains = getString(KEY_SITE_DOMAINS, null)
            ?.lineSequence()
            ?.toList()
            ?.normalizedSiteBaseUrls()
            ?.ifEmpty { SiteDomainResolver.DEFAULT_SITE_DOMAINS }
            ?: SiteDomainResolver.DEFAULT_SITE_DOMAINS,
        savedBrowseFilters = getString(KEY_BROWSE_FILTERS, null)
            ?.decodeAppJsonOrNull<BrowseFilters>()
            ?: BrowseFilters(),
    ).normalized()
}

internal fun AppSettingsPreferences.saveAppSettings(settings: AppSettings) {
    val normalizedSettings = settings.normalized()
    edit {
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

internal fun AppSettingsPreferences.readInterfaceScalePreference(): InterfaceScale {
    return InterfaceScale.fromPersistedValue(all[KEY_INTERFACE_SCALE])
        ?: InterfaceScale.Default
}

internal fun AppSettingsPreferences.saveInterfaceScalePreference(interfaceScale: InterfaceScale) {
    edit {
        putInt(KEY_INTERFACE_SCALE, InterfaceScale.fromPercent(interfaceScale.percent).percent)
    }
}

internal fun AppSettingsPreferences.readContentLanguagePreference(): ContentLanguage {
    return getString(KEY_CONTENT_LANGUAGE, null)
        ?.let(ContentLanguage::fromName)
        ?: ContentLanguage.Russian
}

internal fun AppSettingsPreferences.saveContentLanguagePreference(contentLanguage: ContentLanguage) {
    edit {
        putString(KEY_CONTENT_LANGUAGE, contentLanguage.name)
    }
}

private const val KEY_DEFAULT_QUALITY = "default_quality"
private const val KEY_DECODER_MODE = "decoder_mode"
private const val KEY_PLAYER_BUFFER_PRESET = "player_buffer_preset"
private const val KEY_PLAYER_SPEED = "player_speed"
private const val KEY_MATCH_DISPLAY_MODE_TO_VIDEO = "match_display_mode_to_video"
private const val KEY_SKIP_OPENINGS_AND_ENDINGS = "skip_openings_and_endings"
private const val KEY_AUTOPLAY_NEXT_EPISODE = "autoplay_next_episode"
private const val KEY_AUTO_MARK_WATCHING_ON_PLAYBACK = "auto_mark_watching_on_playback"
private const val KEY_AUTO_MARK_WATCHED_ON_COMPLETED_FINAL_EPISODE = "auto_mark_watched_on_completed_final_episode"
private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
private const val KEY_AUTO_CHECK_UPDATES = "auto_check_updates"
private const val KEY_DOWNLOAD_PARALLELISM = "download_parallelism"
private const val KEY_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND = "download_speed_limit_mb_per_second"
private const val KEY_ALLOW_METERED_DOWNLOADS = "allow_metered_downloads"
private const val KEY_APP_THEME = "app_theme"
private const val KEY_POSTER_CARD_SIZE = "poster_card_size"
private const val KEY_INTERFACE_SCALE = "interface_scale"
private const val KEY_CONTENT_LANGUAGE = "content_language"
private const val KEY_SITE_DOMAINS = "site_domains"
private const val KEY_BROWSE_FILTERS = "browse_filters"

// AppSettingsStorage
class AppSettingsStorage internal constructor(
    private val prefs: AppSettingsPreferences,
) {
    constructor(context: Context) : this(
        SharedPreferencesAppSettingsPreferences(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        ),
    )

    fun read(): AppSettings = prefs.readAppSettings()

    fun save(settings: AppSettings) {
        prefs.saveAppSettings(settings)
    }

    fun readInterfaceScale(): InterfaceScale = prefs.readInterfaceScalePreference()

    fun readContentLanguage(): ContentLanguage = prefs.readContentLanguagePreference()

    fun saveInterfaceScale(interfaceScale: InterfaceScale) {
        prefs.saveInterfaceScalePreference(interfaceScale)
    }

    fun saveContentLanguage(contentLanguage: ContentLanguage) {
        prefs.saveContentLanguagePreference(contentLanguage)
    }

    private companion object {
        const val PREFS_NAME = "yummydroid_settings"
    }
}

// DisplaySettings
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
            val clamped = percent.coerceIn(MIN_INTERFACE_SCALE_PERCENT, MAX_INTERFACE_SCALE_PERCENT)
            val stepOffset = clamped - MIN_INTERFACE_SCALE_PERCENT
            val normalizedStep = (stepOffset + INTERFACE_SCALE_STEP_PERCENT / 2) / INTERFACE_SCALE_STEP_PERCENT
            return InterfaceScale(
                (MIN_INTERFACE_SCALE_PERCENT + normalizedStep * INTERFACE_SCALE_STEP_PERCENT)
                    .coerceAtMost(MAX_INTERFACE_SCALE_PERCENT),
            )
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

// DownloadSpeedLimits
const val DEFAULT_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND = 5
const val MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND = 1
const val MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND = 50
const val DOWNLOAD_SPEED_LIMIT_WARNING_THRESHOLD_MB_PER_SECOND = 10

internal const val BYTES_PER_MEGABYTE = 1024L * 1024L

// InterfaceScaleLimits
const val MIN_INTERFACE_SCALE_PERCENT = 50
const val MAX_INTERFACE_SCALE_PERCENT = 130
const val INTERFACE_SCALE_STEP_PERCENT = 10
const val DEFAULT_INTERFACE_SCALE_PERCENT = 100

// JsonConfig
@PublishedApi
internal val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

inline fun <reified T> String.decodeAppJsonOrNull(): T? {
    return runCatching { AppJson.decodeFromString<T>(this) }.getOrNull()
}

inline fun <reified T> T.encodeAppJson(): String {
    return AppJson.encodeToString(this)
}

inline fun <reified T> SharedPreferences.getJsonOrNull(key: String): T? {
    return getString(key, null)?.decodeAppJsonOrNull()
}

inline fun <reified T> SharedPreferences.putJson(key: String, value: T) {
    edit {
        putString(key, value.encodeAppJson())
    }
}

inline fun <reified T> File.readJsonOrNull(): T? {
    if (!exists()) return null
    return runCatching { readText().decodeAppJsonOrNull<T>() }.getOrNull()
}

inline fun <reified T> File.writeJson(value: T) {
    parentFile?.mkdirs()
    writeText(value.encodeAppJson())
}

// LegacyFilterDefaults
enum class AnimeStatusFilter(
    val title: String,
    val apiValue: String?,
) {
    All("All", null),
}

enum class AnimeGenreFilter(
    val title: String,
    val apiValue: String?,
) {
    All("All genres", null),
}

// PlaybackSettings
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
