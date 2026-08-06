package me.yummydroid.app

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.siteDefaultVideo
import me.yummydroid.app.data.toAnimeSummary

internal data class LoadedAnimeDetails(
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
    val offlineMode: Boolean,
    val progress: PlaybackProgress?,
    val history: List<PlaybackProgress>,
    val selectedVideoGroup: String?,
)

internal class AnimeDetailsLoadCoordinator(
    private val fetchAnimeWithVideos: suspend (Long) -> Pair<AnimeDetails, List<VideoVariant>>,
    private val isOfflineFallbackActive: () -> Boolean,
    private val readProgress: (Long) -> PlaybackProgress?,
    private val readHistory: (Long) -> List<PlaybackProgress>,
    private val resolveEffectiveRating: suspend (
        animeId: Long,
        remoteRating: Int?,
        trustRemote: Boolean,
    ) -> Int?,
    private val saveAnimeSummary: (Anime) -> Unit,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun load(
        animeId: Long,
        isAuthenticated: () -> Boolean,
    ): LoadedAnimeDetails {
        val loaded = withContext(ioDispatcher) {
            val (details, videos) = fetchAnimeWithVideos(animeId)
            val offlineMode = isOfflineFallbackActive()
            val progress = readProgress(animeId)
            LoadedAnimeDetails(
                details = details,
                videos = videos,
                offlineMode = offlineMode,
                progress = progress,
                history = readHistory(animeId),
                selectedVideoGroup = selectInitialVideoGroup(
                    videos = videos,
                    progress = progress,
                    offlineMode = offlineMode,
                ),
            )
        }
        val effectiveRating = resolveEffectiveRating(
            animeId,
            loaded.details.userRating,
            isAuthenticated() && !loaded.offlineMode,
        )
        return loaded.copy(details = loaded.details.copy(userRating = effectiveRating))
    }

    suspend fun cache(details: AnimeDetails) {
        withContext(ioDispatcher) {
            saveAnimeSummary(details.toAnimeSummary())
        }
    }
}

internal fun selectInitialVideoGroup(
    videos: List<VideoVariant>,
    progress: PlaybackProgress?,
    offlineMode: Boolean,
): String? {
    val playableVideos = if (offlineMode) {
        videos.filter(VideoVariant::isOfflineAvailable)
    } else {
        videos
    }
    val progressGroup = progress?.groupKey
        ?.takeIf { groupKey -> playableVideos.any { it.groupKey == groupKey } }
    return progressGroup
        ?: playableVideos.siteDefaultVideo()?.groupKey
        ?: videos.siteDefaultVideo()?.groupKey
}
