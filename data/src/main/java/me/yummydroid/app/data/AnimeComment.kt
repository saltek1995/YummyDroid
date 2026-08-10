package me.yummydroid.app.data

data class AnimeComment(
    val id: Long,
    val userId: Long,
    val userName: String,
    val avatarUrl: String,
    val text: String,
    val createdAtSeconds: Long,
    val likes: Long,
    val dislikes: Long,
    val childrenCount: Int,
)
