package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppSettingsStorageRoundTripTest {
    @Test
    fun settingsRoundTripUsesNormalizedValues() {
        val preferences = InMemoryAppSettingsPreferences().apply {
            values["app_theme"] = "LegacyTheme"
        }
        val storage = AppSettingsStorage(preferences)
        val settings = AppSettings(
            defaultQuality = PreferredQuality.P1080,
            decoderMode = PlayerDecoderMode.Hardware,
            playerBufferPreset = PlayerBufferPreset.Large,
            playerSpeed = PlayerSpeed.X15,
            matchDisplayModeToVideo = true,
            skipOpeningsAndEndings = false,
            autoplayNextEpisode = false,
            autoMarkWatchingOnPlayback = true,
            autoMarkWatchedOnCompletedFinalEpisode = true,
            notificationsEnabled = false,
            autoCheckUpdates = false,
            downloadParallelism = 99,
            downloadSpeedLimitMegabytesPerSecond = Int.MAX_VALUE,
            allowMeteredDownloads = true,
            posterCardSize = PosterCardSize.Large,
            interfaceScale = InterfaceScale(126),
            contentLanguage = ContentLanguage.English,
            siteDomains = listOf("https://example.com/", "example.com"),
            savedBrowseFilters = BrowseFilters(
                sort = AnimeSort.Year,
                fromYear = 2020,
                genres = setOf("Action"),
                offlineOnly = true,
            ),
        )

        storage.save(settings)

        assertEquals(settings.normalized(), storage.read())
        assertFalse("app_theme" in preferences.values)
    }
}
