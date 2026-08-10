package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.BrowseSection

class BrowseCatalogActionsTest {
    @Test
    fun browseCatalogActionsAreEnabledOnlyForOnlineCatalog() {
        assertTrue(browseCatalogActionsEnabledForSection(BrowseSection.Catalog, forcedOfflineMode = false))
        assertFalse(browseCatalogActionsEnabledForSection(BrowseSection.Schedule, forcedOfflineMode = false))
        assertFalse(browseCatalogActionsEnabledForSection(BrowseSection.History, forcedOfflineMode = false))
        assertFalse(browseCatalogActionsEnabledForSection(BrowseSection.Downloads, forcedOfflineMode = false))
        assertFalse(browseCatalogActionsEnabledForSection(BrowseSection.Catalog, forcedOfflineMode = true))
    }
}
