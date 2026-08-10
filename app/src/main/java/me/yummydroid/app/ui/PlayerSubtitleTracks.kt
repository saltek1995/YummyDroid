package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import java.util.Locale

@OptIn(UnstableApi::class)
internal fun ExoPlayer.selectSubtitle(option: SubtitleOption) {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .addOverride(TrackSelectionOverride(option.group.mediaTrackGroup, option.trackIndex))
        .build()
}

@OptIn(UnstableApi::class)
internal fun ExoPlayer.disableSubtitles() {
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        .build()
}

internal data class SubtitleOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val selectionFlags: Int,
    val key: String,
    val isResolvedTrack: Boolean = false,
)

internal data class ResolvedSubtitleTrackReference(
    val media3Id: String,
    val label: String,
    val language: String? = null,
    val sourceIndex: Int? = null,
)

private data class SubtitleTrackCandidate(
    val group: Tracks.Group,
    val trackIndex: Int,
    val format: androidx.media3.common.Format,
)

@OptIn(UnstableApi::class)
internal fun Tracks.subtitleOptions(
    texts: PlayerControlTexts,
    resolvedSubtitles: List<ResolvedSubtitleTrackReference>? = null,
): List<SubtitleOption> {
    val candidates = groups
        .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSupported(trackIndex) }
                .map { trackIndex -> SubtitleTrackCandidate(group, trackIndex, group.getTrackFormat(trackIndex)) }
        }
    val resolvedSubtitleReferences = resolvedSubtitles.orEmpty()
    val fallbackResolvedSubtitle = singleResolvedSubtitleFallback(
        resolvedSubtitles = resolvedSubtitleReferences,
        media3SubtitleTrackCount = candidates.size,
    )
    val options = candidates
        .mapIndexed { candidateIndex, candidate ->
            val format = candidate.format
            val trackIndex = candidate.trackIndex
            val media3Label = format.subtitleLabel(texts, trackIndex)
            val resolvedSubtitle = format.matchingResolvedSubtitleReference(
                resolvedSubtitles = resolvedSubtitleReferences,
            )
                ?: orderedResolvedSubtitleFallback(
                    resolvedSubtitles = resolvedSubtitleReferences,
                    media3SubtitleTrackCount = candidates.size,
                    media3SubtitleTrackIndex = candidateIndex,
                )
                ?: fallbackResolvedSubtitle
            val label = media3Label.subtitleDisplayLabel(
                texts = texts,
                trackIndex = trackIndex,
                resolvedSubtitleLabel = resolvedSubtitle?.label,
            )
            SubtitleOption(
                group = candidate.group,
                trackIndex = trackIndex,
                label = label,
                language = format.language,
                selectionFlags = format.selectionFlags,
                key = "${format.id.orEmpty()}:${format.language.orEmpty()}:${format.label.orEmpty()}:$trackIndex",
                isResolvedTrack = resolvedSubtitle != null,
            )
                }
        .distinctBy { it.subtitleOptionIdentity() }
    val resolvedOptions = options.filter { option -> option.isResolvedTrack }
    val visibleOptions = if (resolvedSubtitles != null) {
        resolvedOptions
    } else {
        options
    }
    return visibleOptions
        .sortedWith(compareByDescending<SubtitleOption> { it.isResolvedTrack }.thenBy { it.label })
}

internal fun singleResolvedSubtitleFallback(
    resolvedSubtitles: List<ResolvedSubtitleTrackReference>,
    media3SubtitleTrackCount: Int,
): ResolvedSubtitleTrackReference? {
    return resolvedSubtitles.singleOrNull().takeIf { media3SubtitleTrackCount == 1 }
}

internal fun orderedResolvedSubtitleFallback(
    resolvedSubtitles: List<ResolvedSubtitleTrackReference>,
    media3SubtitleTrackCount: Int,
    media3SubtitleTrackIndex: Int,
): ResolvedSubtitleTrackReference? {
    if (media3SubtitleTrackCount <= 0) return null
    val orderedResolvedSubtitles = resolvedSubtitles
        .filter { subtitle -> subtitle.label.subtitleUserVisibleLabel() != null }
        .sortedBy { subtitle -> subtitle.sourceIndex ?: Int.MAX_VALUE }
    if (orderedResolvedSubtitles.size != media3SubtitleTrackCount) return null
    return orderedResolvedSubtitles.getOrNull(media3SubtitleTrackIndex)
}

