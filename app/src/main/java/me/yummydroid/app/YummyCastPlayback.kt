package me.yummydroid.app

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import org.json.JSONObject

private const val YUMMY_CAST_PAYLOAD_KEY = "yummydroid"
private const val YUMMY_CAST_PAYLOAD_VERSION = 1

private val YummyCastJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

@Serializable
internal data class YummyCastPlaybackPayload(
    val version: Int = YUMMY_CAST_PAYLOAD_VERSION,
    val animeTitle: String,
    val video: VideoVariant,
    val episodeVideos: List<VideoVariant>,
    val preferredQualityName: String,
    val skipOpeningsAndEndings: Boolean = true,
    val autoplayNextEpisode: Boolean = true,
    val hasPreviousEpisode: Boolean = false,
    val hasNextEpisode: Boolean = false,
) {
    val preferredQuality: PreferredQuality
        get() = PreferredQuality.fromName(preferredQualityName) ?: PreferredQuality.Auto
}

internal fun createYummyCastPlaybackPayload(
    animeTitle: String,
    currentVideo: VideoVariant,
    allVideos: List<VideoVariant>,
    preferredQuality: PreferredQuality,
    skipOpeningsAndEndings: Boolean = true,
    autoplayNextEpisode: Boolean = true,
    hasPreviousEpisode: Boolean = false,
    hasNextEpisode: Boolean = false,
): YummyCastPlaybackPayload {
    val remoteCurrent = currentVideo.withoutDeviceLocalPlayback()
    val episodeVideos = selectYummyCastEpisodeVideos(currentVideo, allVideos)
        .map(VideoVariant::withoutDeviceLocalPlayback)
        .ifEmpty { listOf(remoteCurrent) }
    return YummyCastPlaybackPayload(
        animeTitle = animeTitle,
        video = remoteCurrent,
        episodeVideos = episodeVideos,
        preferredQualityName = preferredQuality.name,
        skipOpeningsAndEndings = skipOpeningsAndEndings,
        autoplayNextEpisode = autoplayNextEpisode,
        hasPreviousEpisode = hasPreviousEpisode,
        hasNextEpisode = hasNextEpisode,
    )
}

internal fun YummyCastPlaybackPayload.withDirectPlayback(
    playbackUrl: String,
    mimeType: String?,
): YummyCastPlaybackPayload {
    val directVideo = video.copy(
        localPlaybackUrl = playbackUrl,
        localMimeType = mimeType,
        localFiles = emptyList(),
    )
    return copy(video = directVideo, episodeVideos = listOf(directVideo))
}

internal fun selectYummyCastEpisodeVideos(
    currentVideo: VideoVariant,
    allVideos: List<VideoVariant>,
): List<VideoVariant> {
    val voiceVideos = allVideos
        .asSequence()
        .filter { it.animeId == currentVideo.animeId }
        .filter { it.matchingVoiceKey == currentVideo.matchingVoiceKey }
        .toList()
        .ifEmpty { listOf(currentVideo) }
    return voiceVideos
        .groupBy(VideoVariant::matchingEpisodeKey)
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.groupKey == currentVideo.groupKey) 0 else 1 }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedWith(compareBy<VideoVariant> { it.index }.thenBy { it.id })
}

internal fun JSONObject.withYummyCastPayload(payload: YummyCastPlaybackPayload): JSONObject {
    val result = JSONObject(toString())
    result.put(
        YUMMY_CAST_PAYLOAD_KEY,
        JSONObject(encodeYummyCastPlaybackPayload(payload)),
    )
    return result
}

internal fun JSONObject?.yummyCastPlaybackPayloadOrNull(): YummyCastPlaybackPayload? {
    val encoded = this?.optJSONObject(YUMMY_CAST_PAYLOAD_KEY)?.toString() ?: return null
    return decodeYummyCastPlaybackPayload(encoded)
}

internal fun encodeYummyCastPlaybackPayload(payload: YummyCastPlaybackPayload): String {
    return YummyCastJson.encodeToString(payload)
}

internal fun decodeYummyCastPlaybackPayload(encoded: String): YummyCastPlaybackPayload? {
    return runCatching {
        YummyCastJson.decodeFromString<YummyCastPlaybackPayload>(encoded)
    }.getOrNull()?.takeIf { it.version == YUMMY_CAST_PAYLOAD_VERSION }
}

private fun VideoVariant.withoutDeviceLocalPlayback(): VideoVariant {
    return copy(
        localPlaybackUrl = "",
        localMimeType = null,
        localBytes = 0L,
        localFiles = emptyList(),
    )
}
