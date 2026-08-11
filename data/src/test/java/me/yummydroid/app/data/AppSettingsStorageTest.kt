package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AppSettingsStorageTest {
    @Test
    fun emptyPreferencesReturnApplicationDefaults() {
        val storage = AppSettingsStorage(InMemoryAppSettingsPreferences())

        assertEquals(AppSettings(), storage.read())
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

    @Test
    fun persistedLegacyScaleValuesRemainReadable() {
        val preferences = InMemoryAppSettingsPreferences()
        val storage = AppSettingsStorage(preferences)

        preferences.values["interface_scale"] = "Percent120"
        assertEquals(InterfaceScale(120), storage.readInterfaceScale())

        preferences.values["interface_scale"] = "150%"
        assertEquals(InterfaceScale(130), storage.readInterfaceScale())

        preferences.values["interface_scale"] = "Unknown"
        assertEquals(InterfaceScale.Default, storage.readInterfaceScale())
    }

    @Test
    fun interfaceScaleCanBePersistedBeforeActivityRecreation() {
        val storage = AppSettingsStorage(InMemoryAppSettingsPreferences())

        storage.saveInterfaceScale(InterfaceScale(126))

        assertEquals(InterfaceScale(130), storage.readInterfaceScale())
    }

    @Test
    fun corruptPreferencesUseSafeFallbacksAndBounds() {
        val preferences = InMemoryAppSettingsPreferences().apply {
            values["default_quality"] = "Unknown"
            values["decoder_mode"] = "Unknown"
            values["player_buffer_preset"] = "Unknown"
            values["player_speed"] = "Unknown"
            values["download_parallelism"] = Int.MIN_VALUE
            values["download_speed_limit_mb_per_second"] = Int.MAX_VALUE
            values["poster_card_size"] = "Unknown"
            values["interface_scale"] = "Unknown"
            values["content_language"] = "Unknown"
            values["site_domains"] = "\n  \n"
            values["browse_filters"] = "{broken-json"
        }

        val settings = AppSettingsStorage(preferences).read()

        assertEquals(PreferredQuality.Auto, settings.defaultQuality)
        assertEquals(PlayerDecoderMode.Auto, settings.decoderMode)
        assertEquals(PlayerBufferPreset.Standard, settings.playerBufferPreset)
        assertEquals(PlayerSpeed.Normal, settings.playerSpeed)
        assertEquals(1, settings.downloadParallelism)
        assertEquals(MAX_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND, settings.downloadSpeedLimitMegabytesPerSecond)
        assertEquals(PosterCardSize.Standard, settings.posterCardSize)
        assertEquals(InterfaceScale.Default, settings.interfaceScale)
        assertEquals(ContentLanguage.Russian, settings.contentLanguage)
        assertEquals(SiteDomainResolver.DEFAULT_SITE_DOMAINS, settings.siteDomains)
        assertEquals(BrowseFilters(), settings.savedBrowseFilters)
    }
}

private class InMemoryAppSettingsPreferences : AppSettingsPreferences {
    val values = mutableMapOf<String, Any?>()

    override val all: Map<String, *>
        get() = values

    override fun getString(key: String, defaultValue: String?): String? {
        return values[key] as? String ?: defaultValue
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return values[key] as? Boolean ?: defaultValue
    }

    override fun getInt(key: String, defaultValue: Int): Int {
        return values[key] as? Int ?: defaultValue
    }

    override fun edit(block: AppSettingsPreferences.Editor.() -> Unit) {
        InMemoryEditor(values).block()
    }
}

private class InMemoryEditor(
    private val values: MutableMap<String, Any?>,
) : AppSettingsPreferences.Editor {
    override fun putString(key: String, value: String?) {
        values[key] = value
    }

    override fun putBoolean(key: String, value: Boolean) {
        values[key] = value
    }

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
