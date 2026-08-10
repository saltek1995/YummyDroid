package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SharedPreferencesAppSettingsStorageTest {
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
