package me.yummydroid.app

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlaybackProgressStorage
import me.yummydroid.app.data.UserProfile
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class WatchHistoryStartupCacheTest {
    @Test
    fun startupPublishesPersistedHistoryBeforeSiteResponseAndReplacesItAfterwards() = runBlocking {
        val stored = storage()
        val old = watchHistoryProgress(1, 10, updatedAtMs = 10)
        val fresh = watchHistoryProgress(2, 20, updatedAtMs = 20)
        stored.replaceAll(listOf(old))
        // A new storage instance models the next application startup.
        val restarted = PlaybackProgressStorage(RuntimeEnvironment.getApplication())
        val state = state()
        val started = CompletableDeferred<Unit>()
        val response = CompletableDeferred<List<PlaybackProgress>>()
        val runtime = runtime(this, state, restarted, fetch = { _, _ ->
            started.complete(Unit)
            response.await()
        })
        runtime.syncPlaybackHistoryFromSite()
        withTimeout(5_000) { started.await() }
        assertEquals(listOf(1L), state.value.historyAnime.readyListOrEmpty().map { it.id })
        assertTrue(state.value.playbackHistoryLoading)
        response.complete(listOf(fresh))
        coroutineContext.job.children.toList().joinAll()
        assertEquals(listOf(2L), state.value.historyAnime.readyListOrEmpty().map { it.id })
        assertFalse(state.value.playbackHistoryLoading)
        assertEquals(listOf(fresh), PlaybackProgressStorage(RuntimeEnvironment.getApplication()).readAll())
    }

    @Test
    fun successfulEmptySiteHistoryRemovesCachedEntriesInsteadOfResurrectingThem() = runBlocking {
        val stored = storage().apply { replaceAll(listOf(watchHistoryProgress(1, 10, updatedAtMs = 10))) }
        val state = state()
        runtime(this, state, stored, fetch = { _, _ -> emptyList() }).syncPlaybackHistoryFromSite()
        coroutineContext.job.children.toList().joinAll()
        assertTrue(state.value.historyAnime.readyListOrEmpty().isEmpty())
        assertTrue(PlaybackProgressStorage(RuntimeEnvironment.getApplication()).readAll().isEmpty())
        assertFalse(state.value.playbackHistoryLoading)
    }

    @Test
    fun failedRefreshKeepsPersistedHistoryVisible() = runBlocking {
        val progress = watchHistoryProgress(1, 10, updatedAtMs = 10)
        val stored = storage().apply { replaceAll(listOf(progress)) }
        val state = state()
        runtime(this, state, stored, fetch = { _, _ -> error("site unavailable") },
            readCachedAnime = { ids -> ids.associateWith(::watchHistoryAnime) }).syncPlaybackHistoryFromSite()
        coroutineContext.job.children.toList().joinAll()
        assertEquals(listOf(1L), state.value.historyAnime.readyListOrEmpty().map { it.id })
        assertEquals(listOf(progress), stored.readAll())
        assertFalse(state.value.playbackHistoryLoading)
    }

    @Test
    fun offlineStartupUsesCachedMetadataAndProgressWithoutAnyNetworkRequests() = runBlocking {
        val stored = storage().apply { replaceAll(listOf(watchHistoryProgress(1, 10, updatedAtMs = 10))) }
        val state = state(offline = true)
        val cachedAnime = watchHistoryAnime(1).copy(title = "Saved full title", year = 2024, rating = 9.0)
        runtime(this, state, stored,
            fetch = { _, _ -> error("Offline startup must not request history") },
            readCachedAnime = { mapOf(1L to cachedAnime) },
        ).syncPlaybackHistoryFromSite()
        coroutineContext.job.children.toList().joinAll()
        assertEquals(listOf(cachedAnime), state.value.historyAnime.readyListOrEmpty())
        assertFalse(state.value.playbackHistoryLoading)
    }

    @Test
    fun accountSwitchWhileReadingCacheRejectsObsoleteSnapshot() = runBlocking {
        val stored = storage().apply { replaceAll(listOf(watchHistoryProgress(1, 10, updatedAtMs = 10))) }
        val state = state()
        runtime(this, state, stored,
            fetch = { _, _ -> error("An obsolete startup must not refresh history") },
            readCachedAnime = {
                state.value = state.value.copy(auth = AuthUiState(profile = UserProfile(2, "Other", "")))
                emptyMap()
            },
        ).syncPlaybackHistoryFromSite()
        coroutineContext.job.children.toList().joinAll()
        assertTrue(state.value.historyAnime.readyListOrEmpty().isEmpty())
    }

    private fun state(offline: Boolean = false) = MutableStateFlow(YummyDroidUiState(
        auth = AuthUiState(profile = UserProfile(1, "User", "")),
        forcedOfflineMode = offline,
    ))

    private fun storage(): PlaybackProgressStorage {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("yummydroid_playback_progress", Context.MODE_PRIVATE)
            .edit().clear().commit()
        return PlaybackProgressStorage(context)
    }

    private fun runtime(
        scope: CoroutineScope,
        state: MutableStateFlow<YummyDroidUiState>,
        storage: PlaybackProgressStorage,
        fetch: suspend (Int, Int) -> List<PlaybackProgress>,
        readCachedAnime: (Collection<Long>) -> Map<Long, Anime> = { emptyMap() },
    ): PlaybackHistoryStateRuntime {
        val coordinator = WatchHistoryCoordinator(
            readProgress = storage::readAll,
            saveProgressIfNewer = { storage.saveIfNewer(it) },
            replaceProgressHistory = storage::replaceAll,
            readHistoryRevision = storage::readHistoryRevision,
            replaceHistoryIfRevision = storage::replaceAllIfRevision,
            readCachedAnime = readCachedAnime,
            saveCachedAnime = {},
            fetchHistoryPage = fetch,
            uploadProgress = { error("A cached snapshot must not upload progress") },
            fetchAnimeSummary = { error("Missing summaries must use persisted progress") },
            ioDispatcher = Dispatchers.Unconfined,
        )
        return PlaybackHistoryStateRuntime(
            scope = scope,
            uiState = state,
            playbackProgressStorage = storage,
            watchHistoryCoordinator = coordinator,
            playbackProgressOperations = KeyedLatestStateOperationCoordinator(),
            playbackHistoryOperations = LatestStateOperationCoordinator(),
            profilePlaybackHistoryCache = ProfilePlaybackHistoryCache(),
            saveProgressToSite = { error("A cached snapshot must not upload progress") },
            requestCaptchaRetry = { _, _ -> false },
            isActiveProfile = { !state.value.forcedOfflineMode && state.value.auth.profile?.id == it },
        )
    }
}
