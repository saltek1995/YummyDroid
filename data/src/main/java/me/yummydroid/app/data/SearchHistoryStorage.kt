package me.yummydroid.app.data

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

class SearchHistoryStorage(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(): List<String> {
        return prefs.getJsonOrNull<List<String>>(KEY_HISTORY)
            .orEmpty()
            .normalizedSearchHistory()
    }

    fun add(query: String): List<String> {
        val normalizedQuery = query.normalizedSearchQuery()
        if (normalizedQuery.isBlank()) return read()

        val updated = (listOf(normalizedQuery) + read())
            .normalizedSearchHistory()
            .take(MAX_HISTORY_ENTRIES)
        prefs.edit { putString(KEY_HISTORY, updated.encodeAppJson()) }
        return updated
    }

    fun remove(query: String): List<String> {
        val normalizedQuery = query.normalizedSearchQuery()
        val updated = read()
            .filterNot { it.equals(normalizedQuery, ignoreCase = true) }
            .normalizedSearchHistory()
        prefs.edit { putString(KEY_HISTORY, updated.encodeAppJson()) }
        return updated
    }

    private companion object {
        const val PREFS_NAME = "yummydroid_search_history"
        const val KEY_HISTORY = "history"
        const val MAX_HISTORY_ENTRIES = 8
    }
}

internal fun String.normalizedSearchQuery(): String {
    return trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ")
}

internal fun List<String>.normalizedSearchHistory(): List<String> {
    val seen = mutableSetOf<String>()
    return map { it.normalizedSearchQuery() }
        .filter { it.isNotBlank() }
        .filter { seen.add(it.lowercase(Locale.ROOT)) }
}
