package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals

class AppBackHandlingTest {
    @Test
    fun modalHasHighestPriority() {
        assertEquals(
            AppBackAction.CloseModal,
            resolveAppBackAction(
                hasModal = true,
                canHidePlayerControls = true,
                canNavigateBack = true,
                canScrollRootHomeToTop = true,
            ),
        )
    }

    @Test
    fun playerControlsBeatNavigation() {
        assertEquals(
            AppBackAction.HidePlayerControls,
            resolveAppBackAction(
                hasModal = false,
                canHidePlayerControls = true,
                canNavigateBack = true,
                canScrollRootHomeToTop = true,
            ),
        )
    }

    @Test
    fun navigationBeatsRootScroll() {
        assertEquals(
            AppBackAction.NavigateBack,
            resolveAppBackAction(
                hasModal = false,
                canHidePlayerControls = false,
                canNavigateBack = true,
                canScrollRootHomeToTop = true,
            ),
        )
    }

    @Test
    fun rootScrollRunsOnlyWhenNothingAboveItConsumesBack() {
        assertEquals(
            AppBackAction.ScrollRootHomeToTop,
            resolveAppBackAction(
                hasModal = false,
                canHidePlayerControls = false,
                canNavigateBack = false,
                canScrollRootHomeToTop = true,
            ),
        )
    }

    @Test
    fun rootAtTopFallsThroughToSystem() {
        assertEquals(
            AppBackAction.LetSystemHandle,
            resolveAppBackAction(
                hasModal = false,
                canHidePlayerControls = false,
                canNavigateBack = false,
                canScrollRootHomeToTop = false,
            ),
        )
    }

    @Test
    fun rootHomeBackToTopHandlesFocusedGridItemEvenWhenScrollIsAtTop() {
        assertEquals(
            true,
            canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = BrowseSection.Catalog,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                focusedItemIndex = 8,
            ),
        )
    }

    @Test
    fun rootHomeBackToTopHandlesHistoryFocusedItemEvenWhenScrollIsAtTop() {
        assertEquals(
            true,
            canHandleRootHomeBackToTop(
                isRootHome = true,
                homeSection = BrowseSection.History,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                focusedItemIndex = 3,
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
                focusedItemIndex = 5,
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
                focusedItemIndex = 0,
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
                focusedItemIndex = 10,
            ),
        )
    }
}
