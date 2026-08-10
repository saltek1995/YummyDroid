package me.yummydroid.app

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
