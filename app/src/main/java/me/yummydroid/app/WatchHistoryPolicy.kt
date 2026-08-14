package me.yummydroid.app

import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.progressSyncKey
import me.yummydroid.app.data.toAnimeSummary

// WatchHistoryPolicy
internal fun watchHistoryRefreshPlan(
    force: Boolean,
    hasReadyHistory: Boolean,
    canUseRemote: Boolean,
    loadActive: Boolean,
    cacheInitialized: Boolean,
    remoteRefreshDue: Boolean,
): WatchHistoryRefreshPlan? {
    if (loadActive && !force) return null
    if (force) return WatchHistoryRefreshPlan(showCachedSnapshot = true)

    val cachedSnapshotMissing = !cacheInitialized || !hasReadyHistory
    if (cachedSnapshotMissing) return WatchHistoryRefreshPlan(showCachedSnapshot = true)
    if (canUseRemote && remoteRefreshDue) return WatchHistoryRefreshPlan(showCachedSnapshot = false)
    return null
}

internal fun List<PlaybackProgress>.latestHistoryByAnime(): List<PlaybackProgress> {
    return groupBy { it.animeId }
        .values
        .mapNotNull { entries -> entries.maxByOrNull { it.updatedAtMs } }
        .sortedByDescending { it.updatedAtMs }
}

internal fun supplementalLocalHistoryEntries(
    localHistory: List<PlaybackProgress>,
    remoteHistory: List<PlaybackProgress>,
): List<PlaybackProgress> {
    val remoteByEpisode = remoteHistory.bestProgressBy(PlaybackProgress::progressSyncKey)
    val remoteByAnime = remoteHistory.bestProgressBy { it.animeId.toString() }
    return localHistory.filter { local ->
        local.videoId > 0L &&
            local.canSupplementEpisode(remoteByEpisode[local.progressSyncKey()]) &&
            local.canAdvanceAnime(remoteByAnime[local.animeId.toString()])
    }
}

internal fun watchHistorySyncAllowsLocalMergePrompt(
    allowLocalHistoryMergePrompt: Boolean,
    mergeLocalHistory: Boolean,
): Boolean = allowLocalHistoryMergePrompt && !mergeLocalHistory

private fun List<PlaybackProgress>.bestProgressBy(
    key: (PlaybackProgress) -> String,
): Map<String, PlaybackProgress> {
    return groupBy(key).mapValues { (_, entries) ->
        entries.maxWithOrNull(progressAdvanceComparator)
            ?: entries.maxByOrNull { it.updatedAtMs }
            ?: error("Progress group is empty")
    }
}

private val progressAdvanceComparator = compareBy<PlaybackProgress>(
    { progress -> if (progress.episodeNumberOrNull() != null) 1 else 0 },
    { progress -> progress.episodeNumberOrNull() ?: Double.NEGATIVE_INFINITY },
    { progress -> progress.positionMs },
    { progress -> progress.updatedAtMs },
)

private fun PlaybackProgress.canSupplementEpisode(remote: PlaybackProgress?): Boolean {
    if (remote == null) return true
    return positionMs > remote.positionMs ||
        (positionMs == remote.positionMs && updatedAtMs > remote.updatedAtMs)
}

private fun PlaybackProgress.canAdvanceAnime(remote: PlaybackProgress?): Boolean {
    if (remote == null) return true
    val localEpisode = episodeNumberOrNull()
    val remoteEpisode = remote.episodeNumberOrNull()
    return when {
        localEpisode != null && remoteEpisode != null && localEpisode != remoteEpisode -> {
            localEpisode > remoteEpisode
        }
        progressSyncKey() == remote.progressSyncKey() || localEpisode != null && localEpisode == remoteEpisode -> {
            positionMs > remote.positionMs ||
                (positionMs == remote.positionMs && updatedAtMs > remote.updatedAtMs)
        }
        else -> false
    }
}

private fun PlaybackProgress.episodeNumberOrNull(): Double? = episode.trim().toDoubleOrNull()

internal fun selectHistoryProgress(
    localHistory: List<PlaybackProgress>,
    remoteHistory: List<PlaybackProgress>,
    remoteFailed: Boolean,
    canUseRemote: Boolean,
): List<PlaybackProgress>? {
    return when {
        canUseRemote && !remoteFailed -> remoteHistory
        localHistory.isNotEmpty() -> localHistory
        remoteHistory.isNotEmpty() -> remoteHistory
        remoteFailed && canUseRemote -> null
        else -> emptyList()
    }
}

internal suspend fun collectWatchHistoryPages(
    pageSize: Int = WATCH_HISTORY_PAGE_SIZE,
    maxOffset: Int = WATCH_HISTORY_MAX_OFFSET,
    fetchPage: suspend (limit: Int, offset: Int) -> List<PlaybackProgress>,
): List<PlaybackProgress> {
    val history = mutableListOf<PlaybackProgress>()
    val seenKeys = mutableSetOf<String>()
    var offset = 0
    var pagesWithoutNewEntries = 0
    while (offset <= maxOffset) {
        val pageEntries = fetchPage(pageSize, offset)
        if (pageEntries.isEmpty()) return history

        val uniqueEntries = pageEntries.filter { seenKeys.add(it.progressSyncKey()) }
        if (uniqueEntries.isNotEmpty()) {
            history += uniqueEntries
            pagesWithoutNewEntries = 0
        } else {
            pagesWithoutNewEntries += 1
            if (pagesWithoutNewEntries >= MAX_HISTORY_PAGES_WITHOUT_NEW_ENTRIES) return history
        }
        offset += pageSize
    }
    return history
}

internal fun List<PlaybackProgress>.toAnimeSummaries(cachedById: Map<Long, Anime>): List<Anime> {
    return map { progress -> cachedById[progress.animeId] ?: progress.toAnimeSummary() }
        .distinctBy { it.id }
}

private const val WATCH_HISTORY_PAGE_SIZE = 100
private const val MAX_HISTORY_PAGES_WITHOUT_NEW_ENTRIES = 2
