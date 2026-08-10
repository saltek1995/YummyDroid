package me.yummydroid.app.data

internal fun assembleHlsSubtitleBody(
    playlist: String,
    playlistUrl: String,
    loadSegment: (String) -> String,
): String? {
    val cueSegments = playlist.materializedSubtitleSegments(playlistUrl, loadSegment)
    val nonBlankSegments = cueSegments.filter { segment -> segment.body.isNotBlank() }
    if (nonBlankSegments.isEmpty()) return null

    val topLevelBlocks = cueSegments
        .flatMap { segment -> segment.topLevelBlocks }
        .distinct()
    val shouldShiftCueTimes = nonBlankSegments.shouldShiftWebVttCueTimes()
    val cues = nonBlankSegments
        .map { segment -> segment.normalizedWebVttCueBody(shouldShiftCueTimes).trim() }
        .filter(String::isNotBlank)
    return buildWebVttDocument(topLevelBlocks, cues)
}

private fun String.materializedSubtitleSegments(
    playlistUrl: String,
    loadSegment: (String) -> String,
): List<MaterializedSubtitleSegment> {
    if (trimStart().startsWith("WEBVTT", ignoreCase = true)) {
        return listOf(webVttCueBody().toMaterializedSegment())
    }
    return hlsSubtitleSegments(playlistUrl).map { segment ->
        val body = loadSegment(segment.url).webVttCueBody()
        body.toMaterializedSegment(
            offsetMs = segment.offsetMs,
            durationMs = segment.durationMs,
        )
    }
}

private fun WebVttCueBody.toMaterializedSegment(
    offsetMs: Long = 0L,
    durationMs: Long = 0L,
): MaterializedSubtitleSegment {
    return MaterializedSubtitleSegment(
        body = text,
        offsetMs = offsetMs,
        durationMs = durationMs,
        localMapMs = localMapMs,
        topLevelBlocks = topLevelBlocks,
    )
}

private fun buildWebVttDocument(
    topLevelBlocks: List<String>,
    cues: List<String>,
): String {
    return buildString {
        append("WEBVTT\n\n")
        if (topLevelBlocks.isNotEmpty()) {
            append(topLevelBlocks.joinToString("\n\n"))
            append("\n\n")
        }
        append(cues.joinToString("\n\n"))
        append('\n')
    }
}
