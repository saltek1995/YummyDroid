package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionNotificationPreferencesTest {
    @Test
    fun accountSwitchKeepsIndependentInitializationHistoryAndUnreadSnapshot() {
        val preferences = InMemorySharedPreferences()
        val first = SubscriptionNotificationStore(preferences, profileId = 42) { 10_000L }
        val second = SubscriptionNotificationStore(preferences, profileId = 84) { 10_000L }
        val episode = siteNotification(id = 1, text = "Episode 1")
        first.markSeen(listOf(episode))
        first.markCheckRun()
        first.saveUnreadShadeItems(listOf(episode))

        assertFalse(second.isInitialized())
        assertFalse(second.isSeen(episode))
        assertTrue(second.shouldRunCheck(1_000))
        assertEquals(emptyList(), second.unreadShadeItems())
        second.markInitialized()
        second.clearUnreadShadeItems()

        val restored = SubscriptionNotificationStore(preferences, profileId = 42) { 10_000L }
        assertTrue(restored.isInitialized())
        assertTrue(restored.isSeen(episode))
        assertFalse(restored.shouldRunCheck(1_000))
        assertEquals(listOf(1L), restored.unreadShadeItems().map { it.id })
    }

    @Test
    fun checkClockHandlesSpacingAndClockRollback() {
        var nowMs = 10_000L
        val store = SubscriptionNotificationStore(InMemorySharedPreferences()) { nowMs }

        assertTrue(store.shouldRunCheck(minSpacingMs = 1_000))
        store.markCheckRun()
        assertFalse(store.shouldRunCheck(minSpacingMs = 1_000))

        nowMs += 1_000L
        assertTrue(store.shouldRunCheck(minSpacingMs = 1_000))
        nowMs = 1L
        assertTrue(store.shouldRunCheck(minSpacingMs = 1_000))
    }

    @Test
    fun seenHistoryKeepsNewestThreeHundredEventsAndInitializesStore() {
        val store = SubscriptionNotificationStore(InMemorySharedPreferences()) { 0L }
        val notifications = (1L..301L).map { id ->
            siteNotification(id = id, text = "Episode $id")
        }

        store.markSeen(notifications)

        assertTrue(store.isInitialized())
        assertFalse(store.isSeen(notifications.first()))
        assertTrue(store.isSeen(notifications.last()))
    }

    @Test
    fun unreadSnapshotPersistsAndClearsThroughCodec() {
        val store = SubscriptionNotificationStore(InMemorySharedPreferences()) { 0L }
        val notifications = listOf(
            siteNotification(id = 1, title = " Old ", dateSeconds = 10),
            siteNotification(id = 2, title = " New ", dateSeconds = 20),
        )

        store.saveUnreadShadeItems(notifications)
        assertEquals(listOf(2L, 1L), store.unreadShadeItems().map { it.id })

        store.clearUnreadShadeItems()
        assertEquals(emptyList(), store.unreadShadeItems())
    }

    @Test
    fun dismissedUnreadSetStaysDismissedWhenItShrinksAndReopensForNewItems() {
        val store = SubscriptionNotificationStore(InMemorySharedPreferences()) { 0L }

        store.markUnreadShadeItemsDismissed(profileId = 42L, notificationIds = longArrayOf(1L, 2L, 3L))

        assertTrue(store.areUnreadShadeItemsDismissed(42L, longArrayOf(1L, 2L, 3L)))
        assertTrue(store.areUnreadShadeItemsDismissed(42L, longArrayOf(2L, 3L)))
        assertFalse(store.areUnreadShadeItemsDismissed(42L, longArrayOf(2L, 3L, 4L)))
        assertFalse(store.areUnreadShadeItemsDismissed(7L, longArrayOf(1L, 2L, 3L)))
    }
}
