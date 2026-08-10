package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class AppUpdateInfoTest {
    @Test
    fun normalizedVersionTrimsWhitespaceAndLowercasePrefix() {
        val update = AppUpdateInfo(
            version = "  v1.3.93  ",
            title = "",
            body = "",
            pageUrl = "",
            apkUrl = "",
            publishedAt = "",
        )

        assertEquals("1.3.93", update.normalizedVersion)
        assertEquals("V1.3.93", update.copy(version = "V1.3.93").normalizedVersion)
    }
}
