package me.yummydroid.app

internal fun canHandleRootHomeBackToTop(
    isRootHome: Boolean,
    homeSection: BrowseSection,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean {
    if (!isRootHome || homeSection == BrowseSection.Downloads) return false
    return firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0
}
