package me.yummydroid.app

internal enum class AppBackAction {
    CloseModal,
    HidePlayerControls,
    NavigateBack,
    ScrollRootHomeToTop,
    LetSystemHandle,
}

internal fun resolveAppBackAction(
    hasModal: Boolean,
    canHidePlayerControls: Boolean,
    canNavigateBack: Boolean,
    canScrollRootHomeToTop: Boolean,
): AppBackAction {
    return when {
        hasModal -> AppBackAction.CloseModal
        canHidePlayerControls -> AppBackAction.HidePlayerControls
        canNavigateBack -> AppBackAction.NavigateBack
        canScrollRootHomeToTop -> AppBackAction.ScrollRootHomeToTop
        else -> AppBackAction.LetSystemHandle
    }
}

internal fun canHandleRootHomeBackToTop(
    isRootHome: Boolean,
    homeSection: BrowseSection,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    focusedItemIndex: Int,
): Boolean {
    if (!isRootHome || homeSection == BrowseSection.Downloads) return false
    return firstVisibleItemIndex > 0 ||
        firstVisibleItemScrollOffset > 0 ||
        focusedItemIndex > 0
}
