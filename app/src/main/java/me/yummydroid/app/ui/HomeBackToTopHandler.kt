package me.yummydroid.app.ui

import me.yummydroid.app.BrowseSection

internal class HomeBackToTopHandler(
    val section: BrowseSection,
    private val canHandle: () -> Boolean,
    private val handle: () -> Boolean,
) {
    fun canHandleBackToTop(): Boolean = canHandle()

    fun handleBackToTop(): Boolean = handle()
}
