package me.yummydroid.app.data

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
