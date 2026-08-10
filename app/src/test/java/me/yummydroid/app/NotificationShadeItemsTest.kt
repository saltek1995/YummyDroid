package me.yummydroid.app

import me.yummydroid.app.data.SiteNotification
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationShadeItemsTest {
    @Test
    fun shadeItemsAreNewestFirstAndTrimText() {
        val items = listOf(
            siteNotification(id = 1, title = " Old ", text = " Body ", dateSeconds = 10),
            siteNotification(id = 2, title = " New ", text = " Text ", dateSeconds = 20),
        ).toNotificationShadeItems()

        assertEquals(listOf(2L, 1L), items.map { it.id })
        assertEquals(listOf("New", "Old"), items.map { it.title })
        assertEquals(listOf("Text", "Body"), items.map { it.text })
    }

    @Test
    fun unreadJsonFiltersViewedLimitsAndRoundTrips() {
        val notifications = (1L..25L).map { id ->
            siteNotification(id = id, dateSeconds = id, viewed = id == 25L)
        }

        val json = notifications.unreadNotificationShadeItemsJson(maxItems = 20)
        val restored = decodeNotificationShadeItems(json)

        assertEquals((24L downTo 5L).toList(), restored.map { it.id })
    }

    @Test
    fun emptyOrMalformedSnapshotDecodesAsEmpty() {
        assertNull(emptyList<SiteNotification>().unreadNotificationShadeItemsJson(20))
        assertEquals(emptyList(), decodeNotificationShadeItems(null))
        assertEquals(emptyList(), decodeNotificationShadeItems("not json"))
    }
}
