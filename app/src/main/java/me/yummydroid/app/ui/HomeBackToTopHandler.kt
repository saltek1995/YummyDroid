package me.yummydroid.app.ui

import me.yummydroid.app.BrowseSection

internal class HomeBackToTopHandler(
    val section: BrowseSection,
    private val canHandle: () -> Boolean,
    private val handle: (withFocus: Boolean) -> Boolean,
) {
    fun canHandleBackToTop(): Boolean = canHandle()

    fun handleBackToTop(withFocus: Boolean): Boolean = handle(withFocus)
}
