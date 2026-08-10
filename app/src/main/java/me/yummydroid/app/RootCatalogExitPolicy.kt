package me.yummydroid.app

internal fun canExitRootCatalog(
    isRootHome: Boolean,
    homeSection: BrowseSection,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    browsePagerSettledAtStateSection: Boolean = true,
): Boolean {
    if (!browsePagerSettledAtStateSection) return false
    if (!isRootHome || homeSection != BrowseSection.Catalog) return false
    return firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0
}
