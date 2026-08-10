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
}
