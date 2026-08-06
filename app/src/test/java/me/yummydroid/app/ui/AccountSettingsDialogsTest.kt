package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
