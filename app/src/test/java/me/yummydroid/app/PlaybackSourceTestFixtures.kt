package me.yummydroid.app

import me.yummydroid.app.data.VideoVariant

internal fun kodikSourceVideo(): VideoVariant = playbackSourceVideo(
    id = 593472,
    player = "Kodik",
    index = 30,
    url = "https://kodikplayer.com/season/95032/hash/720p?episode=5",
)

internal fun cvhSourceVideo(): VideoVariant = playbackSourceVideo(
    id = 843499,
    player = "CVH",
    index = 511,
    url = "https://ru.yummyani.me/iframeCVH.html?dubbing_code=AniLibria&anime_id=51215&episode=5",
)

internal fun playbackSourceVideo(
    id: Long,
    player: String,
    index: Int,
    url: String,
): VideoVariant {
    return VideoVariant(
        id = id,
        animeId = 10669,
        player = player,
        playerId = 0L,
        dubbing = "AniLibria",
        episode = "5",
        url = url,
        index = index,
        durationSeconds = 1_421,
        views = 0L,
    )
}
