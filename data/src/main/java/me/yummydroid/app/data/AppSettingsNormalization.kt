package me.yummydroid.app.data

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

