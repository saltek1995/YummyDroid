package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.UserProfile

class SiteNavigationTest {
    @Test
    fun sitePageUrlNormalizesBaseAndPathSeparators() {
        assertEquals(
            "https://example.test/login/reset-password",
            sitePageUrl(" https://example.test/ ", " /login/reset-password "),
        )
    }

    @Test
    fun profileUrlUsesCanonicalUserIdPath() {
        val profile = UserProfile(
            id = 42,
            nickname = "test",
            avatarUrl = "",
            about = "",
            roles = emptyList(),
        )

        assertEquals("https://example.test/users/id42", profile.siteProfileUrl("https://example.test/"))
    }
}
