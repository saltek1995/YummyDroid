package me.yummydroid.app.data

import kotlinx.serialization.Serializable

@Serializable
enum class VideoSkipKind(
    val title: String,
) {
    Opening("opening"),
    Ending("ending"),
}

@Serializable
data class VideoSkipSegment(
    val kind: VideoSkipKind,
    val startMs: Long,
    val endMs: Long,
) {
    val key: String = "${kind.name}:$startMs:$endMs"

    fun isActive(positionMs: Long): Boolean {
        return startMs >= 0L && endMs > startMs && positionMs in startMs until endMs
    }
}

fun List<VideoSkipSegment>.normalizedSkipSegments(): List<VideoSkipSegment> {
    return asSequence()
        .filter { it.startMs >= 0L && it.endMs > it.startMs }
        .distinctBy { segment -> segment.key }
        .sortedWith(
            compareBy<VideoSkipSegment> { it.startMs }
                .thenBy { it.endMs }
                .thenBy { it.kind.ordinal },
        )
        .toList()
}