internal fun List<SubtitleOption>.defaultSubtitleOption(): SubtitleOption? {
    return firstOrNull { option -> (option.selectionFlags and C.SELECTION_FLAG_DEFAULT) != 0 }
        ?: firstOrNull()
}

@OptIn(UnstableApi::class)
internal fun Tracks.currentSubtitleKey(): String? {
    return groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    "${format.id.orEmpty()}:${format.language.orEmpty()}:${format.label.orEmpty()}:$trackIndex"
                }
        }
        .firstOrNull()
}

@OptIn(UnstableApi::class)
internal fun androidx.media3.common.Format.subtitleLabel(
    texts: PlayerControlTexts,
    trackIndex: Int,
): String {
    val explicitLabel = label?.subtitleUserVisibleLabel()
    val idLabel = id
        ?.takeIf { it.isNotBlank() }
        ?.subtitleIdentifierLabel()
        ?.takeIf { it.isNotBlank() }
    val languageLabel = language
        ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
        ?.let { languageTag ->
            runCatching { Locale.forLanguageTag(languageTag).getDisplayLanguage(Locale.getDefault()) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
    return explicitLabel
        ?: idLabel
        ?: languageLabel
        ?: "${texts.subtitles} ${trackIndex + 1}"
}

internal fun String.subtitleDisplayLabel(
    texts: PlayerControlTexts,
    trackIndex: Int,
    resolvedSubtitleLabel: String? = null,
): String {
    val cleaned = subtitleUserVisibleLabel()
    val resolvedLabel = resolvedSubtitleLabel?.subtitleUserVisibleLabel()
    return when {
        cleaned == null -> resolvedLabel ?: "${texts.subtitles} ${trackIndex + 1}"
        cleaned.isGenericSubtitleLabel(texts, trackIndex) -> resolvedLabel ?: cleaned
        else -> cleaned
    }
}

internal fun androidx.media3.common.Format.matchingResolvedSubtitleReference(
    resolvedSubtitles: List<ResolvedSubtitleTrackReference>,
): ResolvedSubtitleTrackReference? {
    if (resolvedSubtitles.isEmpty()) return null
    id?.takeIf { it.isNotBlank() }?.let { currentId ->
        resolvedSubtitles.firstOrNull { it.media3Id == currentId }?.let { return it }
    }
    val normalizedTokens = subtitleIdentityTokens()
        .mapNotNull { token -> token.subtitleUserVisibleLabel() }
        .map { token -> token.normalizedSubtitleIdentityToken() }
        .filter { token -> token.isNotBlank() }
        .toSet()
    if (normalizedTokens.isEmpty()) return null
    return resolvedSubtitles.firstOrNull { subtitle ->
        val normalizedLabel = subtitle.label
            .subtitleUserVisibleLabel()
            ?.normalizedSubtitleIdentityToken()
        normalizedLabel != null && normalizedLabel in normalizedTokens
    }
}

private fun androidx.media3.common.Format.subtitleIdentityTokens(): List<String> {
    return listOfNotNull(
        this.label,
        id?.subtitleIdentifierLabel(),
        this.label?.subtitleIdentifierLabel(),
    )
}

internal fun String.subtitleIdentifierLabel(): String {
    val fileName = substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .takeIf { it.isNotBlank() }
        ?: return ""
    val label = fileName.substringBeforeLast('.', missingDelimiterValue = fileName)
    return label
        .subtitleUserVisibleLabel()
        ?.replace('_', ' ')
        ?.replace('-', ' ')
        ?.takeIf {
            (
                contains("file:", ignoreCase = true) ||
                    '/' in this ||
                    '\\' in this
                )
        }
        .orEmpty()
}

private fun String.isGenericSubtitleLabel(texts: PlayerControlTexts, trackIndex: Int): Boolean {
    val normalized = normalizedSubtitleIdentityToken()
    return normalized == "${texts.subtitles}${trackIndex + 1}".normalizedSubtitleIdentityToken() ||
        normalized == "subtitles${trackIndex + 1}" ||
        normalized == "subtitle${trackIndex + 1}" ||
        normalized == "captions${trackIndex + 1}" ||
        normalized == "caption${trackIndex + 1}"
}

private fun String.normalizedSubtitleIdentityToken(): String {
    return trim().lowercase(Locale.ROOT).replace(Regex("""\s+"""), "")
}

