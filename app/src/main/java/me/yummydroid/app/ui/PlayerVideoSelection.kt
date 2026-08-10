package me.yummydroid.app.ui

import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.isSubscribedTo
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant

internal fun List<VideoVariant>.sortedForPlayer(): List<VideoVariant> {
    return sortedWith(
        compareBy<VideoVariant> { it.episodeOrderValue() ?: Double.MAX_VALUE }
            .thenBy { it.index.takeIf { index -> index > 0 } ?: Int.MAX_VALUE }
            .thenBy { if (it.isOfflineAvailable) 0 else 1 }
            .thenBy { it.id },
    )
}

internal fun List<VideoVariant>.sortedForPlayer(
    preferredGroupKey: String?,
    preferredVoiceKey: String? = matchingVoiceKeyForGroup(preferredGroupKey),
): List<VideoVariant> {
    val voiceKey = preferredVoiceKey?.takeIf { it.isNotBlank() }
    val scopedVideos = voiceKey
        ?.let { key -> filter { it.matchingVoiceKey == key } }
        ?.takeIf { it.isNotEmpty() }
        ?: this
    return scopedVideos.groupBy { it.matchingEpisodeKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.isOfflineAvailable) 0 else 1 }
                    .thenBy { if (it.groupKey == preferredGroupKey) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedForPlayer()
}

internal fun List<VideoVariant>.matchingVoiceKeyForGroup(groupKey: String?): String? {
    return groupKey
        ?.takeIf { it.isNotBlank() }
        ?.let { key -> firstOrNull { it.groupKey == key }?.matchingVoiceKey }
        ?.takeIf { it.isNotBlank() }
}

internal fun List<VideoSubscription>.isVideoVoiceSubscribed(video: VideoVariant): Boolean {
    return isSubscribedTo(video)
}

internal fun VideoVariant.playbackSourceLabel(isLocalPlayback: Boolean = localPlaybackUrl.isNotBlank()): String {
    return if (isLocalPlayback) {
        "Local"
    } else {
        player.cleanVideoSourceLabel().ifBlank { player }.ifBlank { "HLS" }
    }
}
