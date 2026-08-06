package me.yummydroid.app.data

fun AnimeDetails.toAnimeSummary(): Anime {
    return Anime(
        id = id,
        title = title,
        description = description,
        posterUrl = posterUrl,
        animeUrl = "",
        year = year,
        rating = rating,
        userRating = userRating,
        views = views,
        status = status,
        type = type,
        genres = genres,
        blockedIn = blockedIn,
        episodeAired = episodeAired,
        episodeCount = episodeCount,
    )
}

fun PlaybackProgress.toAnimeSummary(): Anime {
    return Anime(
        id = animeId,
        title = animeTitle.ifBlank { "Anime #$animeId" },
        description = "",
        posterUrl = posterUrl,
        animeUrl = "",
        year = null,
        rating = null,
        views = 0L,
        status = "",
        type = "",
        genres = emptyList(),
        blockedIn = emptyList(),
    )
}
