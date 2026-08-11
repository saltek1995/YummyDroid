package me.yummydroid.app.data

import kotlin.math.abs

internal fun List<MaterializedSubtitleSegment>.shouldShiftWebVttCueTimes(): Boolean {
    val samples = filter { it.offsetMs > 0L }
        .mapNotNull { segment ->
            val firstCueStartMs = segment.body.firstWebVttCueStartMs() ?: return@mapNotNull null
            segment to firstCueStartMs
        }
    if (samples.isEmpty()) return false

    val localCueCount = samples.count { (segment, firstCueStartMs) ->
        val localWindowMs = maxOf(segment.durationMs + 5_000L, 60_000L)
        firstCueStartMs < localWindowMs && firstCueStartMs + 10_000L < segment.offsetMs
    }
    val absoluteCueCount = samples.count { (segment, firstCueStartMs) ->
        firstCueStartMs + 10_000L >= segment.offsetMs ||
            abs(firstCueStartMs - segment.offsetMs) <= segment.durationMs + 10_000L
    }

    return localCueCount > absoluteCueCount
}

internal fun MaterializedSubtitleSegment.normalizedWebVttCueBody(shiftBySegmentOffset: Boolean): String {
    val firstCueStartMs = body.firstWebVttCueStartMs()
    val mapLocalMs = localMapMs
    val timestampMapShiftMs = if (mapLocalMs != null && firstCueStartMs != null) {
        val localWindowMs = maxOf(durationMs + 5_000L, 60_000L)
        val cueLooksLocalToMap = abs(firstCueStartMs - mapLocalMs) <= localWindowMs ||
            firstCueStartMs < localWindowMs
        if (cueLooksLocalToMap) offsetMs - mapLocalMs else 0L
    } else {
        0L
    }
    val shiftMs = if (timestampMapShiftMs != 0L || mapLocalMs != null) {
        timestampMapShiftMs
    } else if (shiftBySegmentOffset) {
        offsetMs
    } else {
        0L
    }
    return body.shiftWebVttCueTimes(shiftMs)
}

private fun String.firstWebVttCueStartMs(): Long? {
    return lineSequence()
        .mapNotNull { line ->
            SubtitleParsingPatterns.webVttTiming
                .find(line.trim())
                ?.groupValues
                ?.getOrNull(1)
                ?.webVttTimestampMs()
        }
        .firstOrNull()
}

private fun String.shiftWebVttCueTimes(offsetMs: Long): String {
    if (offsetMs == 0L) return this
    return lineSequence().joinToString("\n") { line ->
        val match = SubtitleParsingPatterns.webVttTiming.find(line.trim()) ?: return@joinToString line
        val startMs = match.groupValues.getOrNull(1)?.webVttTimestampMs() ?: return@joinToString line
        val endMs = match.groupValues.getOrNull(2)?.webVttTimestampMs() ?: return@joinToString line
        val settings = match.groupValues.getOrNull(3).orEmpty()
        "${(startMs + offsetMs).toWebVttTimestamp()} --> ${(endMs + offsetMs).toWebVttTimestamp()}$settings"
    }
}
