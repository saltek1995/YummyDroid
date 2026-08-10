package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionNotificationPreferencesTest {
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
}
