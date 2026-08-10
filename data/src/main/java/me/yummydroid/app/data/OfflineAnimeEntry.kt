package me.yummydroid.app.data

import kotlinx.serialization.Serializable

internal const val MIN_COMPLETED_VIDEO_BYTES = 256L * 1024L

@Serializable
data class OfflineAnimeEntry(
    val anime: Anime,
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
    val updatedAtMs: Long,
) {
    val downloadedVariants: List<VideoVariant>
        get() = videos.filter { video ->
            video.offlineFiles.any { it.bytes >= MIN_COMPLETED_VIDEO_BYTES }
        }.distinctBy { video ->
            video.offlineFiles
                .map { it.playbackUrl }
                .filter { it.isNotBlank() }
                .sorted()
                .joinToString("|")
                .ifBlank { "${video.animeId}|${video.episode}|${video.dubbing}|${video.player}" }
        }

    val downloadedVideos: List<VideoVariant>
        get() = downloadedVariants
            .sortedWith(compareBy<VideoVariant> { it.storageEpisodeSortKey() }.thenBy { it.index })
            .distinctBy { it.storageEpisodeKey() }

    val totalBytes: Long
        get() = videos
            .flatMap { it.offlineFiles }
            .filter { it.bytes >= MIN_COMPLETED_VIDEO_BYTES }
            .distinctBy { it.playbackUrl }
            .sumOf { it.bytes.coerceAtLeast(0L) }
}

internal fun VideoVariant.storageEpisodeKey(): String {
    return matchingEpisodeKey.takeIf { it.isNotBlank() }
        ?: episode.trim().takeIf { it.isNotBlank() }
        ?: index.takeIf { it > 0 }?.let { "index:$it" }
        ?: "video:$id"
}

private fun VideoVariant.storageEpisodeSortKey(): Double {
    return storageEpisodeKey().toDoubleOrNull()
        ?: index.takeIf { it > 0 }?.toDouble()
        ?: Double.MAX_VALUE
}
