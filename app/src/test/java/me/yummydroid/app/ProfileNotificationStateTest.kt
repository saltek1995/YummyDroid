package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    @Test
    fun loadedNotificationsReplaceListAndDeriveExactUnreadCount() {
        val state = uiState(unreadNotifications = 8)
        val notifications = listOf(
            notification(id = 1, viewed = false),
            notification(id = 2, viewed = true),
        )

        val updated = state.withProfileNotifications(notifications)

        assertEquals(notifications, assertIs<LoadState.Ready<List<SiteNotification>>>(updated.profileNotifications).data)
        assertEquals(1, updated.auth.profile?.unreadNotifications)
    }

    @Test
    fun markingOneNotificationReadUpdatesOnlyThatItemAndExactCount() {
        val state = uiState(
            notifications = listOf(
                notification(id = 1, viewed = false),
                notification(id = 2, viewed = false),
            ),
            unreadNotifications = 9,
        )

        val updated = state.withProfileNotificationRead(notificationId = 2)
        val notifications = assertIs<LoadState.Ready<List<SiteNotification>>>(updated.profileNotifications).data

        assertEquals(listOf(false, true), notifications.map(SiteNotification::viewed))
        assertEquals(1, updated.auth.profile?.unreadNotifications)
    }

    @Test
    fun markingAllNotificationsReadPreservesItemsAndClearsCount() {
        val state = uiState(
            notifications = listOf(notification(id = 1, viewed = false), notification(id = 2, viewed = true)),
            unreadNotifications = 4,
        )

        val updated = state.withAllProfileNotificationsRead()
        val notifications = assertIs<LoadState.Ready<List<SiteNotification>>>(updated.profileNotifications).data

        assertEquals(listOf(1L, 2L), notifications.map(SiteNotification::id))
        assertEquals(listOf(true, true), notifications.map(SiteNotification::viewed))
        assertEquals(0, updated.auth.profile?.unreadNotifications)
    }

    @Test
    fun deletingNotificationDerivesCountFromRemainingList() {
        val removed = notification(id = 1, viewed = false)
        val state = uiState(
            notifications = listOf(removed, notification(id = 2, viewed = false), notification(id = 3, viewed = true)),
            unreadNotifications = 9,
        )

        val updated = state.withoutProfileNotification(removed)
        val notifications = assertIs<LoadState.Ready<List<SiteNotification>>>(updated.profileNotifications).data

        assertEquals(listOf(2L, 3L), notifications.map(SiteNotification::id))
        assertEquals(1, updated.auth.profile?.unreadNotifications)
    }

    @Test
    fun retryingDeleteDoesNotDecrementUnreadCountTwice() {
        val state = uiState(
            notifications = listOf(notification(id = 2, viewed = false)),
            unreadNotifications = 1,
        )

        val updated = state.withoutProfileNotification(notification(id = 1, viewed = false))

        assertEquals(1, updated.auth.profile?.unreadNotifications)
    }

    private fun uiState(
        notifications: List<SiteNotification> = emptyList(),
        unreadNotifications: Int,
    ): YummyDroidUiState {
        return YummyDroidUiState(
            auth = AuthUiState(profile = userProfile(unreadNotifications)),
            profileNotifications = LoadState.Ready(notifications),
        )
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
