package me.yummydroid.app.data

data class AnimeCollectionSummary(
    val id: Long,
    val title: String,
    val description: String,
    val ownerName: String,
    val posterUrl: String,
    val animeCount: Int,
    val views: Long,
    val likes: Long,
    val dislikes: Long,
    val createdAtSeconds: Long,
    val animes: List<Anime> = emptyList(),
)
