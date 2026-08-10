package me.yummydroid.app.ui

import androidx.compose.ui.focus.FocusRequester

internal class SearchDialogActions(
    private val query: String,
    private val isTelevision: Boolean,
    private val inputFocusRequester: FocusRequester,
    private val historyFocusRequesters: List<FocusRequester>,
    private val showKeyboard: () -> Unit,
    private val hideKeyboardAction: () -> Unit,
    private val onSubmitQuery: (String) -> Unit,
    private val onDismiss: () -> Unit,
    private val onExitDown: () -> Unit,
) {
    fun submitCurrentQuery() {
        submittedSearchQuery(query)?.let(onSubmitQuery)
    }

    fun dismissSearch() {
        submitCurrentQuery()
        hideKeyboard()
        onDismiss()
    }

    fun exitDownFromSearch() {
        submitCurrentQuery()
        hideKeyboard()
        onExitDown()
    }

    fun focusInput() {
        inputFocusRequester.requestFocusSafely()
        if (!isTelevision) showKeyboard()
    }

    fun focusHistoryOrExit(): Boolean {
        val firstHistoryFocus = historyFocusRequesters.firstOrNull()
        if (firstHistoryFocus == null) {
            exitDownFromSearch()
        } else {
            hideKeyboard()
            firstHistoryFocus.requestFocusSafely()
        }
        return true
    }

    fun submitAndHideKeyboard() {
        submitCurrentQuery()
        hideKeyboard()
    }

    fun hideKeyboard() {
        hideKeyboardAction()
    }
}
