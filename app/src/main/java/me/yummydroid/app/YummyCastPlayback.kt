package me.yummydroid.app

import android.content.Context
import android.view.Menu
import androidx.annotation.OptIn
import androidx.media3.cast.DefaultCastOptionsProvider
import androidx.media3.cast.MediaRouteButtonFactory
import androidx.media3.common.util.UnstableApi
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.CastMediaOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity
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

@OptIn(UnstableApi::class)
class YummyCastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        val configuredReceiverId = BuildConfig.CAST_RECEIVER_APP_ID.trim()
        val notificationOptions = NotificationOptions.Builder()
            .setActions(
                listOf(
                    MediaIntentReceiver.ACTION_SKIP_NEXT,
                    MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
                    MediaIntentReceiver.ACTION_STOP_CASTING,
                ),
                intArrayOf(1, 2),
            )
            .setTargetActivityClassName(YummyCastExpandedControlsActivity::class.java.name)
            .build()
        val mediaOptions = CastMediaOptions.Builder()
            .setNotificationOptions(notificationOptions)
            .setExpandedControllerActivityClassName(YummyCastExpandedControlsActivity::class.java.name)
            .build()
        return CastOptions.Builder()
            .setReceiverApplicationId(
                configuredReceiverId.ifBlank {
                    DefaultCastOptionsProvider.APP_ID_DEFAULT_RECEIVER_WITH_DRM
                },
            )
            .setResumeSavedSession(true)
            .setEnableReconnectionService(true)
            .setStopReceiverApplicationWhenEndingSession(true)
            .setRemoteToLocalEnabled(true)
            .setCastMediaOptions(mediaOptions)
            .build()
    }

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()
}

@OptIn(UnstableApi::class)
class YummyCastExpandedControlsActivity : ExpandedControllerActivity() {
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.cast_expanded_controller, menu)
        MediaRouteButtonFactory.setUpMediaRouteButton(this, menu, R.id.cast_media_route)
        return true
    }
}
