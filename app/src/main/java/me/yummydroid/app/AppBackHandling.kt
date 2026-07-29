package me.yummydroid.app

internal enum class AppBackAction {
    CloseModal,
    HidePlayerControls,
    NavigateBack,
    ScrollRootHomeToTop,
    ReturnRootHomeToCatalog,
    ExitApp,
    Ignore,
}

internal fun resolveAppBackAction(
    hasModal: Boolean,
    canHidePlayerControls: Boolean,
    canNavigateBack: Boolean,
    canScrollRootHomeToTop: Boolean,
    canReturnRootHomeToCatalog: Boolean = false,
    canExitApp: Boolean = false,
): AppBackAction {
    return when {
        hasModal -> AppBackAction.CloseModal
        canHidePlayerControls -> AppBackAction.HidePlayerControls
        canNavigateBack -> AppBackAction.NavigateBack
        canScrollRootHomeToTop -> AppBackAction.ScrollRootHomeToTop
        canReturnRootHomeToCatalog -> AppBackAction.ReturnRootHomeToCatalog
        canExitApp -> AppBackAction.ExitApp
        else -> AppBackAction.Ignore
    }
}

internal fun canHandleRootHomeBackToTop(
    isRootHome: Boolean,
    homeSection: BrowseSection,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean {
    if (!isRootHome || homeSection == BrowseSection.Downloads) return false
    return firstVisibleItemIndex > 0 ||
        firstVisibleItemScrollOffset > 0
}

internal fun canExitRootCatalog(
    isRootHome: Boolean,
    homeSection: BrowseSection,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean {
    if (!isRootHome || homeSection != BrowseSection.Catalog) return false
    return firstVisibleItemIndex == 0 &&
        firstVisibleItemScrollOffset == 0
}

internal fun canReturnRootHomeToCatalog(
    isRootHome: Boolean,
    homeSection: BrowseSection,
): Boolean {
    return isRootHome &&
        (homeSection == BrowseSection.Schedule || homeSection == BrowseSection.History)
}
