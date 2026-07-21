package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersioningTest {
    @Test
    fun comparesVersionsWithPrefixAndSuffix() {
        val update = AppUpdateInfo(
            version = "v1.2.10-beta",
            title = "",
            body = "",
            pageUrl = "",
            apkUrl = "",
            publishedAt = "",
        )

        assertTrue(update.isNewerThanVersion("1.2.9"))
        assertFalse(update.isNewerThanVersion("1.2.10"))
        assertFalse(update.isNewerThanVersion("1.3.0"))
    }
}
