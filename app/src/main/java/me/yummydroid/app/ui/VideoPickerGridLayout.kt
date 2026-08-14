package me.yummydroid.app.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// VideoPickerGridLayout
internal val EpisodeGridHorizontalPadding = 24.dp
internal val EpisodeGridGap = 8.dp
internal val EpisodeCardDefaultHeight = 58.dp
internal val EpisodeCardCompactHeight = 56.dp

private val EpisodeCardMinWidth = 148.dp
private const val EpisodeGridCollapsedRows = 4
private const val EpisodeGridMaxColumns = 5
internal const val EpisodePreviousPageFocusSlot = EpisodeGridMaxColumns * EpisodeGridCollapsedRows
internal const val EpisodeNextPageFocusSlot = EpisodePreviousPageFocusSlot + 1
internal const val EpisodeGridFocusCapacity = EpisodeNextPageFocusSlot + 1

internal data class EpisodeGridLayout(
    val columns: Int,
    val compactCards: Boolean,
    val cardHeight: Dp,
    val pageSize: Int,
    val pageCount: Int,
    val normalizedPage: Int,
    val pageStart: Int,
    val pageEnd: Int,
    val pageContentHeight: Dp,
) {
    fun itemCount(page: Int, total: Int): Int {
        val start = visualGridPageStart(page, pageSize, total)
        return (total - start).coerceIn(0, pageSize)
    }
}
internal fun episodeGridLayout(
    width: Dp,
    itemCount: Int,
    requestedPage: Int,
): EpisodeGridLayout {
    val columns = episodeGridColumns(width)
    val estimatedCardWidth = (width - EpisodeGridGap * (columns - 1).coerceAtLeast(0).toFloat()) /
        columns.toFloat()
    val compactCards = estimatedCardWidth < 190.dp
    val cardHeight = if (compactCards) EpisodeCardCompactHeight else EpisodeCardDefaultHeight
    val pageSize = visualGridPageSize(columns, EpisodeGridCollapsedRows)
    val pageCount = visualGridPageCount(itemCount, pageSize)
    val normalizedPage = requestedPage.coerceIn(0, pageCount - 1)
    val pageStart = visualGridPageStart(normalizedPage, pageSize, itemCount)
    val pageEnd = (pageStart + pageSize).coerceAtMost(itemCount)
    val totalRows = ((itemCount + columns - 1) / columns).coerceAtLeast(1)
    val pageRows = if (pageCount > 1) EpisodeGridCollapsedRows else totalRows
    val pageContentHeight = cardHeight * pageRows.toFloat() +
        EpisodeGridGap * (pageRows - 1).coerceAtLeast(0).toFloat()

    return EpisodeGridLayout(
        columns = columns,
        compactCards = compactCards,
        cardHeight = cardHeight,
        pageSize = pageSize,
        pageCount = pageCount,
        normalizedPage = normalizedPage,
        pageStart = pageStart,
        pageEnd = pageEnd,
        pageContentHeight = pageContentHeight,
    )
}

internal fun episodeGridColumns(width: Dp): Int {
    val columns = ((width.value + EpisodeGridGap.value) / (EpisodeCardMinWidth.value + EpisodeGridGap.value))
        .toInt()
    return columns.coerceIn(1, EpisodeGridMaxColumns)
}
