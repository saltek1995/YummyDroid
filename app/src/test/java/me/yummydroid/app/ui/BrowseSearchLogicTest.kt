package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BrowseSearchLogicTest {
    @Test
    fun visibleHistoryKeepsTheSixMostRecentEntriesInOrder() {
        val history = (1..8).map { "query-$it" }

        assertEquals(history.take(6), visibleSearchHistory(history))
    }

    @Test
    fun submittedQueryIsTrimmedAndBlankInputIsIgnored() {
        assertEquals("anime title", submittedSearchQuery("  anime title  "))
        assertNull(submittedSearchQuery(" \t\n "))
    }
}
