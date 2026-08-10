package me.yummydroid.app.data

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackProgress(
    val animeId: Long,
    val videoId: Long,
    val animeTitle: String = "",
    val posterUrl: String = "",
    val groupKey: String,
    val episode: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long,
)
