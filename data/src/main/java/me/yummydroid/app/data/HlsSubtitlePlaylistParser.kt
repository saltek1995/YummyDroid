package me.yummydroid.app.data

internal fun String.isHlsPlaylistUrl(): Boolean {
    val lower = substringBefore('?').substringBefore('#').lowercase()
    return lower.endsWith(".m3u8") || "mpegurl" in lower
}

internal fun String.hlsSubtitleSegments(baseUrl: String): List<HlsSubtitleSegment> {
    val segments = mutableListOf<HlsSubtitleSegment>()
    var offsetMs = 0L
    var pendingDurationMs = 0L

    lineSequence().forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.startsWith("#EXTINF", ignoreCase = true) -> {
                pendingDurationMs = line.substringAfter(':')
                    .substringBefore(',')
                    .toDoubleOrNull()
                    ?.let { (it * 1000.0).toLong() }
                    ?: 0L
            }
            line.isNotBlank() && !line.startsWith("#") -> {
                segments += HlsSubtitleSegment(
                    url = line.resolveUrlAgainst(baseUrl),
                    offsetMs = offsetMs,
                    durationMs = pendingDurationMs,
                )
                offsetMs += pendingDurationMs
                pendingDurationMs = 0L
            }
        }
    }

    return segments
}

internal fun String.looksLikeStandaloneHlsWebVttSegment(): Boolean {
    val normalized = replace("\uFEFF", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trimStart()
    if (!normalized.startsWith("WEBVTT", ignoreCase = true)) return false

    return normalized
        .lineSequence()
        .drop(1)
        .takeWhile { it.isNotBlank() }
        .any { line -> line.trim().startsWith("X-TIMESTAMP-MAP", ignoreCase = true) }
}
