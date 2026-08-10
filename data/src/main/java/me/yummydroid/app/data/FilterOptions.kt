package me.yummydroid.app.data

val statusFilterOptions = listOf(
    FilterOption("Released", "released"),
    FilterOption("Ongoing", "ongoing"),
    FilterOption("Announcements", "announcement"),
)

val seasonFilterOptions = listOf(
    FilterOption("Winter", "winter"),
    FilterOption("Spring", "spring"),
    FilterOption("Summer", "summer"),
    FilterOption("Fall", "fall"),
)

val translateFilterOptions = listOf(
    FilterOption("Full dubbing", "dubbing"),
    FilterOption("Multi voice", "multivoice"),
    FilterOption("Two voice", "duet"),
    FilterOption("Single voice", "onevoice"),
    FilterOption("Subtitles", "subtitles"),
)

val ageRatingFilterOptions = listOf(
    FilterOption("PG", "1"),
    FilterOption("PG-13", "2"),
    FilterOption("R-17+", "3"),
    FilterOption("R+", "4"),
    FilterOption("Rx", "5"),
)

val userMarkFilterOptions = listOf(
    FilterOption("Watching", "0"),
    FilterOption("Planned", "1"),
    FilterOption("Watched", "2"),
    FilterOption("Dropped", "3"),
    FilterOption("Postponed", "5"),
    FilterOption("Favorites", "4"),
)
