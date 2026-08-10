package me.yummydroid.app

import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.VideoVariant

internal fun downloadPlanTestVideo(
    id: Long,
    player: String,
    dubbing: String,
    episode: String,
    quality: Int,
): VideoVariant {
    return VideoVariant(
        id = id,
        animeId = 100,
        player = player,
        playerId = id,
        dubbing = dubbing,
        episode = episode,
        url = "https://example.test/$id",
        index = id.toInt(),
        durationSeconds = 1_400,
        views = 0,
        sourceQualities = listOf(SourceQuality(height = quality)),
    )
}
