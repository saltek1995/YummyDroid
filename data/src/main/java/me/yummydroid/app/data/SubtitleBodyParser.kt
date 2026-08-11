package me.yummydroid.app.data

internal data class HlsSubtitleSegment(
    val url: String,
    val offsetMs: Long,
    val durationMs: Long,
)

internal data class MaterializedSubtitleSegment(
    val body: String,
    val offsetMs: Long,
    val durationMs: Long,
    val localMapMs: Long? = null,
    val topLevelBlocks: List<String> = emptyList(),
)

internal data class PlayableSubtitleBody(
    val text: String,
    val mimeType: String,
    val fileExtension: String = mimeType.subtitleFileExtension(),
)

internal data class WebVttCueBody(
    val text: String,
    val localMapMs: Long? = null,
    val topLevelBlocks: List<String> = emptyList(),
)

private fun String.subtitleFileExtension(): String {
    return when {
        this == "text/x-ssa" -> "ass"
        this == "application/ttml+xml" -> "ttml"
        this == "application/x-subrip" -> "srt"
        else -> "vtt"
    }
}
