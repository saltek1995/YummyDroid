package me.yummydroid.app.data

import android.content.Context

class AppSettingsStorage internal constructor(
    private val prefs: AppSettingsPreferences,
) {
    constructor(context: Context) : this(
        SharedPreferencesAppSettingsPreferences(
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        ),
    )

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
            savedBrowseFilters = prefs.getString(KEY_BROWSE_FILTERS, null)
                ?.decodeAppJsonOrNull<BrowseFilters>()
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
