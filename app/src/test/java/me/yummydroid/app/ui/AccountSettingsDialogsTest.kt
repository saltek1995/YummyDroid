package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.InputAction

class AccountSettingsDialogsTest {
    @Test
    fun cacheSizeFormatterUsesReadableUnits() {
        assertEquals("0 B", formatCacheSize(-1))
        assertEquals("0 B", formatCacheSize(0))
        assertEquals("512 B", formatCacheSize(512))
        assertEquals("1.0 KB", formatCacheSize(1024))
        assertEquals("1.5 KB", formatCacheSize(1536))
        assertEquals("100 KB", formatCacheSize(100 * 1024))
        assertEquals("1.0 MB", formatCacheSize(1024L * 1024L))
        assertEquals("1.5 GB", formatCacheSize(1536L * 1024L * 1024L))
    }

    @Test
    fun domainDisplayTitleRemovesSchemeAndTrailingSlashOnly() {
        assertEquals("yummyani.me", "https://yummyani.me/".domainDisplayTitle())
        assertEquals("api.yani.tv/swagger", "http://api.yani.tv/swagger/".domainDisplayTitle())
        assertEquals("mirror.example/path", "mirror.example/path".domainDisplayTitle())
    }

    @Test
    fun notificationBadgeTextOnlyShowsPositiveUnreadCount() {
        assertNull(0.notificationBadgeText())
        assertNull((-4).notificationBadgeText())
        assertEquals("1", 1.notificationBadgeText())
        assertEquals("99", 99.notificationBadgeText())
        assertEquals("99+", 100.notificationBadgeText())
    }

    @Test
    fun childSettingsDialogConsumesOnlyBack() {
        assertTrue(
            shouldCloseSettingsChildDialog(InputAction.Back, SettingsChildDialog.InterfaceScale),
        )
        assertFalse(
            shouldCloseSettingsChildDialog(InputAction.Confirm, SettingsChildDialog.InterfaceScale),
        )
        assertFalse(shouldCloseSettingsChildDialog(InputAction.Back, null))
    }

    @Test
    fun settingsPickerAppliesSelectionBeforeClosing() {
        val events = mutableListOf<String>()

        selectSettingsPickerOption(
            option = "selected",
            onSelected = { events += "apply:$it" },
            onDismiss = { events += "dismiss" },
        )

        assertEquals(listOf("apply:selected", "dismiss"), events)
    }

    @Test
    fun profileBackClosesTheTopmostRenderedChildDialogFirst() {
        assertEquals(
            ProfileChildDialog.Notifications,
            profileChildDialogForBack(subscriptionsOpen = true, notificationsOpen = true),
        )
        assertEquals(
            ProfileChildDialog.Notifications,
            profileChildDialogForBack(subscriptionsOpen = false, notificationsOpen = true),
        )
        assertNull(profileChildDialogForBack(subscriptionsOpen = false, notificationsOpen = false))
    }

    @Test
    fun rootAppDialogsAreMutuallyExclusive() {
        val state = YummyDroidAppModalState()

        state.openProfile()
        state.openLogin()
        assertTrue(state.loginDialogOpen)
        assertFalse(state.profileDialogOpen)

        state.openSettings()
        assertTrue(state.settingsDialogOpen)
        assertFalse(state.loginDialogOpen)
    }

    @Test
    fun catalogDialogsAreMutuallyExclusive() {
        val state = BrowseCatalogDialogRuntime()

        state.openSearch()
        state.openFilters()
        assertTrue(state.filtersDialogOpen)
        assertFalse(state.searchDialogOpen)

        state.openSearch()
        assertTrue(state.searchDialogOpen)
        assertFalse(state.filtersDialogOpen)
    }
}
