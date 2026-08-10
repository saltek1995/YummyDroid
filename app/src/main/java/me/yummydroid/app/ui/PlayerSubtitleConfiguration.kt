package me.yummydroid.app.ui

import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.util.Locale
import me.yummydroid.app.data.ResolvedEmbeddedSubtitleTrack
import me.yummydroid.app.data.ResolvedSubtitleTrack

internal fun ResolvedSubtitleTrack.toMedia3SubtitleConfiguration(): MediaItem.SubtitleConfiguration? {
    val cleanUri = uri.takeIf { it.isNotBlank() } ?: return null
    if (!isMaterializedSubtitleTrack()) return null
    val resolvedMimeType = subtitleMimeTypeForMedia3(cleanUri, mimeType)
        ?.takeIf { it.isSideLoadedSubtitleMimeType() }
        ?: return null
    return MediaItem.SubtitleConfiguration.Builder(cleanUri.toUri()).apply {
        setMimeType(resolvedMimeType)
        language?.takeIf { it.isNotBlank() }?.let(::setLanguage)
        setId(media3SubtitleId())
        subtitleLabelForMedia3(label, cleanUri).takeIf { it.isNotBlank() }?.let { resolvedLabel ->
            setLabel(resolvedLabel)
        }
        setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
    }.build()
}

internal fun ResolvedSubtitleTrack.toMedia3SubtitleReference(): ResolvedSubtitleTrackReference? {
    val cleanUri = uri.takeIf { it.isNotBlank() } ?: return null
    if (!isMaterializedSubtitleTrack()) return null
    return ResolvedSubtitleTrackReference(
        media3Id = media3SubtitleId(),
        label = subtitleLabelForMedia3(label, cleanUri),
    )
}

internal fun ResolvedSubtitleTrack.toSubtitleDisplayReference(sourceIndex: Int): ResolvedSubtitleTrackReference? {
    val cleanUri = uri.takeIf { it.isNotBlank() } ?: return null
    val resolvedLabel = subtitleLabelForMedia3(label, cleanUri)
        .takeIf { it.isNotBlank() }
        ?: return null
    return ResolvedSubtitleTrackReference(
        media3Id = if (isMaterializedSubtitleTrack()) media3SubtitleId() else "",
        label = resolvedLabel,
        language = language,
        sourceIndex = sourceIndex,
    )
}

internal fun ResolvedEmbeddedSubtitleTrack.toSubtitleDisplayReference(sourceIndex: Int): ResolvedSubtitleTrackReference? {
    val resolvedLabel = label.subtitleUserVisibleLabel()
        ?: language?.subtitleLanguageDisplayName()
        ?: id.subtitleUserVisibleLabel()
        ?: return null
    return ResolvedSubtitleTrackReference(
        media3Id = id,
        label = resolvedLabel,
        language = language,
        sourceIndex = sourceIndex,
    )
}

internal fun ResolvedSubtitleTrack.isMaterializedSubtitleTrack(): Boolean {
    val cleanUri = uri.takeIf { it.isNotBlank() } ?: return false
    return cleanUri.startsWith("file:", ignoreCase = true) ||
        cleanUri.startsWith("content:", ignoreCase = true)
}

private fun ResolvedSubtitleTrack.media3SubtitleId(): String {
    val cleanUri = uri.trim()
    return listOf(
        "external-subtitle",
        cleanUri,
        language.orEmpty(),
        subtitleLabelForMedia3(label, cleanUri),
    ).joinToString(":")
}

internal fun subtitleLabelForMedia3(label: String, uri: String): String {
    label.subtitleUserVisibleLabel()?.let { return it }
    return uri.subtitleIdentifierLabel()
}

private fun String.subtitleLanguageDisplayName(): String? {
    if (isBlank() || this == C.LANGUAGE_UNDETERMINED) return null
    return runCatching { Locale.forLanguageTag(this).getDisplayLanguage(Locale.getDefault()) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
}

internal fun String.subtitleUserVisibleLabel(): String? {
    val cleaned = trim().takeIf { it.isNotBlank() } ?: return null
    val lower = cleaned.lowercase(Locale.ROOT)
    val looksLikeCacheHash = lower.startsWith("subtitle_") &&
        lower.removePrefix("subtitle_").all { it in '0'..'9' || it in 'a'..'f' } &&
        lower.length >= 24
    val looksLikeNumericTrackId = lower.all(Char::isDigit)
    val looksLikeOpaqueTrackId = lower.length in 4..16 &&
        lower.all { it in '0'..'9' || it in 'a'..'f' } &&
        lower.any(Char::isDigit) &&
        lower.any { it in 'a'..'f' }
    return cleaned.takeUnless { looksLikeCacheHash || looksLikeNumericTrackId || looksLikeOpaqueTrackId }
}

internal fun subtitleMimeTypeForMedia3(uri: String, mimeType: String?): String? {
    val source = mimeType?.takeIf { it.isNotBlank() } ?: uri
    val lower = source.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
    return when {
        "mpegurl" in lower || lower.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        "subrip" in lower || lower.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
        "text/vtt" in lower || lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
        "text/x-ssa" in lower || lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
        "ttml" in lower || lower.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
        else -> null
    }
}

internal fun String.isSideLoadedSubtitleMimeType(): Boolean {
    return this == MimeTypes.TEXT_VTT ||
        this == MimeTypes.APPLICATION_SUBRIP ||
        this == MimeTypes.TEXT_SSA ||
        this == MimeTypes.APPLICATION_TTML
}
