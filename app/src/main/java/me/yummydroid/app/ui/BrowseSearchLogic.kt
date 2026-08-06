package me.yummydroid.app.ui

private const val SearchHistoryVisibleLimit = 6

internal fun visibleSearchHistory(searchHistory: List<String>): List<String> {
    return searchHistory.take(SearchHistoryVisibleLimit)
}

internal fun submittedSearchQuery(query: String): String? {
    return query.trim().takeIf { it.isNotBlank() }
}
