package me.yummydroid.app.data

data class UserProfile(
    val id: Long,
    val nickname: String,
    val avatarUrl: String,
    val about: String = "",
    val banned: Boolean = false,
    val roles: List<String> = emptyList(),
    val unreadNotifications: Int = 0,
    val unreadMessages: Int = 0,
)
