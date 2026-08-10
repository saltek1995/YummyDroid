package me.yummydroid.app.data

enum class AnimeSort(
    val title: String,
    val apiValue: String,
    val forward: Boolean,
) {
    Rating("Rating", "rating", false),
    RatingCounters("Votes", "rating_counters", false),
    Views("Views", "views", false),
    Year("New", "year", false),
    Top("Top", "top", false),
    Title("A-Z", "title", true),
    Id("Recently added", "id", false),
    Random("Random", "random", true),
}
