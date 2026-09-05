package me.yummydroid.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
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

    @Test
    fun failedReadRestoresNotificationSnapshotWithoutReplacingOtherProfileChanges() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val original = uiState(listOf(notification(1, false), notification(2, false)), 2)
        val fixture = RuntimeFixture(this, original, mark = { release.await(); error("offline") })

        fixture.runtime.markRead(notification(1, false))
        yield()
        assertEquals(1, fixture.state.auth.profile?.unreadNotifications)
        fixture.state = fixture.state.copy(
            auth = fixture.state.auth.copy(profile = fixture.state.auth.profile!!.copy(nickname = "updated")),
        )
        release.complete(Unit)
        yield()

        assertEquals(original.profileNotifications, fixture.state.profileNotifications)
        assertEquals(2, fixture.state.auth.profile?.unreadNotifications)
        assertEquals("updated", fixture.state.auth.profile?.nickname)
        assertEquals(listOf("offline"), fixture.errors)
        assertTrue(fixture.persisted.isEmpty())
    }

    @Test
    fun queuedDeleteStartsFromRolledBackReadAndPersistsOnlyConfirmedChanges() = runBlocking {
        val releaseRead = CompletableDeferred<Unit>()
        val deleted = CompletableDeferred<Unit>()
        val fixture = RuntimeFixture(
            this,
            uiState(listOf(notification(1, false), notification(2, false)), 2),
            mark = { releaseRead.await(); error("offline") },
            delete = { deleted.complete(Unit) },
        )

        fixture.runtime.markRead(notification(1, false))
        yield()
        fixture.runtime.delete(notification(2, false))
        releaseRead.complete(Unit)
        deleted.await()
        yield()

        assertEquals(listOf(notification(1, false)), fixture.state.profileNotifications.readyDataOrNull())
        assertEquals(1, fixture.state.auth.profile?.unreadNotifications)
        assertEquals(listOf(listOf(notification(1, false))), fixture.persisted)
        assertEquals(listOf("offline"), fixture.errors)
    }

    @Test
    fun refreshWaitsForMutationAndPublishesTheResultBeforeQueuedEdits() = runBlocking {
        val releaseDelete = CompletableDeferred<Unit>()
        val releaseFetch = CompletableDeferred<Unit>()
        val fetched = CompletableDeferred<Unit>()
        val marked = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val fixture = RuntimeFixture(
            this,
            uiState(listOf(notification(1, false), notification(2, false)), 2),
            delete = { events += "delete"; releaseDelete.await() },
            fetch = {
                events += "fetch"
                fetched.complete(Unit)
                releaseFetch.await()
                listOf(notification(1, false), notification(3, false))
            },
            mark = { events += "mark"; marked.complete(Unit) },
        )

        fixture.runtime.delete(notification(2, false))
        yield()
        fixture.runtime.refresh()
        yield()
        assertEquals(listOf("delete"), events)
        releaseDelete.complete(Unit)
        fetched.await()
        fixture.runtime.markRead(notification(1, false))
        releaseFetch.complete(Unit)
        marked.await()
        yield()

        assertEquals(listOf("delete", "fetch", "mark"), events)
        assertEquals(
            listOf(notification(3, false), notification(1, true)),
            fixture.state.profileNotifications.readyDataOrNull(),
        )
        assertEquals(1, fixture.state.auth.profile?.unreadNotifications)
    }

    @Test
    fun cancellingAccountOperationsDoesNotRestoreSignedOutProfile() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val fixture = RuntimeFixture(
            this,
            uiState(listOf(notification(1, false)), 1),
            mark = { release.await() },
        )

        fixture.runtime.markRead(notification(1, false))
        yield()
        fixture.runtime.cancel()
        val guest = fixture.state.copy(auth = AuthUiState(), profileNotifications = LoadState.Ready(emptyList()))
        fixture.state = guest
        release.complete(Unit)
        yield()

        assertEquals(guest, fixture.state)
        assertTrue(fixture.errors.isEmpty())
        assertTrue(fixture.persisted.isEmpty())
    }

    private class RuntimeFixture(
        scope: CoroutineScope,
        initialState: YummyDroidUiState,
        mark: suspend (Long) -> Unit = {},
        delete: suspend (Long) -> Unit = {},
        fetch: suspend (Int) -> List<SiteNotification> = { emptyList() },
    ) {
        var state = initialState
        val errors = mutableListOf<String>()
        val persisted = mutableListOf<List<SiteNotification>>()
        val runtime = ProfileNotificationStateRuntime(
            scope = scope,
            coordinator = ProfileNotificationCoordinator(
                runtime = object : ProfileNotificationRuntime {
                    override suspend fun synchronize(
                        profileId: Long,
                        notifications: List<SiteNotification>,
                        cancelledNotificationIds: List<Long>,
                    ) {
                        persisted += notifications
                    }
                },
                fetchNotifications = fetch,
                markNotificationRead = mark,
                markAllNotificationsRead = {},
                deleteNotification = delete,
            ),
            currentState = { state },
            updateState = { state = it(state) },
            requestCaptchaRetry = { _, _ -> false },
            showErrorNotice = errors::add,
        )
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
