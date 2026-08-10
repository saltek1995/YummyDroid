package me.yummydroid.app.data

import kotlinx.serialization.Serializable

@Serializable
data class OfflineVideoFile(
    val playbackUrl: String,
    val mimeType: String? = null,
    val bytes: Long = 0L,
    val qualityTitle: String = "",
    val voiceTitle: String = "",
    val player: String = "",
    val createdAtMs: Long = 0L,
)

@Serializable
data class SourceQuality(
    val height: Int? = null,
    val bitrate: Int = 0,
) {
    val title: String
        get() = height.validVideoQualityHeight()?.let { "${it}p" }.orEmpty()
}

fun OfflineVideoFile.qualityHeight(): Int {
    return Regex("""(\d{3,4})p""", RegexOption.IGNORE_CASE)
        .find(qualityTitle)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: 0
}

@Serializable
data class VideoVariant(
    val id: Long,
    val animeId: Long,
    val player: String,
    val playerId: Long = 0L,
    val dubbing: String,
    val episode: String,
    val url: String,
    val index: Int,
    val durationSeconds: Int?,
    val views: Long,
    val skipSegments: List<VideoSkipSegment> = emptyList(),
    val previewUrl: String = "",
    val localPlaybackUrl: String = "",
    val localMimeType: String? = null,
    val localBytes: Long = 0L,
    val localFiles: List<OfflineVideoFile> = emptyList(),
    val sourceQualities: List<SourceQuality> = emptyList(),
    val subscribed: Boolean = false,
) {
    val groupKey: String = "$player|$dubbing"
    val groupTitle: String = listOf(player.cleanVideoLabel("Player"), dubbing.cleanVideoLabel("Voice"))
        .filter { it.isNotBlank() }
        .joinToString(" \u2022 ")

    val episodeTitle: String
        get() = if (episode.isBlank()) "Episode" else "Episode $episode"

    val isOfflineAvailable: Boolean
        get() = localPlaybackUrl.isNotBlank() || localFiles.any { it.playbackUrl.isNotBlank() }

    val offlineFiles: List<OfflineVideoFile>
        get() = localFiles.filter { it.playbackUrl.isNotBlank() }.ifEmpty(::legacyOfflineFiles)

    private fun legacyOfflineFiles(): List<OfflineVideoFile> {
        if (localPlaybackUrl.isBlank()) return emptyList()
        return listOf(
            OfflineVideoFile(
                playbackUrl = localPlaybackUrl,
                mimeType = localMimeType,
                bytes = localBytes,
                qualityTitle = "",
                voiceTitle = dubbing.cleanVideoLabel("Voice")
                    .ifBlank { player.cleanVideoLabel("Player") },
                player = player,
            ),
        )
    }
}

data class ResolvedPlayback(
    val video: VideoVariant,
    val stream: ResolvedVideoStream,
)

data class DownloadProgressInfo(
    val fraction: Float,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val bytesPerSecond: Long = 0L,
    val qualityTitle: String = "",
    val voiceTitle: String = "",
)

private fun String.cleanVideoLabel(prefix: String): String {
    return trim().removePrefix(prefix).trim()
}
