package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.yummydroid.app.data.UserAnimeMark

/** Main-thread confined; stores only membership, never full anime or poster data. */
internal class AnimeCardMarksCoordinator(
    private val currentState: () -> YummyDroidUiState,
    private val updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    private val fetchMarks: suspend (Long) -> Map<Long, UserAnimeMark>,
    private val onLoadFailure: (Throwable) -> Unit = {},
    private val readCachedMarks: suspend (Long) -> Map<Long, UserAnimeMark>? = { null },
    private val saveCachedMarks: (Long, Map<Long, UserAnimeMark>) -> Unit = { _, _ -> },
) {
    private var account: Pair<Long?, Long>? = null
    private var loaded = false
    private var cacheRestored = false
    private var snapshotReady = false
    private var baselineComplete = false
    private var baseline: Map<Long, UserAnimeMark> = emptyMap()
    // Empty marks are tombstones: a removed mark must not reappear from an older bulk response.
    private val overrides = mutableMapOf<Long, UserAnimeMark>()
    private val confirmedOverrides = mutableMapOf<Long, UserAnimeMark>()

    private fun synchronizeAccount(): Pair<Long?, Long> {
        val state = currentState()
        val key = state.auth.profile?.id to state.contentSessionRevision
        if (account != key) {
            account = key
            loaded = false
            cacheRestored = false
            snapshotReady = false
            baselineComplete = false
            baseline = emptyMap()
            overrides.clear()
            confirmedOverrides.clear()
            publish(key)
        }
        return key
    }

    suspend fun synchronize() {
        val key = synchronizeAccount()
        val profileId = key.first
        if (profileId == null) {
            snapshotReady = !currentState().auth.loading
            publish(key)
            return
        }
        if (!cacheRestored) {
            val cached = try {
                readCachedMarks(profileId)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                onLoadFailure(failure)
                null
            }
            currentCoroutineContext().ensureActive()
            if (!isCurrent(key)) return
            baseline = cached.orEmpty()
            baselineComplete = cached != null
            cacheRestored = true
            snapshotReady = cached != null
            publish(key)
        }
        if (currentState().forcedOfflineMode) {
            snapshotReady = true
            publish(key)
            return
        }
        if (loaded || currentState().auth.loading) return
        try {
            val marks = fetchMarks(profileId)
            currentCoroutineContext().ensureActive()
            if (!isCurrent(key)) return
            baseline = marks
            baselineComplete = true
            loaded = true
            snapshotReady = true
            publish(key)
            persist(key)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            if (isCurrent(key)) {
                snapshotReady = true
                publish(key)
                onLoadFailure(failure)
            }
        }
    }

    fun recordMark(
        profileId: Long,
        animeId: Long,
        mark: LoadState<UserAnimeMark?>,
        confirmed: LoadState<UserAnimeMark?> = mark,
    ) {
        if (currentState().auth.profile?.id != profileId) return
        val key = synchronizeAccount()
        if (confirmed is LoadState.Ready) confirmedOverrides[animeId] = confirmed.data ?: UserAnimeMark()
        when (mark) {
            is LoadState.Ready -> overrides[animeId] = mark.data ?: UserAnimeMark()
            is LoadState.Error -> {
                val previous = confirmedOverrides[animeId]
                if (previous != null) overrides[animeId] = previous else overrides.remove(animeId)
            }
            LoadState.Loading -> return
        }
        publish(key)
        if (confirmed is LoadState.Ready) persist(key)
    }

    private fun isCurrent(key: Pair<Long?, Long>): Boolean {
        val state = currentState()
        return account == key && state.auth.profile?.id == key.first && state.contentSessionRevision == key.second
    }

    private fun publish(key: Pair<Long?, Long>) {
        val marks = baseline.toMutableMap().apply {
            for ((id, mark) in overrides) {
                if (mark.list == null && !mark.isFavorite) remove(id) else put(id, mark)
            }
        }.toMap()
        updateState { if (isCurrent(key)) it.copy(cardAnimeMarks = marks, cardAnimeMarksReady = snapshotReady) else it }
    }

    private fun persist(key: Pair<Long?, Long>) {
        val profileId = key.first ?: return
        if (!cacheRestored || !baselineComplete || !isCurrent(key)) return
        val marks = baseline.toMutableMap().apply {
            for ((id, mark) in confirmedOverrides) {
                if (mark.list == null && !mark.isFavorite) remove(id) else put(id, mark)
            }
        }.toMap()
        saveCachedMarks(profileId, marks)
    }
}

internal suspend fun observeAnimeCardMarks(
    state: StateFlow<YummyDroidUiState>,
    coordinator: AnimeCardMarksCoordinator,
) {
    state.map { (it.auth.profile?.id to it.contentSessionRevision) to (it.forcedOfflineMode to it.auth.loading) }
        .distinctUntilChanged().collectLatest { coordinator.synchronize() }
}

/** Presentation waits for the initial mark snapshot; the original content keeps loading in parallel. */
internal fun YummyDroidUiState.withReadyCardMarks(): YummyDroidUiState {
    if (cardAnimeMarksReady || (auth.profile == null && !auth.loading)) return this
    return copy(
        featured = LoadState.Loading,
        searchResults = LoadState.Loading,
        historyAnime = LoadState.Loading,
        schedule = LoadState.Loading,
        details = LoadState.Loading,
    )
}
