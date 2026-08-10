package me.yummydroid.app.data

enum class AnimeStatusFilter(
    val title: String,
    val apiValue: String?,
) {
    All("All", null),
}

enum class AnimeGenreFilter(
    val title: String,
    val apiValue: String?,
) {
    All("All genres", null),
}
