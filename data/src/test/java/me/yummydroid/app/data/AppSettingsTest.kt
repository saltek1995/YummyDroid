package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsTest {
    @Test
    fun downloadSpeedLimitDefaultsToFiveMegabytesPerSecond() {
        val settings = AppSettings()

        assertEquals(DEFAULT_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND, settings.downloadSpeedLimitMegabytesPerSecond)
        assertEquals(5L * 1024L * 1024L, settings.downloadSpeedLimitBytesPerSecond)
    }

    @Test
    fun downloadSpeedLimitIsNormalized() {
        assertEquals(
            MIN_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
            AppSettings(downloadSpeedLimitMegabytesPerSecond = -1).normalized().downloadSpeedLimitMegabytesPerSecond,
        )
        assertEquals(
            MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND,
            AppSettings(downloadSpeedLimitMegabytesPerSecond = 1_000).normalized().downloadSpeedLimitMegabytesPerSecond,
        )
    }
}
