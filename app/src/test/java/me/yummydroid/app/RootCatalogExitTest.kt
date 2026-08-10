package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals

class RootCatalogExitTest {
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
    fun rootCatalogExitRejectsUnsettledBrowsePager() {
        assertEquals(
            false,
            canExitRootCatalog(
                isRootHome = true,
                homeSection = BrowseSection.Catalog,
                firstVisibleItemIndex = 0,
                firstVisibleItemScrollOffset = 0,
                browsePagerSettledAtStateSection = false,
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
