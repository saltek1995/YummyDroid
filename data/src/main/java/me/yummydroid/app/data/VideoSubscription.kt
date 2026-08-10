package me.yummydroid.app.data

data class VideoSubscription(
    val animeId: Long,
    val title: String,
    val posterUrl: String,
    val player: String,
    val dubbing: String,
    val playerId: Long = 0L,
    val videoId: Long = 0L,
)
