package me.yummydroid.app.data

internal fun String.toPlayableSubtitleBody(
    mimeType: String? = null,
    uri: String = "",
): PlayableSubtitleBody? {
    val normalized = normalizedSubtitleBody() ?: return null
    val typeHint = SubtitleTypeHint.from(mimeType, uri)

    normalized.jsonSubtitleToWebVtt()?.asVerifiedWebVtt()?.let { return it }
    normalized.asNativeWebVtt(uri)?.let { return it }
    normalized.asNativeAss(typeHint, uri)?.let { return it }
    normalized.asNativeTtml(typeHint, uri)?.let { return it }
    return normalized.convertToWebVtt(typeHint)?.asVerifiedWebVtt()
}

private fun String.normalizedSubtitleBody(): String? {
    return replace("\uFEFF", "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .trim()
        .takeIf(String::isNotBlank)
}

private fun String.asNativeWebVtt(uri: String): PlayableSubtitleBody? {
    if (!startsWith("WEBVTT", ignoreCase = true)) return null
    if (!hasSubtitleCues(mimeType = "text/vtt", uri = uri)) return null
    return PlayableSubtitleBody(
        text = withNonOverlappingWebVttCueSettings(),
        mimeType = "text/vtt",
        fileExtension = "vtt",
    )
}

private fun String.asNativeAss(typeHint: SubtitleTypeHint, uri: String): PlayableSubtitleBody? {
    if (!typeHint.isAss && !looksLikeAssSubtitle()) return null
    if (!hasSubtitleCues(mimeType = "text/x-ssa", uri = uri)) return null
    return PlayableSubtitleBody(
        text = withAssHeaderIfMissing(),
        mimeType = "text/x-ssa",
        fileExtension = typeHint.assExtension,
    )
}

private fun String.asNativeTtml(typeHint: SubtitleTypeHint, uri: String): PlayableSubtitleBody? {
    if (!typeHint.isTtml && !looksLikeTtmlSubtitle()) return null
    if (!hasSubtitleCues(mimeType = "application/ttml+xml", uri = uri)) return null
    return PlayableSubtitleBody(
        text = this,
        mimeType = "application/ttml+xml",
        fileExtension = typeHint.ttmlExtension,
    )
}

private fun String.convertToWebVtt(typeHint: SubtitleTypeHint): String? {
    return when {
        typeHint.isAss -> assToWebVtt()
        typeHint.isTtml -> ttmlToWebVtt()
        else -> timedSubtitleTextToWebVtt()
    }
}

private fun String.asVerifiedWebVtt(): PlayableSubtitleBody? {
    return withNonOverlappingWebVttCueSettings()
        .takeIf { it.hasSubtitleCues(mimeType = "text/vtt") }
        ?.let { PlayableSubtitleBody(text = it, mimeType = "text/vtt") }
}

private data class SubtitleTypeHint(private val value: String) {
    val isAss: Boolean
        get() = "x-ssa" in value || value.endsWith(".ass") || value.endsWith(".ssa")

    val isTtml: Boolean
        get() = "ttml" in value ||
            "dfxp" in value ||
            value.endsWith(".ttml") ||
            value.endsWith(".dfxp")

    val assExtension: String
        get() = if (value.endsWith(".ssa")) "ssa" else "ass"

    val ttmlExtension: String
        get() = if (value.endsWith(".dfxp")) "dfxp" else "ttml"

    companion object {
        fun from(mimeType: String?, uri: String): SubtitleTypeHint {
            val value = listOf(mimeType.orEmpty(), uri.substringBefore('?').substringBefore('#'))
                .joinToString(" ")
                .lowercase()
            return SubtitleTypeHint(value)
        }
    }
}
