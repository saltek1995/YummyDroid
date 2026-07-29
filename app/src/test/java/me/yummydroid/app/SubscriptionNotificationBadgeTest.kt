package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SubscriptionNotificationBadgeTest {
    @Test
    fun shadeContentUsesNewestUnreadNotificationTitles() {
        val content = SubscriptionNotificationBadge.notificationShadeContent(
            unreadCount = 3,
            shadeItems = listOf(
                NotificationShadeItem(
                    id = 1,
                    title = "Older title",
                    text = "Older body",
                    dateSeconds = 10,
                ),
                NotificationShadeItem(
                    id = 2,
                    title = "Newest title",
                    text = "Newest body",
                    dateSeconds = 30,
                ),
                NotificationShadeItem(
                    id = 3,
                    title = "Middle title",
                    text = "Middle body",
                    dateSeconds = 20,
                ),
            ),
            fallbackTitle = "Unread notifications",
            countText = "3 unread notifications",
        )

        assertEquals("Newest title", content.title)
        assertEquals("Middle title", content.text)
        assertEquals(
            listOf("Newest title", "Middle title", "Older title"),
            content.inboxLines,
        )
    }

    @Test
    fun shadeContentFallsBackToBodyWhenTitleIsBlank() {
        val content = SubscriptionNotificationBadge.notificationShadeContent(
            unreadCount = 1,
            shadeItems = listOf(
                NotificationShadeItem(
                    id = 1,
                    title = "",
                    text = "Concrete notification body",
                    dateSeconds = 10,
                ),
            ),
            fallbackTitle = "Unread notifications",
            countText = "1 unread notification",
        )

        assertEquals("Concrete notification body", content.title)
        assertEquals("Concrete notification body", content.inboxLines.single())
    }

    @Test
    fun profileNotificationsIntentIsRecognized() {
        assertTrue(
            requestsProfileNotifications(
                action = ACTION_OPEN_PROFILE_NOTIFICATIONS,
                openExtra = false,
            ),
        )
        assertTrue(
            requestsProfileNotifications(
                action = null,
                openExtra = true,
            ),
        )
    }
}
