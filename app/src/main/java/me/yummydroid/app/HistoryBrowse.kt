package me.yummydroid.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.BrowseFilters

data class HistoryBrowseRequest(
    val context: ContentContext,
    val anime: LoadState<List<Anime>>,
    val query: String,
    val filters: BrowseFilters,
    val revision: Long,
)

data class HistoryBrowseResult(val request: HistoryBrowseRequest, val content: LoadState<List<Anime>>)

internal val YummyDroidUiState.historyBrowseActive: Boolean
    get() = historySearchQuery.isNotBlank() || historyFilters != BrowseFilters()

internal fun YummyDroidUiState.historyBrowseRequest() = HistoryBrowseRequest(
    contentContext(), historyAnime, historySearchQuery, historyFilters, historyBrowseRevision,
)

internal val YummyDroidUiState.visibleHistoryAnime: LoadState<List<Anime>>
    get() = when {
        !historyBrowseActive || historyAnime !is LoadState.Ready -> historyAnime
        else -> historyBrowseResult?.takeIf { it.request == historyBrowseRequest() }?.content ?: LoadState.Loading
    }

internal suspend fun observeHistoryBrowse(
    state: StateFlow<YummyDroidUiState>,
    updateState: ((YummyDroidUiState) -> YummyDroidUiState) -> Unit,
    filter: suspend (Set<Long>, String, BrowseFilters) -> List<Anime>,
) {
    state.map { it.historyBrowseRequest() }.distinctUntilChanged().collectLatest { request ->
        if (request.query.isBlank() && request.filters == BrowseFilters()) return@collectLatest
        val anime = (request.anime as? LoadState.Ready)?.data ?: return@collectLatest
        val content: LoadState<List<Anime>> = try {
            // Typing only cancels history filtering, never catalog requests.
            delay(250)
            val ids = anime.map { it.id }.toSet()
            LoadState.Ready(if (ids.isEmpty()) emptyList() else filter(ids, request.query, request.filters))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            LoadState.Error(failure.userMessage())
        }
        updateState { current ->
            if (current.historyBrowseRequest() == request) {
                current.copy(historyBrowseResult = HistoryBrowseResult(request, content))
            } else current
        }
    }
}
