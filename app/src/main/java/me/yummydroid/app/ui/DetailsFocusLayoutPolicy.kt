package me.yummydroid.app.ui

internal fun buildDetailsFocusLayout(counts: DetailsFocusCounts): DetailsFocusLayout {
    var nextIndex = DETAILS_HERO_FOCUS_GRAPH_SIZE
    val offsets = mutableMapOf<DetailsFocusBlock, Int>()

    fun allocate(block: DetailsFocusBlock, count: Int) {
        offsets[block] = nextIndex
        nextIndex += count.coerceAtLeast(0)
    }

    allocate(DetailsFocusBlock.Screenshots, counts.screenshots)
    allocate(DetailsFocusBlock.RelatedAnime, counts.relatedAnime)
    allocate(DetailsFocusBlock.Episodes, counts.episodes)
    allocate(DetailsFocusBlock.Subscriptions, counts.subscriptions)
    allocate(DetailsFocusBlock.Recommendations, counts.recommendations)
    allocate(DetailsFocusBlock.Comments, counts.comments)
    return DetailsFocusLayout(
        size = nextIndex.coerceAtLeast(DETAILS_HERO_FOCUS_GRAPH_SIZE),
        offsets = offsets,
    )
}

internal fun detailsExpandedListFocusCount(itemCount: Int, expanded: Boolean): Int {
    if (itemCount <= 0) return 0
    return 1 + if (expanded) itemCount else 0
}

internal fun detailsSubscriptionFocusItemCount(
    isAuthorized: Boolean,
    videoCount: Int,
    voiceGroupCount: Int,
    allowSubscriptions: Boolean,
    extrasReady: Boolean,
    expanded: Boolean,
): Int {
    val canShowItems = allowSubscriptions && isAuthorized && videoCount > 0 && extrasReady && voiceGroupCount > 0
    if (!canShowItems) return 0
    return detailsExpandedListFocusCount(voiceGroupCount, expanded)
}

internal fun detailsCommentsFocusItemCount(
    extrasReady: Boolean,
    commentCount: Int,
    isAuthorized: Boolean,
    expanded: Boolean,
): Int {
    if (!extrasReady || (commentCount <= 0 && !isAuthorized)) return 0
    if (!expanded) return 1
    return 1 + if (isAuthorized) 2 else commentCount.coerceAtLeast(0)
}
