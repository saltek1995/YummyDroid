package me.yummydroid.app.data

enum class UserAnimeListMark(
    val id: Int,
) {
    Watching(0),
    Planned(1),
    Watched(2),
    Dropped(3),
    Postponed(5);

    companion object {
        fun fromId(id: Int?): UserAnimeListMark? = entries.firstOrNull { it.id == id }

        val displayOrder: List<UserAnimeListMark> = listOf(
            Watching,
            Planned,
            Watched,
            Postponed,
            Dropped,
        )
    }
}

data class UserAnimeMark(
    val list: UserAnimeListMark? = null,
    val isFavorite: Boolean = false,
)
