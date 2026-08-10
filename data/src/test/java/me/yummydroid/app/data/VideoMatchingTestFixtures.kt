package me.yummydroid.app.data

internal const val RU_VOICE_LABEL = "\u041e\u0437\u0432\u0443\u0447\u043a\u0430"
internal const val RU_SUBTITLES_LABEL = "\u0421\u0443\u0431\u0442\u0438\u0442\u0440\u044b"
internal const val RU_YOLKA_LABEL = "\u0401\u043b\u043a\u0430"
internal const val RU_YOLKA_KEY = "\u0435\u043b\u043a\u0430"

internal fun matchingSubscription(
    animeId: Long = 7,
    player: String = "Kodik",
    dubbing: String = "",
    playerId: Long = 0L,
    videoId: Long = 101,
): VideoSubscription {
    return VideoSubscription(
        animeId = animeId,
        title = "Anime",
        posterUrl = "",
        player = player,
        dubbing = dubbing,
        playerId = playerId,
        videoId = videoId,
    )
}

internal fun matchingVideoVariant(
    dubbing: String,
    player: String = "Alloha",
    playerId: Long = 4,
): VideoVariant {
    return VideoVariant(
        id = 101,
        animeId = 7,
        player = player,
        playerId = playerId,
        dubbing = dubbing,
        episode = "1",
        url = "",
        index = 1,
        durationSeconds = null,
        views = 0,
    )
}
