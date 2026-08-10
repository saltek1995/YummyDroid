package me.yummydroid.app

internal fun canReturnRootHomeToCatalog(
    isRootHome: Boolean,
    homeSection: BrowseSection,
    visualHomeSection: BrowseSection = homeSection,
): Boolean {
    return isRootHome &&
        (
            homeSection == BrowseSection.Schedule ||
                homeSection == BrowseSection.History ||
                visualHomeSection == BrowseSection.Schedule ||
                visualHomeSection == BrowseSection.History
        )
}
