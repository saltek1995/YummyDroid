package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppSettingsStorageRoundTripTest {
    @Test
    fun legacyDefaultAndMislabelledFiltersMigrateOnce() {
        val preferences = InMemoryAppSettingsPreferences()
        val storage = AppSettingsStorage(preferences)
        preferences.values["browse_filters"] = BrowseFilters(sort = AnimeSort.Rating).encodeAppJson()
        assertEquals(AnimeSort.Top, storage.read().savedBrowseFilters.sort)

        preferences.values["browse_filters"] = BrowseFilters(sort = AnimeSort.Year,
            ageRatings = setOf("2"), genres = setOf("action")).encodeAppJson()
        val migrated = storage.read()
        assertEquals(AnimeSort.Id, migrated.savedBrowseFilters.sort)
        assertEquals(setOf("3"), migrated.savedBrowseFilters.ageRatings)
        assertEquals(setOf("action"), migrated.savedBrowseFilters.genres)
        storage.save(migrated)
        assertEquals(migrated, storage.read())

        // New deliberate rating/age/direction choices must not be migrated again.
        val selected = migrated.copy(savedBrowseFilters = BrowseFilters(sort = AnimeSort.Rating,
            sortForward = true, ageRatings = setOf("1")))
        storage.save(selected)
        assertEquals(selected, storage.read())
    }

    @Test
    fun legacyExplicitSortIsPreservedAndUnsupportedAgeDoesNotBecomeAnotherRating() {
        val preferences = InMemoryAppSettingsPreferences().apply {
            values["browse_filters"] = BrowseFilters(sort = AnimeSort.Views, ageRatings = setOf("5")).encodeAppJson()
        }
        val filters = AppSettingsStorage(preferences).read().savedBrowseFilters
        assertEquals(AnimeSort.Views, filters.sort)
        assertEquals(emptySet(), filters.ageRatings)
        preferences.values["browse_filters"] = BrowseFilters(sort = AnimeSort.Title).encodeAppJson()
        assertEquals(true, AppSettingsStorage(preferences).read().savedBrowseFilters.effectiveSortForward)
    }

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
