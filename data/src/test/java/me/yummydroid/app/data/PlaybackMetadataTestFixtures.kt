package me.yummydroid.app.data

internal fun testVideo(
    id: Long,
    player: String = "Player",
    dubbing: String = "Voice",
    episode: String = "1",
    skipSegments: List<VideoSkipSegment> = emptyList(),
): VideoVariant {
    return VideoVariant(
        id = id,
        animeId = 100L,
        player = player,
        dubbing = dubbing,
        episode = episode,
        url = "https://example.com/$player/$episode",
        index = id.toInt(),
        durationSeconds = 1_400,
        views = 0L,
        skipSegments = skipSegments,
    )
}
