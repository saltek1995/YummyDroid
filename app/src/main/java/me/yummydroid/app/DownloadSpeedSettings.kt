package me.yummydroid.app

import me.yummydroid.app.data.AppSettingsStorage

internal class DownloadSpeedSettings(
    private val settingsStorage: AppSettingsStorage,
    initialLimitBytesPerSecond: Long,
    initialReadMs: Long,
) {
    @Volatile
    private var limitBytesPerSecond = initialLimitBytesPerSecond
    private val lock = Any()
    private var lastReadMs = initialReadMs

    fun currentLimitBytesPerSecond(): Long {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (now - lastReadMs >= SPEED_LIMIT_SETTINGS_REFRESH_MS) {
                limitBytesPerSecond = settingsStorage.read().downloadSpeedLimitBytesPerSecond
                lastReadMs = now
            }
            return limitBytesPerSecond
        }
    }
}

private const val SPEED_LIMIT_SETTINGS_REFRESH_MS = 1_000L
