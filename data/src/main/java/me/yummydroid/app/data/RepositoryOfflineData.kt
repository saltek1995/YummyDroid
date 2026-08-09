package me.yummydroid.app.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun YummyAnimeRepository.repositoryOfflineAnime(): List<OfflineAnimeEntry> =
    withContext(Dispatchers.IO) {
        offlineStorage?.readAll().orEmpty()
    }

internal suspend fun YummyAnimeRepository.repositoryDeleteOfflineVideo(
    animeId: Long,
    videoId: Long,
    playbackUrl: String?,
) = withContext(Dispatchers.IO) {
    offlineStorage?.deleteVideo(animeId, videoId, playbackUrl)
}

internal suspend fun YummyAnimeRepository.repositoryDeleteOfflineAnime(
    animeId: Long,
) = withContext(Dispatchers.IO) {
    offlineStorage?.deleteAnime(animeId)
}

internal suspend fun YummyAnimeRepository.repositoryClearAppContentCache(
    playbackProgressStorage: PlaybackProgressStorage,
) = withContext(Dispatchers.IO) {
    offlineStorage?.clearOfflineCache()
    playbackProgressStorage.clear()
    contentCache?.clear()
    sourceQualityCache?.clear()
}

internal suspend fun YummyAnimeRepository.repositoryDownloadVideo(
    details: AnimeDetails,
    videos: List<VideoVariant>,
    video: VideoVariant,
    preferredQuality: PreferredQuality,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    deletePartialOnCancel: () -> Boolean,
): VideoVariant = withContext(Dispatchers.IO) {
    val storage = offlineStorage ?: error("Offline storage is unavailable")
    check(!isCancelled()) { "Download cancelled" }
    val playbacks = repositoryResolveDownloadPlaybacks(
        requested = video,
        videos = videos,
        preferredQuality = preferredQuality,
    )
    val failures = mutableListOf<String>()

    for (playback in playbacks) {
        val stream = playback.stream
        val target = runCatching {
            when {
                stream.isHlsStream() -> this@repositoryDownloadVideo.downloadHlsAsSingleVideoFile(
                    storage = storage,
                    video = playback.video,
                    stream = stream,
                    preferredQuality = preferredQuality,
                    onProgress = onProgress,
                    isCancelled = isCancelled,
                    deletePartialOnCancel = deletePartialOnCancel,
                    bandwidthLimiter = downloadBandwidthLimiter,
                )
                stream.isDashStream() -> throw IOException(
                    "DASH offline downloading is not available for this source yet",
                )
                else -> this@repositoryDownloadVideo.downloadDirectVideo(
                    storage = storage,
                    video = playback.video,
                    stream = stream,
                    preferredQuality = preferredQuality,
                    onProgress = onProgress,
                    isCancelled = isCancelled,
                    deletePartialOnCancel = deletePartialOnCancel,
                    bandwidthLimiter = downloadBandwidthLimiter,
                )
            }
        }.getOrElse { throwable ->
            throwable.throwIfCancellation()
            if (isCancelled() || throwable.message.equals("Download cancelled", ignoreCase = true)) {
                throw IllegalStateException("Download cancelled", throwable)
            }
            failures += downloadFailureDescription(playback.video, throwable)
            null
        } ?: continue

        if (isCancelled()) {
            if (deletePartialOnCancel()) target.delete()
            throw IllegalStateException("Download cancelled")
        }
        storage.markVideoDownloaded(
            details = details,
            videos = videos,
            video = playback.video,
            file = target,
            mimeType = target.name.mimeTypeFromFileName() ?: stream.mimeType,
        )
        val downloaded = storage.read(details.id)
            ?.videos
            ?.firstOrNull { stored ->
                stored.matchesDownloadedPlayback(playback.video, preferredQuality)
            }
            ?: throw IOException("Downloaded file was not confirmed by the offline index")
        val downloadedBytes = target.length().coerceAtLeast(0L)
        onProgress(
            DownloadProgressInfo(
                fraction = 1f,
                downloadedBytes = downloadedBytes,
                totalBytes = downloadedBytes,
                bytesPerSecond = 0L,
                qualityTitle = target.downloadQualityTitle(),
                voiceTitle = playback.video.downloadVoiceTitle(),
            ),
        )
        return@withContext downloaded
    }

    throw IOException(downloadFailureMessage(failures))
}

internal fun downloadFailureMessage(failures: List<String>): String {
    val detailsText = failures.take(3).joinToString("; ").takeIf { it.isNotBlank() }
    return buildString {
        append("Could not download episode")
        if (detailsText != null) append(": ").append(detailsText)
    }
}

private fun downloadFailureDescription(video: VideoVariant, throwable: Throwable): String {
    val sourceTitle = video.groupTitle.ifBlank { video.player }
    return "$sourceTitle: ${throwable.message.orEmpty()}"
}

private fun VideoVariant.matchesDownloadedPlayback(
    playbackVideo: VideoVariant,
    preferredQuality: PreferredQuality,
): Boolean {
    val matchesVoice = id == playbackVideo.id ||
        downloadVoiceSlotKey == playbackVideo.downloadVoiceSlotKey
    return matchesVoice && offlineFiles.any { file ->
        file.matchesPreferredQuality(preferredQuality) && file.bytes > 0L
    }
}
