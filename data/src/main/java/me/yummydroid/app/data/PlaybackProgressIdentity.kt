package me.yummydroid.app.data

fun List<PlaybackProgress>.distinctLatestByEpisode(): List<PlaybackProgress> {
    return groupBy { it.progressSyncKey() }
        .values
        .mapNotNull { entries -> entries.maxByOrNull { it.updatedAtMs } }
        .sortedWith(compareBy<PlaybackProgress> { it.episode.toDoubleOrNull() ?: Double.MAX_VALUE }.thenBy { it.videoId })
}

fun PlaybackProgress.sameProgressEpisodeAs(other: PlaybackProgress): Boolean {
    return animeId == other.animeId && progressSyncKey() == other.progressSyncKey()
}

fun PlaybackProgress.progressSyncKey(): String {
    val episodeKey = episode.trim()
    if (episodeKey.isNotBlank()) {
        val voiceKey = groupKey.substringAfter('|', groupKey).normalizedVoiceKey()
        return if (voiceKey.isNotBlank()) {
            "anime:$animeId:episode:$episodeKey:voice:$voiceKey"
        } else {
            "anime:$animeId:episode:$episodeKey"
        }
    }
    return when {
        groupKey.isNotBlank() -> "anime:$animeId:group:$groupKey"
        videoId > 0L -> "anime:$animeId:video:$videoId"
        else -> "anime:$animeId"
    }
}
