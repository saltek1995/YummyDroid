package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.yummydroid.app.data.SiteNotification

class SiteNotificationNavigationTest {
    @Test
    fun animeIdForOpenUsesCatalogUrlId() {
        val notification = notification(
            clickUrl = "https://yummyani.me/catalog/item/keyon-1-5500",
            objectId = 12,
        )

        assertEquals(5500, notification.animeIdForOpen())
    }

    @Test
    fun animeIdForOpenFallsBackToObjectId() {
        val notification = notification(
            clickUrl = "https://yummyani.me/profile/notifications",
            objectId = 5500,
        )

        assertEquals(5500, notification.animeIdForOpen())
    }

    @Test
    fun animeIdForOpenReturnsNullWithoutKnownTarget() {
        val notification = notification(
            clickUrl = "https://yummyani.me/profile/notifications",
            objectId = 0,
        )

        assertNull(notification.animeIdForOpen())
    }

    private fun notification(
        clickUrl: String,
        objectId: Long,
    ): SiteNotification {
        return SiteNotification(
            id = 1,
            title = "New episode",
            text = "",
            clickUrl = clickUrl,
            type = "anime_episode",
            subType = "new_episode",
            objectId = objectId,
            dateSeconds = 1,
            viewed = false,
        )
    }
}
