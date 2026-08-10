package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals

class RootHomeBackToTopTest {
    @Test
    fun rootHomeBackToTopIgnoresFocusedGridItemWhenScrollIsAtTop() {
        assertEquals(
            false,
            canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = BrowseSection.Catalog,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun rootHomeBackToTopHandlesCatalogScrollEvenWhenFocusIndexIsLost() {
        assertEquals(
            true,
            canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = BrowseSection.Catalog,
                firstVisibleItemIndex = 12,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun rootHomeBackToTopIgnoresHistoryFocusedItemWhenScrollIsAtTop() {
        assertEquals(
            false,
            canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = BrowseSection.History,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun rootHomeBackToTopHandlesScheduleScroll() {
        assertEquals(
            true,
            canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = BrowseSection.Schedule,
                firstVisibleItemIndex = 2,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun rootHomeBackToTopIgnoresDownloadsFocus() {
        assertEquals(
            false,
            canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = BrowseSection.Downloads,
                firstVisibleItemIndex = 5,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun rootHomeBackToTopFallsThroughAtFirstFocusedItemAndTopScroll() {
        assertEquals(
            false,
            canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = BrowseSection.Catalog,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun rootHomeBackToTopRequiresRootHome() {
        assertEquals(
            false,
            canHandleRootHomeBackToTop(
                isRootHome = false,
                homeSection = BrowseSection.Catalog,
                firstVisibleItemIndex = 10,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }
}
