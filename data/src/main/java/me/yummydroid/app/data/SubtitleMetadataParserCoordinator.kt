package me.yummydroid.app.data

import kotlinx.serialization.json.Json

internal class SubtitleMetadataParser(
    fallbackSiteBaseUrl: () -> String,
    json: Json,
) {
    private val dashParser = SubtitleDashMetadataParser()
    private val hlsParser = SubtitleHlsMetadataParser()
    private val trackClassifier = SubtitleTrackClassifier(fallbackSiteBaseUrl)
    private val structuredParser = SubtitleStructuredMetadataParser(json, trackClassifier)

    fun extractTracks(body: String, baseUrl: String): List<ResolvedSubtitleTrack> {
        val normalizedBody = body.normalizeSubtitleMetadataBody()
        return (
            trackClassifier.extractDirectTracks(normalizedBody, baseUrl) +
                structuredParser.extractTracks(normalizedBody, baseUrl)
            ).normalizedSubtitleTracks()
    }

    fun extractDashEmbeddedTracks(body: String): List<ResolvedEmbeddedSubtitleTrack> {
        return dashParser.extractTracks(body)
    }

    fun extractHlsTracks(body: String, baseUrl: String): SubtitleDetection {
        return hlsParser.extractTracks(body, baseUrl)
    }

    fun directTrack(url: String): ResolvedSubtitleTrack? = trackClassifier.directTrack(url)

    fun potentialTrack(url: String): ResolvedSubtitleTrack? = trackClassifier.potentialTrack(url)

    fun isResolvableCandidate(url: String): Boolean = trackClassifier.isResolvableCandidate(url)
}
