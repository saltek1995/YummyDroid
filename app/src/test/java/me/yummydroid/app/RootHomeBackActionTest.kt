package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals

class RootHomeBackActionTest {
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
