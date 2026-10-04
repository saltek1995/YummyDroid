package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import androidx.compose.ui.focus.FocusRequester

class BrowseSearchLogicTest {
    @Test
    fun visibleHistoryKeepsTheSixMostRecentEntriesInOrder() {
        val history = (1..8).map { "query-$it" }

        assertEquals(history.take(6), visibleSearchHistory(history))
    }

    @Test
    fun catalogSubmissionPreservesRawTextRejectsOneCharacterAndAllowsClearing() {
        assertEquals("  anime title  ", submittedSearchQuery("  anime title  "))
        assertEquals("", submittedSearchQuery(""))
        assertEquals("  ", submittedSearchQuery("  "))
        assertNull(submittedSearchQuery("a"))
        assertNull(submittedSearchQuery(" "))
    }

    @Test
    fun localHistoryKeepsItsSingleCharacterSearchAndTrimming() {
        assertEquals("a", submittedSearchQuery(" a ", catalogSearch = false))
        assertNull(submittedSearchQuery(" ", catalogSearch = false))
    }

    @Test
    fun dismissingOrNavigatingAwayDoesNotApplyTheUnsubmittedDraft() {
        val submitted = mutableListOf<String>()
        var dismissals = 0
        val actions = actions("draft", submitted, onDismiss = { dismissals++ })
        actions.dismissSearch()
        actions.exitDownFromSearch()
        assertEquals(emptyList(), submitted)
        assertEquals(1, dismissals)
    }

    @Test
    fun confirmationAppliesRawDraftButRejectsShortQueriesWithoutHidingKeyboard() {
        val submitted = mutableListOf<String>()
        var keyboardHides = 0
        val short = actions("a", submitted, onHideKeyboard = { keyboardHides++ })
        assertFalse(short.submitCurrentQuery())
        short.submitAndHideKeyboard()
        assertEquals(0, keyboardHides)
        actions(" Naruto ", submitted).submitCurrentQuery()
        actions("", submitted).submitCurrentQuery()
        assertEquals(listOf(" Naruto ", ""), submitted)
    }

    private fun actions(
        draft: String,
        submitted: MutableList<String>,
        onDismiss: () -> Unit = {},
        onHideKeyboard: () -> Unit = {},
    ) = SearchDialogActions(
        query = draft,
        isTelevision = false,
        inputFocusRequester = FocusRequester(),
        historyFocusRequesters = emptyList(),
        showKeyboard = {},
        hideKeyboardAction = onHideKeyboard,
        onSubmitQuery = { submitted += it },
        onDismiss = onDismiss,
        onExitDown = {},
    )
}
