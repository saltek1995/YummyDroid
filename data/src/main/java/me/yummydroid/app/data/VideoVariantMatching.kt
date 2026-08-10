package me.yummydroid.app.data

import java.util.Locale

val VideoVariant.matchingVoiceTitle: String
    get() = matchingDubbingTitle.ifBlank { "Voice" }

val VideoVariant.matchingDisplayVoiceTitle: String
    get() = matchingDubbingTitle
        .ifBlank { player.cleanVideoSourceLabel() }
        .ifBlank { matchingVoiceTitle }

val VideoVariant.matchingDubbingTitle: String
    get() = dubbing.cleanVideoSourceLabel()
        .takeUnless { it.isKnownPlayerLabel() }
        .orEmpty()

val VideoVariant.matchingVoiceKey: String
    get() = matchingDubbingTitle.normalizedVoiceKey()

val VideoVariant.matchingSourceKey: String
    get() = listOf(player.cleanVideoSourceLabel(), matchingVoiceKey)
        .joinToString("|")
        .normalizedVoiceKey()

val VideoVariant.matchingEpisodeKey: String
    get() = episode.normalizedEpisodeKey()
        ?: index.takeIf { it > 0 }?.let { "index:$it" }
        ?: "video:$id"

val VideoVariant.matchingPlayerKey: String
    get() = player.cleanVideoSourceLabel().normalizedVoiceKey()

fun VideoVariant.isSameEpisodeAs(other: VideoVariant): Boolean {
    return matchingEpisodeKey == other.matchingEpisodeKey
}

fun VideoVariant.hasSameVoiceAs(other: VideoVariant): Boolean {
    return matchingVoiceKey == other.matchingVoiceKey
}

fun VideoVariant.episodeOrderValue(): Double? {
    return episode
        .trim()
        .replace(',', '.')
        .toDoubleOrNull()
        ?: index.takeIf { it > 0 }?.toDouble()
}

val VideoVariant.downloadVoiceSlotKey: String
    get() = listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingVoiceKey,
    ).joinToString("|") { it.trim().lowercase(Locale.ROOT) }

val VideoVariant.sourceSlotKey: String
    get() = listOf(
        animeId.toString(),
        matchingEpisodeKey,
        matchingPlayerKey,
        matchingVoiceKey,
    ).joinToString("|") { it.trim().lowercase(Locale.ROOT) }

val VideoVariant.downloadEpisodeSlotKey: String
    get() = matchingEpisodeKey

fun OfflineVideoFile.matchesPreferredQuality(preferredQuality: PreferredQuality): Boolean {
    val preferredHeight = preferredQuality.height ?: return true
    return qualityHeight() == preferredHeight
}

fun VideoVariant.downloadedEpisodeCountForVoice(variants: List<VideoVariant>): Int {
    val voiceKey = downloadPlanVoiceKey
    return variants
        .asSequence()
        .filter { it.downloadPlanVoiceKey == voiceKey && it.isOfflineAvailable }
        .map { it.episodeDownloadSlotKey() }
        .distinct()
        .count()
}

private fun VideoVariant.episodeDownloadSlotKey(): String = matchingEpisodeKey

private fun String.normalizedEpisodeKey(): String? {
    val raw = trim()
    if (raw.isBlank()) return null
    val numericTokens = episodeNumberRegex.findAll(raw)
        .map { it.groupValues.getOrNull(1).orEmpty().normalizedEpisodeNumber() }
        .filter { it.isNotBlank() }
        .distinct()
        .toList()
    if (numericTokens.size == 1) return numericTokens.single()
    return raw
        .lowercase(Locale.ROOT)
        .replace('\u0451', '\u0435')
        .replace(whitespaceRegex, " ")
        .trim()
        .takeIf { it.isNotBlank() }
}

private fun String.normalizedEpisodeNumber(): String {
    val normalized = replace(',', '.')
    if (!normalized.contains('.')) return normalized.trimStart('0').ifBlank { "0" }
    val compact = normalized
        .trimEnd('0')
        .trimEnd('.')
    return compact.trimStart('0').ifBlank { "0" }
}

private val episodeNumberRegex = Regex("""(?<!\d)(\d+(?:[.,]\d+)?)(?!\d)""")
