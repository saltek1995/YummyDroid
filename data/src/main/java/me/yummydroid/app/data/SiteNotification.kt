package me.yummydroid.app.data

data class SiteNotification(
    val id: Long,
    val title: String,
    val text: String,
    val clickUrl: String,
    val type: String,
    val subType: String,
    val objectId: Long,
    val dateSeconds: Long,
    val viewed: Boolean,
)
