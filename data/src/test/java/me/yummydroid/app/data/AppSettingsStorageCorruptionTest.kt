package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsStorageCorruptionTest {
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
