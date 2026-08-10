package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AppSettingsModelTest {
    @Test
    fun defaultsMatchApplicationContracts() {
        val settings = AppSettings()

        assertEquals(PreferredQuality.Auto, settings.defaultQuality)
        assertEquals(PlayerDecoderMode.Auto, settings.decoderMode)
        assertEquals(PlayerBufferPreset.Standard, settings.playerBufferPreset)
        assertEquals(PlayerSpeed.Normal, settings.playerSpeed)
        assertEquals(DEFAULT_DOWNLOAD_SPEED_LIMIT_MB_PER_SECOND, settings.downloadSpeedLimitMegabytesPerSecond)
        assertEquals(InterfaceScale.Default, settings.interfaceScale)
        assertEquals(ContentLanguage.Russian, settings.contentLanguage)
        assertEquals(SiteDomainResolver.DEFAULT_SITE_DOMAINS, settings.siteDomains)
    }
}
