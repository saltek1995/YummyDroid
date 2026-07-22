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
