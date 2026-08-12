package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.SiteNotification

class ProfileNotificationCoordinatorTest {
    @Test
    fun updateGatePostsForEachSupportedTrigger() {
        var now = 1_000L
        val gate = NotificationUpdateGate(minIntervalMs = 100L) { now }

        assertTrue(gate.shouldPost())
        assertFalse(gate.shouldPost())

        now = 1_050L
        assertTrue(gate.shouldPost(force = true))
        assertFalse(gate.shouldPost())

        now = 900L
        assertTrue(gate.shouldPost())

        now = 901L
        gate.reset()
        assertTrue(gate.shouldPost())

        now = 1_001L
        assertTrue(gate.shouldPost())
    }

    @Test
    fun loadUsesCanonicalLimitSortsNewestFirstAndSynchronizesRuntime() = runBlocking {
        val events = mutableListOf<String>()
        val runtime = RecordingRuntime(events)
        val coordinator = coordinator(
            runtime = runtime,
            fetchNotifications = { limit ->
                events += "fetch:$limit"
                listOf(notification(1, dateSeconds = 10), notification(2, dateSeconds = 30))
            },
        )

        val result = coordinator.load(profileId = 42)

        assertEquals(listOf(2L, 1L), result.map(SiteNotification::id))
        assertEquals(listOf("fetch:80", "runtime"), events)
        assertEquals(42, runtime.calls.single().profileId)
        assertEquals(listOf(2L, 1L), runtime.calls.single().notifications.map(SiteNotification::id))
    }

    @Test
    fun markReadSynchronizesOptimisticSnapshotBeforeBackendMutation() = runBlocking {
        val events = mutableListOf<String>()
        val runtime = RecordingRuntime(events)
        val coordinator = coordinator(
            runtime = runtime,
            markNotificationRead = { id -> events += "mark:$id" },
        )
        val notifications = listOf(notification(7, viewed = true), notification(8))

        coordinator.markRead(profileId = 42, notificationId = 7, notifications = notifications)

        assertEquals(listOf("runtime", "mark:7"), events)
        assertEquals(listOf(7L), runtime.calls.single().cancelledNotificationIds)
        assertEquals(notifications, runtime.calls.single().notifications)
    }

    @Test
    fun markAllReadCancelsLoadedNotificationsBeforeBackendMutation() = runBlocking {
        val events = mutableListOf<String>()
        val runtime = RecordingRuntime(events)
        val coordinator = coordinator(
            runtime = runtime,
            markAllNotificationsRead = { events += "mark-all" },
        )
        val notifications = listOf(notification(7, viewed = true), notification(8, viewed = true))

        coordinator.markAllRead(profileId = 42, notifications = notifications)

        assertEquals(listOf("runtime", "mark-all"), events)
        assertEquals(listOf(7L, 8L), runtime.calls.single().cancelledNotificationIds)
    }

    @Test
    fun deleteSynchronizesRemainingSnapshotBeforeBackendMutation() = runBlocking {
        val events = mutableListOf<String>()
        val runtime = RecordingRuntime(events)
        val coordinator = coordinator(
            runtime = runtime,
            deleteNotification = { id -> events += "delete:$id" },
        )
        val remaining = listOf(notification(8))

        coordinator.delete(profileId = 42, notificationId = 7, notifications = remaining)

        assertEquals(listOf("runtime", "delete:7"), events)
        assertEquals(listOf(7L), runtime.calls.single().cancelledNotificationIds)
        assertEquals(remaining, runtime.calls.single().notifications)
    }

    @Test
    fun cancellationFromBackendMutationPropagates() = runBlocking {
        val runtime = RecordingRuntime()
        val coordinator = coordinator(
            runtime = runtime,
            markNotificationRead = { throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> {
            coordinator.markRead(
                profileId = 42,
                notificationId = 7,
                notifications = listOf(notification(7, viewed = true)),
            )
        }

        assertEquals(1, runtime.calls.size)
    }

    private fun coordinator(
        runtime: ProfileNotificationRuntime = RecordingRuntime(),
        fetchNotifications: suspend (Int) -> List<SiteNotification> = { emptyList() },
        markNotificationRead: suspend (Long) -> Unit = {},
        markAllNotificationsRead: suspend () -> Unit = {},
        deleteNotification: suspend (Long) -> Unit = {},
    ): ProfileNotificationCoordinator {
        return ProfileNotificationCoordinator(
            runtime = runtime,
            fetchNotifications = fetchNotifications,
            markNotificationRead = markNotificationRead,
            markAllNotificationsRead = markAllNotificationsRead,
            deleteNotification = deleteNotification,
        )
    }

    private fun notification(
        id: Long,
        dateSeconds: Long = id,
        viewed: Boolean = false,
    ): SiteNotification {
        return SiteNotification(
            id = id,
            title = "Title $id",
            text = "",
            clickUrl = "",
            type = "",
            subType = "",
            objectId = 0,
            dateSeconds = dateSeconds,
            viewed = viewed,
        )
    }

    private class RecordingRuntime(
        private val events: MutableList<String>? = null,
    ) : ProfileNotificationRuntime {
        val calls = mutableListOf<RuntimeCall>()

        override suspend fun synchronize(
            profileId: Long,
            notifications: List<SiteNotification>,
            cancelledNotificationIds: List<Long>,
        ) {
            events?.add("runtime")
            calls += RuntimeCall(profileId, notifications, cancelledNotificationIds)
        }
    }

    private data class RuntimeCall(
        val profileId: Long,
        val notifications: List<SiteNotification>,
        val cancelledNotificationIds: List<Long>,
    )
}
