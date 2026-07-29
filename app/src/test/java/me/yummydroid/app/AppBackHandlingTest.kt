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
    fun rootScrollBeatsReturnToCatalog() {
        assertEquals(
            AppBackAction.ScrollRootHomeToTop,
            resolveAppBackAction(
                hasModal = false,
                canHidePlayerControls = false,
                canNavigateBack = false,
                canScrollRootHomeToTop = true,
                canReturnRootHomeToCatalog = true,
            ),
        )
    }

    @Test
    fun rootNonCatalogAtTopReturnsToCatalog() {
        assertEquals(
            AppBackAction.ReturnRootHomeToCatalog,
            resolveAppBackAction(
                hasModal = false,
                canHidePlayerControls = false,
                canNavigateBack = false,
                canScrollRootHomeToTop = false,
                canReturnRootHomeToCatalog = true,
            ),
        )
    }

    @Test
    fun rootCatalogAtTopCanExitApp() {
        assertEquals(
            AppBackAction.ExitApp,
            resolveAppBackAction(
                hasModal = false,
                canHidePlayerControls = false,
                canNavigateBack = false,
                canScrollRootHomeToTop = false,
                canExitApp = true,
            ),
        )
    }

    @Test
    fun rootAtTopWithNoDestinationIsIgnored() {
        assertEquals(
            AppBackAction.Ignore,
            resolveAppBackAction(
                hasModal = false,
                canHidePlayerControls = false,
                canNavigateBack = false,
                canScrollRootHomeToTop = false,
                canExitApp = false,
            ),
        )
    }

    @Test
    fun rootScheduleCanReturnToCatalog() {
        assertEquals(
            true,
            canReturnRootHomeToCatalog(
                isRootHome = true,
                homeSection = BrowseSection.Schedule,
            ),
        )
    }

    @Test
    fun rootHistoryCanReturnToCatalog() {
        assertEquals(
            true,
            canReturnRootHomeToCatalog(
                isRootHome = true,
                homeSection = BrowseSection.History,
            ),
        )
    }

    @Test
    fun rootCatalogDoesNotReturnToItself() {
        assertEquals(
            false,
            canReturnRootHomeToCatalog(
                isRootHome = true,
                homeSection = BrowseSection.Catalog,
            ),
        )
    }

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

    @Test
    fun rootCatalogExitRequiresCatalogAtTop() {
        assertEquals(
            true,
            canExitRootCatalog(
                isRootHome = true,
                homeSection = BrowseSection.Catalog,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun rootCatalogExitRejectsScrolledCatalog() {
        assertEquals(
            false,
            canExitRootCatalog(
                isRootHome = true,
                homeSection = BrowseSection.Catalog,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }

    @Test
    fun rootCatalogExitRejectsScheduleAtTop() {
        assertEquals(
            false,
            canExitRootCatalog(
                isRootHome = true,
                homeSection = BrowseSection.Schedule,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
            ),
        )
    }
}
