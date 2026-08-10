package me.yummydroid.app.data

data class DownloadEpisodeSlot(
    val key: String,
    val title: String,
    val order: Double?,
)

fun VideoVariant.downloadEpisodeSlot(): DownloadEpisodeSlot {
    return DownloadEpisodeSlot(
        key = matchingEpisodeKey,
        title = episode.trim().takeIf { it.isNotBlank() } ?: matchingEpisodeKey,
        order = episodeOrderValue(),
    )
}

fun downloadEpisodeSlotComparator(): Comparator<DownloadEpisodeSlot> {
    return compareBy<DownloadEpisodeSlot> { it.order ?: Double.MAX_VALUE }
        .thenBy { it.title }
        .thenBy { it.key }
}

fun Iterable<VideoVariant>.sortedDownloadEpisodeSlots(): List<DownloadEpisodeSlot> {
    return distinctBy { it.matchingEpisodeKey }
        .map { it.downloadEpisodeSlot() }
        .sortedWith(downloadEpisodeSlotComparator())
}

fun List<DownloadEpisodeSlot>.compactEpisodeRanges(): List<String> {
    if (isEmpty()) return emptyList()
    val ranges = mutableListOf<String>()
    var start = first()
    var previous = first()

    drop(1).forEach { current ->
        val contiguous = previous.order?.let { previousOrder ->
            current.order?.let { currentOrder ->
                isWholeNumber(previousOrder) &&
                    isWholeNumber(currentOrder) &&
                    currentOrder.toInt() == previousOrder.toInt() + 1
            }
        } == true
        if (contiguous) {
            previous = current
        } else {
            ranges += start.rangeTitle(previous)
            start = current
            previous = current
        }
    }
    ranges += start.rangeTitle(previous)
    return ranges
}

fun List<DownloadEpisodeSlot>.compactEpisodeNumberRanges(): List<IntRange> {
    return mapNotNull { slot ->
        slot.order
            ?.takeIf(::isWholeNumber)
            ?.toInt()
            ?.takeIf { it > 0 }
            ?.let { it..it }
    }.mergeEpisodeRanges()
}

fun List<IntRange>.mergeEpisodeRanges(): List<IntRange> {
    if (isEmpty()) return emptyList()
    val sorted = sortedWith(compareBy<IntRange> { it.first }.thenBy { it.last })
    val merged = mutableListOf<IntRange>()
    var current = sorted.first()
    sorted.drop(1).forEach { next ->
        if (next.first <= current.last + 1) {
            current = current.first..maxOf(current.last, next.last)
        } else {
            merged += current
            current = next
        }
    }
    merged += current
    return merged
}

fun IntRange.subtractEpisodeRanges(availableRanges: List<IntRange>): List<IntRange> {
    var cursor = first
    val missing = mutableListOf<IntRange>()
    availableRanges
        .mergeEpisodeRanges()
        .forEach { available ->
            if (available.last < cursor) return@forEach
            if (available.first > last) return@forEach
            if (available.first > cursor) {
                missing += cursor..minOf(available.first - 1, last)
            }
            cursor = maxOf(cursor, available.last + 1)
            if (cursor > last) return missing
        }
    if (cursor <= last) {
        missing += cursor..last
    }
    return missing
}

fun List<IntRange>.formatEpisodeRanges(limit: Int): String {
    val visible = take(limit)
    val suffix = if (size > limit) ", ..." else ""
    return visible.joinToString(", ") { range ->
        if (range.first == range.last) range.first.toString() else "${range.first}-${range.last}"
    } + suffix
}

fun isWholeNumber(value: Double): Boolean {
    return value % 1.0 == 0.0
}

private fun DownloadEpisodeSlot.rangeTitle(end: DownloadEpisodeSlot): String {
    val startTitle = order?.formatEpisodeNumber() ?: title
    val endTitle = end.order?.formatEpisodeNumber() ?: end.title
    return if (key == end.key) startTitle else "$startTitle-$endTitle"
}

private fun Double.formatEpisodeNumber(): String {
    val asInt = toInt()
    return if (isWholeNumber(this)) asInt.toString() else toString().trimEnd('0').trimEnd('.')
}
