package me.yummydroid.app.data

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
        contentLanguage = getString(KEY_CONTENT_LANGUAGE, null)
            ?.let(ContentLanguage::fromName)
            ?: ContentLanguage.Russian,
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
