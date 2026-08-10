package me.yummydroid.app.ui

internal enum class DetailsFocusBlock {
    Screenshots,
    RelatedAnime,
    Episodes,
    Subscriptions,
    Recommendations,
    Comments,
}

internal data class DetailsFocusLayout(
    val size: Int,
    private val offsets: Map<DetailsFocusBlock, Int>,
) {
    fun offset(block: DetailsFocusBlock): Int = offsets.getValue(block)
}

internal data class DetailsFocusCounts(
    val screenshots: Int,
    val relatedAnime: Int,
    val episodes: Int,
    val subscriptions: Int,
    val recommendations: Int,
    val comments: Int,
)

internal object DetailsFocusBlockKey {
    const val HeroPoster = "details:hero-poster"
    const val HeroActions = "details:hero-actions"
    const val HeroStats = "details:hero-stats"
    const val HeroFacts = "details:hero-facts"
    const val HeroMarks = "details:hero-marks"
    const val Screenshots = "details:screenshots"
    const val RelatedAnime = "details:related-anime"
    const val Episodes = "details:episodes"
    const val Subscriptions = "details:subscriptions"
    const val Recommendations = "details:recommendations"
    const val Comments = "details:comments"
}
