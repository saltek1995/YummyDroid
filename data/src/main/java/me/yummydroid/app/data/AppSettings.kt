package me.yummydroid.app.data

const val DEFAULT_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND = 5
const val MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND = 1
const val MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND = 50
const val DOWNLOAD_SPEED_LIMIT_WARNING_THRESHOLD_MB_PER_SECOND = 10
const val MIN_INTERFACE_SCALE_PERCENT = 50
const val MAX_INTERFACE_SCALE_PERCENT = 130
const val INTERFACE_SCALE_STEP_PERCENT = 10
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
