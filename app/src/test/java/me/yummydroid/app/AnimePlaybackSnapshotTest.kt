package me.yummydroid.app

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.PlaybackSelection
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoVariant
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AnimePlaybackSnapshotTest {
    @Test
    fun cachedHistoryAndSelectionArePublishedIncludingEmptyHistoryAfterReset() = runBlocking {
        val storage = storage()
        val history = progress(updatedAtMs = 1L)
        val selection = selection()
        storage.save(history)
        storage.saveSelection(selection)
        val loader = AnimePlaybackSnapshotLoader(storage, Dispatchers.Unconfined)
        val snapshots = mutableListOf<AnimePlaybackSnapshot>()

        loader.publish(10L, isCurrent = { true }, onSnapshot = snapshots::add)
        storage.clear()
        loader.publish(10L, isCurrent = { true }, onSnapshot = snapshots::add)

        assertEquals(
            listOf(
                AnimePlaybackSnapshot(listOf(history), selection),
                AnimePlaybackSnapshot(emptyList(), selection),
            ),
            snapshots,
        )
    }

    @Test
    fun revisionChangeDuringReadRereadsBeforePublishing() = runBlocking {
        val storage = storage()
        val stale = progress(updatedAtMs = 1L)
        val current = stale.copy(positionMs = 1_500L, updatedAtMs = 2L)
        storage.save(stale)
        val dispatcher = OnceDispatcher { storage.save(current) }
        val loader = AnimePlaybackSnapshotLoader(storage, dispatcher)
        val snapshots = mutableListOf<AnimePlaybackSnapshot>()

        loader.publish(10L, isCurrent = { true }, onSnapshot = snapshots::add)

        assertEquals(2, dispatcher.dispatchCalls)
        assertEquals(listOf(AnimePlaybackSnapshot(listOf(current), null)), snapshots)
    }

    @Test
    fun staleRequestDoesNotPublishSnapshot() = runBlocking {
        val storage = storage()
        storage.save(progress(updatedAtMs = 1L))
        var current = true
        val loader = AnimePlaybackSnapshotLoader(storage, OnceDispatcher { current = false })
        val snapshots = mutableListOf<AnimePlaybackSnapshot>()

        loader.publish(10L, isCurrent = { current }, onSnapshot = snapshots::add)

        assertEquals(emptyList(), snapshots)
    }

    @Test
    fun globalHistoryRefreshPopulatesCardCacheWithoutCardNetworkReads() = runBlocking {
        val storage = storage()
        var remoteHistory = listOf(
            watchHistoryProgress(10L, 100L, positionMs = 1_000L, updatedAtMs = 1L),
            watchHistoryProgress(20L, 200L, positionMs = 2_000L, updatedAtMs = 2L),
        )
        var historyRequests = 0
        val coordinator = coordinator(storage, fetchHistoryPage = { _, offset ->
            historyRequests += 1
            if (offset == 0) remoteHistory else emptyList()
        })
        val loader = AnimePlaybackSnapshotLoader(storage, Dispatchers.Unconfined)

        coordinator.load(WatchHistoryRefreshPlan(false), { true }, {}, { false })
        val afterInitialRefresh = historyRequests
        val initialCards = mutableListOf<AnimePlaybackSnapshot>()
        loader.publish(10L, { true }, initialCards::add)
        loader.publish(20L, { true }, initialCards::add)
        loader.publish(10L, { true }, initialCards::add)

        assertEquals(2, afterInitialRefresh)
        assertEquals(afterInitialRefresh, historyRequests)
        assertEquals(listOf(1_000L, 2_000L, 1_000L), initialCards.map { it.progress?.positionMs })

        remoteHistory = listOf(
            watchHistoryProgress(10L, 100L, positionMs = 3_000L, updatedAtMs = 3L),
        )
        coordinator.load(WatchHistoryRefreshPlan(false), { true }, {}, { false })
        val afterChangedRefresh = historyRequests
        val refreshedCards = mutableListOf<AnimePlaybackSnapshot>()
        loader.publish(10L, { true }, refreshedCards::add)
        loader.publish(20L, { true }, refreshedCards::add)

        assertEquals(4, afterChangedRefresh)
        assertEquals(afterChangedRefresh, historyRequests)
        assertEquals(3_000L, refreshedCards[0].progress?.positionMs)
        assertEquals(emptyList(), refreshedCards[1].history)
    }

    @Test
    fun historyUpdatedCallbackSeesPersistedProgressBeforeSummaryFetchCompletes() = runBlocking {
        val storage = storage()
        val remote = watchHistoryProgress(10L, 100L, positionMs = 3_000L, updatedAtMs = 3L)
        val summaryStarted = CompletableDeferred<Unit>()
        val releaseSummary = CompletableDeferred<Unit>()
        var callbackHistory: List<PlaybackProgress>? = null
        val coordinator = coordinator(
            storage = storage,
            fetchHistoryPage = { _, offset -> if (offset == 0) listOf(remote) else emptyList() },
            readCachedAnime = { emptyMap() },
            fetchAnimeSummary = {
                summaryStarted.complete(Unit)
                releaseSummary.await()
                watchHistoryAnime(it)
            },
        )
        val load = async(start = CoroutineStart.UNDISPATCHED) {
            coordinator.load(
                WatchHistoryRefreshPlan(false),
                { true },
                {},
                { false },
                onPlaybackHistoryUpdated = { callbackHistory = storage.readAnimeHistory(10L) },
            )
        }

        try {
            summaryStarted.await()
            assertEquals(listOf(remote), callbackHistory)
        } finally {
            releaseSummary.complete(Unit)
        }
        load.await()
        Unit
    }

    @Test
    fun globalSyncRestoresRemoteVoiceWhenHistoryArrivesAfterCardOpens() = runBlocking {
        assertGlobalSyncSelection()
    }

    @Test
    fun globalSyncPreservesManualVoiceSelectedWhileRemoteHistoryIsPending() = runBlocking {
        assertGlobalSyncSelection(selectManually = true)
    }

    @Test
    fun globalSyncDoesNotReplaceActivePlayerProgressOrVoice() = runBlocking {
        assertGlobalSyncSelection(openPlayer = true)
    }

    private suspend fun CoroutineScope.assertGlobalSyncSelection(
        selectManually: Boolean = false,
        openPlayer: Boolean = false,
    ) {
        val storage = storage()
        val default = VideoVariant(
            id = 20L, animeId = 10L, player = "CVH", dubbing = "Default", episode = "1",
            url = "https://example.test/default", index = 1, durationSeconds = null, views = 0L,
        )
        val remembered = default.copy(id = 21L, dubbing = "Remembered")
        val manual = default.copy(id = 22L, dubbing = "Manual")
        val remote = progress(2L).copy(videoId = remembered.id, groupKey = remembered.groupKey)
        val remoteStarted = CompletableDeferred<Unit>()
        val releaseRemote = CompletableDeferred<Unit>()
        val state = MutableStateFlow(YummyDroidUiState(
            route = AppRoute.Details(10L),
            auth = AuthUiState(profile = UserProfile(42L, "User", "")),
            details = LoadState.Ready(AnimeDetails(
                id = 10L, title = "Anime", otherTitles = emptyList(), description = "", posterUrl = "",
                backdropUrl = null, year = null, rating = null, views = 0L, status = "", type = "",
                minAge = "", genreTags = emptyList(), genres = emptyList(), episodeSummary = "",
                episodeAired = 0, episodeCount = 0, nextEpisodeText = "", durationSeconds = 0,
                ratingDetails = RatingDetails(), studios = emptyList(), creators = emptyList(), original = "",
                commentsCount = 0, listsCount = 0, translations = emptyList(), relatedAnime = emptyList(),
                screenshots = emptyList(), blockedIn = emptyList(),
            )),
            videos = LoadState.Ready(listOf(default, remembered, manual)),
            selectedVideoGroup = default.groupKey,
        ))
        val runtime = PlaybackHistoryStateRuntime(
            scope = this,
            uiState = state,
            playbackProgressStorage = storage,
            watchHistoryCoordinator = coordinator(storage, fetchHistoryPage = { _, offset ->
                if (offset == 0) {
                    remoteStarted.complete(Unit)
                    releaseRemote.await()
                    listOf(remote)
                } else emptyList()
            }),
            playbackProgressOperations = KeyedLatestStateOperationCoordinator(),
            playbackHistoryOperations = LatestStateOperationCoordinator(),
            profilePlaybackHistoryCache = ProfilePlaybackHistoryCache(),
            saveProgressToSite = { error("A remote snapshot must not upload progress") },
            requestCaptchaRetry = { _, _ -> false },
            isActiveProfile = { it == state.value.auth.profile?.id },
        )

        runtime.syncPlaybackHistoryFromSite()
        remoteStarted.await()
        if (selectManually) {
            // The public group-selection action persists this before updating the card.
            storage.saveSelection(manual.toPlaybackSelection(updatedAtMs = 3L))
            state.value = state.value.copy(selectedVideoGroup = manual.groupKey)
        }
        if (openPlayer) state.value = state.value.copy(route = AppRoute.Player(default, "Anime"))
        releaseRemote.complete(Unit)
        coroutineContext.job.children.toList().joinAll()

        assertEquals(listOf(remote), storage.readAnimeHistory(10L))
        assertEquals(
            when {
                selectManually -> manual.groupKey
                openPlayer -> default.groupKey
                else -> remembered.groupKey
            },
            state.value.selectedVideoGroup,
        )
        assertEquals(if (openPlayer) null else remote, state.value.playbackProgress)
        assertEquals(if (openPlayer) emptyList() else listOf(remote), state.value.playbackHistory)
    }

    private fun coordinator(
        storage: PlaybackProgressStorage,
        fetchHistoryPage: suspend (Int, Int) -> List<PlaybackProgress>,
        readCachedAnime: (Collection<Long>) -> Map<Long, me.yummydroid.app.data.Anime> = { ids ->
            ids.associateWith(::watchHistoryAnime)
        },
        fetchAnimeSummary: suspend (Long) -> me.yummydroid.app.data.Anime = ::watchHistoryAnime,
    ): WatchHistoryCoordinator {
        return WatchHistoryCoordinator(
            readProgress = storage::readAll,
            saveProgressIfNewer = { storage.saveIfNewer(it) },
            replaceProgressHistory = storage::replaceAll,
            readHistoryRevision = storage::readHistoryRevision,
            replaceHistoryIfRevision = storage::replaceAllIfRevision,
            readCachedAnime = readCachedAnime,
            saveCachedAnime = {},
            fetchHistoryPage = fetchHistoryPage,
            uploadProgress = { true },
            fetchAnimeSummary = fetchAnimeSummary,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    private fun storage(): PlaybackProgressStorage {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("yummydroid_playback_progress", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        return PlaybackProgressStorage(context)
    }

    private fun progress(updatedAtMs: Long): PlaybackProgress {
        return PlaybackProgress(
            animeId = 10L,
            videoId = 20L,
            animeTitle = "Anime",
            posterUrl = "",
            groupKey = "CVH|Voice",
            episode = "1",
            positionMs = 1_000L,
            durationMs = 2_000L,
            updatedAtMs = updatedAtMs,
        )
    }

    private fun selection(): PlaybackSelection {
        return PlaybackSelection(
            animeId = 10L,
            groupKey = "CVH|Voice",
            voiceKey = "voice",
            sourceKey = "cvh|test",
            updatedAtMs = 1L,
        )
    }

    private class OnceDispatcher(
        private val beforeFirstDispatch: () -> Unit,
    ) : CoroutineDispatcher() {
        var dispatchCalls = 0
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            dispatchCalls += 1
            if (dispatchCalls == 1) beforeFirstDispatch()
            block.run()
        }
    }
}
