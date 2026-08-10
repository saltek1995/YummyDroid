package me.yummydroid.app.data

internal class SubtitleHlsMetadataParser {
    fun extractTracks(body: String, baseUrl: String): SubtitleDetection {
        val tracks = mutableListOf<ResolvedSubtitleTrack>()
        val embeddedTracks = mutableListOf<ResolvedEmbeddedSubtitleTrack>()
        var hasEmbeddedSubtitles = false
        body.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (!line.startsWith("#EXT-X-MEDIA", ignoreCase = true)) return@forEach
            when (line.hlsAttribute("TYPE").orEmpty().uppercase()) {
                "SUBTITLES" -> {
                    val uri = line.hlsAttribute("URI") ?: return@forEach
                    if (uri.isBlank()) return@forEach
                    val resolvedUri = uri.resolveUrlAgainst(baseUrl)
                    tracks += ResolvedSubtitleTrack(
                        uri = resolvedUri,
                        label = line.hlsAttribute("NAME").orEmpty()
                            .ifBlank { line.hlsAttribute("GROUP-ID").orEmpty() }
                            .ifBlank { resolvedUri.subtitleLabelFromUrl() },
                        language = line.hlsAttribute("LANGUAGE"),
                        mimeType = resolvedUri.subtitleMimeTypeFromUrl() ?: "application/x-mpegURL",
                    )
                }
                "CLOSED-CAPTIONS" -> {
                    hasEmbeddedSubtitles = true
                    val name = line.hlsAttribute("NAME").orEmpty()
                    val groupId = line.hlsAttribute("GROUP-ID").orEmpty()
                    val instreamId = line.hlsAttribute("INSTREAM-ID").orEmpty()
                    embeddedTracks += ResolvedEmbeddedSubtitleTrack(
                        id = instreamId.ifBlank { groupId }.ifBlank { name },
                        label = name.ifBlank { groupId },
                        language = line.hlsAttribute("LANGUAGE"),
                    )
                }
            }
        }
        return SubtitleDetection(
            tracks = tracks.normalizedSubtitleTracks(),
            embeddedSubtitles = embeddedTracks.normalizedEmbeddedSubtitleTracks(),
            hasEmbeddedSubtitles = hasEmbeddedSubtitles,
        )
    }
}
