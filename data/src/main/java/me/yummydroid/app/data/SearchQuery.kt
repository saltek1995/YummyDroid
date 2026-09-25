package me.yummydroid.app.data

import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal fun String.toApiSearchQuery(): String {
    val query = replace('"', ' ').normalizedSearchQuery()
    // Quoting narrows the server's broad matching while retaining alternate titles.
    // The server can still ignore unknown words, so verify its results as well.
    return if (' ' in query) "\"$query\"" else query
}

internal fun String.searchTitlePhrase(): String? = normalizedSearchTitle()
    .takeIf { it.isNotBlank() && ' ' in replace('"', ' ').normalizedSearchQuery() }

private fun String.normalizedSearchTitle(): String = lowercase(Locale.ROOT)
    .replace('ё', 'е')
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
    .trim()

internal suspend fun List<AnimeDto>.matchingSearchTitles(
    query: String,
    loadDetails: suspend (Long) -> AnimeDto,
): List<AnimeDto> {
    val phrase = query.searchTitlePhrase() ?: return this
    fun AnimeDto.matches(): Boolean = (listOf(title) + otherTitles)
        .any { it.normalizedSearchTitle().contains(phrase) }

    // List responses omit alternate titles. Load details only for candidates whose
    // visible title does not match, with bounded concurrency and cancellable work.
    return coroutineScope {
        chunked(4).flatMap { batch ->
            batch.map { anime ->
                async {
                    anime.takeIf { it.matches() || loadDetails(it.animeId).matches() }
                }
            }.awaitAll().filterNotNull()
        }
    }
}
