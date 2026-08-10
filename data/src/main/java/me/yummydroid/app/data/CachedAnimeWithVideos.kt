package me.yummydroid.app.data

import kotlinx.serialization.Serializable

@Serializable
data class CachedAnimeWithVideos(
    val details: AnimeDetails,
    val videos: List<VideoVariant>,
)
