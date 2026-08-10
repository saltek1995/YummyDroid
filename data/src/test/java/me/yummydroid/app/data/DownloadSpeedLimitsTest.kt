package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadSpeedLimitsTest {
    @Test
    fun defaultLimitIsFiveMegabytesPerSecond() {
        val settings = AppSettings()

        assertEquals(5L * 1024L * 1024L, settings.downloadSpeedLimitBytesPerSecond)
    }

    @Test
    fun limitIsClampedBeforeConversionToBytes() {
        assertEquals(1L * 1024L * 1024L, AppSettings(downloadSpeedLimitMegabytesPerSecond = -1).downloadSpeedLimitBytesPerSecond)
        assertEquals(50L * 1024L * 1024L, AppSettings(downloadSpeedLimitMegabytesPerSecond = 1_000).downloadSpeedLimitBytesPerSecond)
    }

    @Test
    fun normalizationStoresClampedLimit() {
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
