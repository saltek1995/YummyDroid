package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.SiteNotification
import me.yummydroid.app.data.UserProfile

class ProfileNotificationStateTest {
    @Test
    fun unreadNotificationsNeverDropBelowZero() {
        val auth = AuthUiState(profile = userProfile(unreadNotifications = 2))

        assertEquals(0, auth.withUnreadNotifications(-4).profile?.unreadNotifications)
    }

    @Test
    fun unreadDeltaUpdatesExistingProfileCount() {
        val auth = AuthUiState(profile = userProfile(unreadNotifications = 2))

        assertEquals(5, auth.withUnreadNotificationDelta(3).profile?.unreadNotifications)
        assertEquals(0, auth.withUnreadNotificationDelta(-7).profile?.unreadNotifications)
    }

    @Test
    fun unreadDeltaKeepsAnonymousAuthUnchanged() {
        assertEquals(AuthUiState(), AuthUiState().withUnreadNotificationDelta(3))
    }

    @Test
    fun unreadCountCountsOnlyUnviewedNotifications() {
        val notifications = listOf(
            notification(id = 1, viewed = false),
            notification(id = 2, viewed = true),
            notification(id = 3, viewed = false),
        )

        assertEquals(2, notifications.unreadCount())
    }

    private fun userProfile(unreadNotifications: Int): UserProfile {
        return UserProfile(
            id = 10,
            nickname = "test",
            avatarUrl = "",
            about = "",
            roles = emptyList(),
            unreadNotifications = unreadNotifications,
        )
    }

    private fun notification(id: Long, viewed: Boolean): SiteNotification {
        return SiteNotification(
            id = id,
            title = "Title",
            text = "",
            clickUrl = "",
            type = "",
            subType = "",
            objectId = 0,
            dateSeconds = id,
            viewed = viewed,
        )
    }
}
