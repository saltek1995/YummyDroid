package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals

class RootHomeCatalogReturnTest {
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
    fun rootVisualHistoryCanReturnToCatalogEvenWhenStateIsCatalog() {
        assertEquals(
            true,
            canReturnRootHomeToCatalog(
                isRootHome = true,
                homeSection = BrowseSection.Catalog,
                visualHomeSection = BrowseSection.History,
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
}
