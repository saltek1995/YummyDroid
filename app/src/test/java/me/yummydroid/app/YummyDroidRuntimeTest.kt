package me.yummydroid.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertSame
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile

class YummyDroidRuntimeTest {
    @Test
    fun endedSessionClearsEveryAccountPanelAndOnlyAccountFilters() {
        val filters = BrowseFilters(userMarks = setOf("favorite"), fromYear = 2020)
        val settings = AppSettings(defaultQuality = PreferredQuality.P720, savedBrowseFilters = filters.copy(userMarks = emptySet()))
        val state = YummyDroidUiState(
            auth = AuthUiState(profile = UserProfile(42, "User", ""), loading = true, error = "expired", captchaRequestNonce = 3),
            animeMark = LoadState.Ready(UserAnimeMark(isFavorite = true)),
            globalSubscriptions = LoadState.Loading,
            profileNotifications = LoadState.Error("old account error"),
            commentSubmission = AnimeCommentSubmission(1, 42, "Pending comment"),
            localWatchHistoryMergePrompt = LocalWatchHistoryMergePrompt(42, 1, emptyList()),
            playbackHistoryLoading = true,
            filters = filters,
        )

        val guest = state.withEndedProfileSession(settings)

        assertEquals(AuthUiState(), guest.auth)
        assertEquals(LoadState.Ready(null), guest.animeMark)
        assertEquals(LoadState.Ready(emptyList()), guest.globalSubscriptions)
        assertEquals(LoadState.Ready(emptyList()), guest.profileNotifications)
        assertNull(guest.localWatchHistoryMergePrompt)
        assertNull(guest.commentSubmission)
        assertFalse(guest.playbackHistoryLoading)
        assertEquals(filters.copy(userMarks = emptySet()), guest.filters)
        assertEquals(settings.savedBrowseFilters, guest.filters)
        assertEquals(PreferredQuality.P720, guest.settings.defaultQuality)
        assertSame(state.playerStream, guest.playerStream)
        assertSame(state.downloadQueue, guest.downloadQueue)
        assertSame(state.historyAnime, guest.historyAnime)
        assertSame(state.playbackHistory, guest.playbackHistory)
        assertEquals(guest, guest.withEndedProfileSession(settings))
    }

    @Test
    fun runtimeRemainsIndependentFromAndroidViewModelLifecycle() {
        assertFalse(ViewModel::class.java.isAssignableFrom(YummyDroidRuntime::class.java))
    }

    @Test
    fun runtimeKeepsActionsExposedByLifecycleFacade() {
        val runtimeMethods = YummyDroidRuntime::class.java.methods.mapTo(mutableSetOf()) { it.name }
        val facadeActions = setOf(
            "refresh",
            "updateSearchQuery",
            "openAnime",
            "playVideo",
            "navigateBack",
            "updateSettings",
            "logout",
        )

        assertTrue(runtimeMethods.containsAll(facadeActions))
    }

    @Test
    fun latestOperationWaitsForCancelledCriticalSectionBeforeStartingReplacement() = runBlocking {
        val coordinator = LatestStateOperationCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        coordinator.launchLatest(this) { lease ->
            firstStarted.complete(Unit)
            withContext(NonCancellable) { releaseFirst.await() }
            events += "first:${lease.isCurrent}"
        }
        firstStarted.await()
        val replacement = coordinator.launchLatest(this) { lease ->
            events += "second:${lease.isCurrent}"
        }
        yield()

        assertTrue(events.isEmpty())
        releaseFirst.complete(Unit)
        replacement.join()
        assertEquals(listOf("first:false", "second:true"), events)
    }

    @Test
    fun serialOperationPreservesInvocationOrderAndOnlyLatestLeasePublishes() = runBlocking {
        val coordinator = SerialStateOperationCoordinator()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        coordinator.launch(this) { lease ->
            releaseFirst.await()
            events += "first:${lease.isCurrent}"
        }
        val second = coordinator.launch(this) { lease ->
            events += "second:${lease.isCurrent}"
        }
        yield()

        assertTrue(events.isEmpty())
        releaseFirst.complete(Unit)
        second.join()
        assertEquals(listOf("first:false", "second:true"), events)
    }

    @Test
    fun serialReplacementWaitsForCancelledNonCancellableOperation() = runBlocking {
        val coordinator = SerialStateOperationCoordinator()
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()

        coordinator.launch(this) {
            firstStarted.complete(Unit)
            withContext(NonCancellable) { releaseFirst.await() }
            events += "first"
        }
        firstStarted.await()
        coordinator.cancel()
        val replacement = coordinator.launch(this) { events += "second" }
        yield()

        assertTrue(events.isEmpty())
        releaseFirst.complete(Unit)
        replacement.join()
        assertEquals(listOf("first", "second"), events)
    }
}
