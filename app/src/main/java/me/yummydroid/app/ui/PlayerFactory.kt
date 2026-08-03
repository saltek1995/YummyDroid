package me.yummydroid.app.ui

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import java.util.Locale
import me.yummydroid.app.data.APP_USER_AGENT
import me.yummydroid.app.data.bestSourceQualityPerHeight
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingSourceKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.availableVoiceEpisodeCount
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.sourceEpisodeCounts
import me.yummydroid.app.data.sourceQualitiesForSameEpisodeVoice
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.AppSettingsStorage
import me.yummydroid.app.hasSamePlaybackSourceAs
import me.yummydroid.app.localizedString
import me.yummydroid.app.R
import me.yummydroid.app.sourceSelectionKey
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
internal fun createVideoPlayer(
    context: Context,
    stream: ResolvedVideoStream,
    startPositionMs: Long,
    httpClient: OkHttpClient,
    renderersFactory: DefaultRenderersFactory,
    loadControl: DefaultLoadControl,
): ExoPlayer {
    val userAgent = stream.headers["User-Agent"] ?: APP_USER_AGENT
    val trackSelector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
            .setMaxVideoBitrate(Int.MAX_VALUE)
            .build()
    }
    val httpDataSourceFactory = OkHttpDataSource.Factory(httpClient)
        .setUserAgent(userAgent)
        .setDefaultRequestProperties(stream.headers)
    val dataSourceFactory: DataSource.Factory = if (stream.url.startsWith("file:", ignoreCase = true)) {
        DefaultDataSource.Factory(context)
    } else {
        DefaultDataSource.Factory(context, httpDataSourceFactory)
    }
    return ExoPlayer.Builder(context, renderersFactory)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true,
            )
            setMediaItem(stream.toMediaItem(), startPositionMs.coerceAtLeast(0L))
            playWhenReady = false
            prepare()
        }
}

internal fun ResolvedVideoStream.toMediaItem(): MediaItem {
    val mediaItemBuilder = MediaItem.Builder().setUri(url)
    mimeType?.let { mediaItemBuilder.setMimeType(it) }
    val subtitleConfigurations = subtitles.mapNotNull { it.toMedia3SubtitleConfiguration() }
    if (subtitleConfigurations.isNotEmpty()) {
        mediaItemBuilder.setSubtitleConfigurations(subtitleConfigurations)
    }
    return mediaItemBuilder.build()
}

internal fun VideoVariant.localQualityOptions(): List<QualityOption> {
    return offlineFiles
        .filter { it.playbackUrl.isNotBlank() }
        .sortedWith(compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle })
        .distinctBy { it.qualityOptionIdentity() }
        .map { file ->
            QualityOption(
                group = null,
                trackIndex = -1,
                label = file.qualityDisplayTitle(),
                height = file.qualityHeight(),
                bitrate = 0,
                key = file.qualityKey(),
                localFile = file,
            )
        }
}

internal fun List<VideoVariant>.sourceQualityOptionsFor(currentVideo: VideoVariant): List<QualityOption> {
    return sourceQualitiesForSameEpisodeVoice(currentVideo).sourceQualityOptions()
}

internal fun List<SourceQuality>.sourceQualityOptions(): List<QualityOption> {
    return bestSourceQualityPerHeight().mapNotNull { quality ->
        val preferredQuality = PreferredQuality.fromHeight(quality.height) ?: return@mapNotNull null
        val label = quality.title.takeIf { it.isNotBlank() } ?: preferredQuality.title
        QualityOption(
            group = null,
            trackIndex = -1,
            label = label,
            height = quality.height ?: 0,
            bitrate = quality.bitrate,
            key = "source:${quality.height}",
            preferredQuality = preferredQuality,
        )
    }
}

internal fun VideoVariant.withOfflineFile(file: OfflineVideoFile): VideoVariant {
    val mergedLocalFiles = (localFiles + file)
        .filter { it.playbackUrl.isNotBlank() }
        .distinctBy { it.playbackUrl }
        .sortedWith(compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle })
    return copy(
        localPlaybackUrl = file.playbackUrl,
        localMimeType = file.mimeType,
        localBytes = file.bytes,
        localFiles = mergedLocalFiles,
    )
}

internal fun VideoVariant.withoutLocalPlayback(): VideoVariant {
    return copy(
        localPlaybackUrl = "",
        localMimeType = null,
        localBytes = 0L,
        localFiles = emptyList(),
    )
}

internal fun VideoVariant.selectedLocalQualityKey(streamUrl: String): String? {
    val selectedUrl = streamUrl.takeIf { it.startsWith("file:", ignoreCase = true) }
        ?: localPlaybackUrl.takeIf { it.isNotBlank() }
    return offlineFiles.firstOrNull { it.playbackUrl == selectedUrl }?.qualityKey()
}

internal fun OfflineVideoFile.qualityDisplayTitle(): String {
    return qualityTitle
        .replace('_', ' ')
        .takeIf { it.isNotBlank() }
        ?: "Local"
}

internal fun OfflineVideoFile.qualityKey(): String {
    return "local:${playbackUrl}:${qualityTitle}"
}

internal fun OfflineVideoFile.qualityOptionIdentity(): String {
    return qualityHeight()
        .takeIf { it > 0 }
        ?.let { "height:$it" }
        ?: qualityDisplayTitle().qualityIdentityFromLabel()
}

internal fun QualityOption.qualityOptionIdentity(): String {
    return height
        .takeIf { it > 0 }
        ?.let { "height:$it" }
        ?: label.qualityIdentityFromLabel()
}

internal fun String.qualityIdentityFromLabel(): String {
    val cleaned = replace("downloaded", "", ignoreCase = true)
    val height = Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)p""")
        .find(cleaned)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (height != null) return "height:$height"
    return cleaned
        .lowercase(Locale.ROOT)
        .replace(Regex("""[\s\u2022|:_\-]+"""), "")
        .trim()
}

internal fun QualityOption.withDownloadedLabel(downloadedLabel: String): QualityOption {
    if (localFile == null || label.contains(downloadedLabel, ignoreCase = true)) return this
    return copy(label = "$label \u2022 $downloadedLabel")
}

internal fun mergeVideoQualityOptions(
    onlineOptions: List<QualityOption>,
    localOptions: List<QualityOption>,
    offlineMode: Boolean,
    downloadedLabel: String = defaultPlayerControlTexts.downloaded,
): List<QualityOption> {
    val uniqueLocalOptions = localOptions.distinctBy { it.qualityOptionIdentity() }
    if (offlineMode) {
        return uniqueLocalOptions
            .map { it.withDownloadedLabel(downloadedLabel) }
            .sortedByQuality()
    }

    val localByIdentity = uniqueLocalOptions.associateBy { it.qualityOptionIdentity() }
    val onlineWithLocalFiles = onlineOptions.map { online ->
        val local = localByIdentity[online.qualityOptionIdentity()] ?: return@map online
        online.copy(
            label = if (online.label.contains(downloadedLabel, ignoreCase = true)) {
                online.label
            } else {
                "${online.label} \u2022 $downloadedLabel"
            },
            localFile = local.localFile,
        )
    }
    val onlineIdentities = onlineOptions.mapTo(mutableSetOf()) { it.qualityOptionIdentity() }
    val localOnlyOptions = uniqueLocalOptions
        .filterNot { it.qualityOptionIdentity() in onlineIdentities }
        .map { it.withDownloadedLabel(downloadedLabel) }

    return (onlineWithLocalFiles + localOnlyOptions)
        .distinctBy { it.qualityOptionIdentity() }
        .sortedByQuality()
}

internal fun List<QualityOption>.sortedByQuality(): List<QualityOption> {
    return sortedWith(
        compareByDescending<QualityOption> { it.height.coerceAtLeast(0) }
            .thenBy { it.label },
    )
}

internal fun QualityOption.matchesSelectedQualityKey(selectedQualityKey: String?): Boolean {
    val selected = selectedQualityKey?.takeIf { it.isNotBlank() } ?: return false
    return key == selected ||
        localFile?.qualityKey() == selected ||
        qualityOptionIdentity() == selected ||
        qualityOptionIdentity() == selected.qualityIdentityFromLabel()
}

internal data class SourceOption(
    val key: String,
    val label: String,
    val video: VideoVariant,
)

internal fun List<VideoVariant>.sourceOptionsFor(
    currentVideo: VideoVariant,
    selectedVoiceKey: String?,
    sourceSubtitleSourceKeys: Set<String> = emptySet(),
    sourceSubtitleSelectionKeys: Set<String> = emptySet(),
    sourceSubtitleLabel: String = "Has subtitles",
): List<SourceOption> {
    val requestedVoiceKey = selectedVoiceKey?.takeIf { it.isNotBlank() } ?: currentVideo.matchingVoiceKey
    val voiceKey = requestedVoiceKey
        .takeIf { key ->
            any { candidate ->
                candidate.animeId == currentVideo.animeId &&
                    candidate.matchingVoiceKey == key
            }
        }
        ?: currentVideo.matchingVoiceKey
    val voiceVideos = filter { candidate ->
        candidate.animeId == currentVideo.animeId &&
            candidate.matchingVoiceKey == voiceKey
    }
    val episodeCountsBySource = voiceVideos.sourceEpisodeCounts()
    return filter { candidate ->
        candidate.animeId == currentVideo.animeId &&
            candidate.isSameEpisodeAs(currentVideo) &&
            candidate.matchingVoiceKey == voiceKey
    }
        .ifEmpty { listOf(currentVideo) }
        .sortedWith(
            compareBy<VideoVariant> { sourceProviderRank(it.player) }
                .thenBy { it.playbackSourceLabel(false).lowercase(Locale.ROOT) }
                .thenBy { it.index }
                .thenBy { it.id },
        )
        .distinctBy { it.sourceSelectionKey }
        .map { video ->
            val sourceLabel = video.playbackSourceLabel(false)
            val sourceEpisodeCount = episodeCountsBySource[video.matchingSourceKey].takeIf { it != null && it > 0 }
            val suffixParts = buildList {
                sourceEpisodeCount?.let { add(it.toString()) }
                if (
                    video.matchingSourceKey in sourceSubtitleSourceKeys ||
                    video.sourceSelectionKey in sourceSubtitleSelectionKeys
                ) {
                    add(sourceSubtitleLabel)
                }
            }
            val suffix = suffixParts.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = " (", postfix = ")").orEmpty()
            SourceOption(
                key = video.sourceSelectionKey,
                label = "$sourceLabel$suffix",
                video = video,
            )
        }
}

internal fun List<SourceOption>.withCurrentSubtitleMarker(
    currentVideo: VideoVariant,
    hasSubtitles: Boolean,
    sourceSubtitleLabel: String,
): List<SourceOption> {
    val label = sourceSubtitleLabel.trim()
    if (!hasSubtitles || label.isBlank()) return this
    return map { option ->
        if (!option.video.hasSamePlaybackSourceAs(currentVideo) || option.label.hasSourceOptionSuffixPart(label)) {
            option
        } else {
            option.copy(label = option.label.withSourceOptionSuffixPart(label))
        }
    }
}

private fun String.hasSourceOptionSuffixPart(part: String): Boolean {
    val normalizedPart = part.trim().lowercase(Locale.ROOT)
    if (normalizedPart.isBlank()) return false
    return substringAfterLast('(', missingDelimiterValue = "")
        .substringBeforeLast(')')
        .split(',')
        .map { it.trim().lowercase(Locale.ROOT) }
        .any { it == normalizedPart }
}

private fun String.withSourceOptionSuffixPart(part: String): String {
    val suffix = part.trim()
    if (suffix.isBlank()) return this
    val closingIndex = lastIndexOf(')')
    val openingIndex = lastIndexOf('(')
    return if (endsWith(")") && openingIndex >= 0 && openingIndex < closingIndex) {
        replaceRange(closingIndex, closingIndex, ", $suffix")
    } else {
        "$this ($suffix)"
    }
}

internal fun SubtitleOption.subtitleOptionIdentity(): String {
    val stableKey = key.substringBeforeLast(':', missingDelimiterValue = key)
    return listOf(
        language.orEmpty().lowercase(Locale.ROOT),
        label.lowercase(Locale.ROOT),
        stableKey.lowercase(Locale.ROOT),
    ).joinToString(":").replace(Regex("""\s+"""), "")
}

internal fun SubtitleOption.matchesSelectedSubtitleKey(selectedSubtitleKey: String?): Boolean {
    val selected = selectedSubtitleKey?.takeIf { it.isNotBlank() } ?: return false
    return key == selected || subtitleOptionIdentity() == selected
}

internal fun VideoVariant.playbackSubtitle(
    texts: PlayerControlTexts,
    videos: Collection<VideoVariant> = emptyList(),
): String {
    val voice = dubbing.cleanVideoSourceLabel()
    return listOf(voice, localizedPlaybackEpisodeTitle(texts, videos))
        .filterNot { it.isNullOrBlank() }
        .joinToString(" \u2022 ")
}

private fun VideoVariant.localizedPlaybackEpisodeTitle(
    texts: PlayerControlTexts,
    videos: Collection<VideoVariant>,
): String {
    val episodeNumber = episode.trim()
    if (episodeNumber.isBlank()) return texts.episodeFallback
    val episodeCount = playbackEpisodeCount(videos)
    return if (episodeCount > 0) {
        "${texts.episode} $episodeNumber ${texts.of} $episodeCount"
    } else {
        "${texts.episode} $episodeNumber"
    }
}

private fun VideoVariant.playbackEpisodeCount(videos: Collection<VideoVariant>): Int {
    val candidates = videos.ifEmpty { listOf(this) }
    val sameAnime = candidates.filter { it.animeId == animeId }
    val sameVoice = sameAnime.filter { it.matchingVoiceKey == matchingVoiceKey }
    return sameVoice
        .ifEmpty { sameAnime }
        .ifEmpty { candidates.toList() }
        .availableVoiceEpisodeCount()
}

internal fun findAdjacentPlayerVideo(
    currentVideo: VideoVariant,
    allVideos: List<VideoVariant>,
    selectedGroup: String?,
    forward: Boolean,
): VideoVariant? {
    val videos = allVideos.ifEmpty { listOf(currentVideo) }
    val preferredVoiceKey = selectedGroup
        ?.let { groupKey -> videos.firstOrNull { it.groupKey == groupKey }?.matchingVoiceKey }
        ?: currentVideo.matchingVoiceKey
    val preferredGroupKey = selectedGroup?.takeIf { groupKey -> videos.any { it.groupKey == groupKey } }
        ?: currentVideo.groupKey
    val voiceScopedVideos = videos
        .filter { it.matchingVoiceKey == preferredVoiceKey }
        .ifEmpty { videos }

    val episodeVideos = voiceScopedVideos
        .groupBy { it.matchingEpisodeKey }
        .values
        .mapNotNull { variants ->
            variants.minWithOrNull(
                compareBy<VideoVariant> { if (it.matchingVoiceKey == preferredVoiceKey) 0 else 1 }
                    .thenBy { if (it.groupKey == preferredGroupKey) 0 else 1 }
                    .thenBy { if (it.isOfflineAvailable) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedForPlayer()

    val currentIndex = episodeVideos.indexOfFirst { it.isSameEpisodeAs(currentVideo) }
        .takeIf { it >= 0 }
        ?: return null
    val nextIndex = if (forward) currentIndex + 1 else currentIndex - 1
    return episodeVideos.getOrNull(nextIndex)
}

internal fun showVoiceFallbackToast(
    context: Context,
    previousVideo: VideoVariant,
    nextVideo: VideoVariant,
) {
    if (previousVideo.matchingVoiceKey == nextVideo.matchingVoiceKey) return
    val language = AppSettingsStorage(context).read().contentLanguage
    Toast.makeText(
        context,
        context.localizedString(
            R.string.ui_voice_fallback_toast,
            language,
            previousVideo.matchingVoiceTitle,
            nextVideo.episodeTitle,
            nextVideo.matchingVoiceTitle,
        ),
        Toast.LENGTH_LONG,
    ).show()
}

internal data class PlayerControlTexts(
    val title: String,
    val watch: String,
    val voice: String,
    val source: String,
    val quality: String,
    val subtitles: String,
    val subtitlesOff: String,
    val subscription: String,
    val subscribed: String,
    val skip: String,
    val episode: String,
    val episodeFallback: String,
    val of: String,
    val downloaded: String,
)

internal val defaultPlayerControlTexts = PlayerControlTexts(
    title = "Watch",
    watch = "Watch",
    voice = "Voice",
    source = "Source",
    quality = "Quality",
    subtitles = "Subtitles",
    subtitlesOff = "Off",
    subscription = "Subscription",
    subscribed = "Subscribed",
    skip = "Skip",
    episode = "Episode",
    episodeFallback = "Episode",
    of = "of",
    downloaded = "downloaded",
)

@Composable
internal fun rememberPlayerControlTexts(): PlayerControlTexts {
    return PlayerControlTexts(
        title = uiText(UiStringKey.Watch),
        watch = uiText(UiStringKey.Watch5af041),
        voice = uiText(UiStringKey.Voice),
        source = uiText(UiStringKey.Source),
        quality = uiText(UiStringKey.Quality),
        subtitles = uiText(UiStringKey.Subtitles),
        subtitlesOff = uiText(UiStringKey.Off),
        subscription = uiText(UiStringKey.Subscription),
        subscribed = uiText(UiStringKey.Subscribed),
        skip = uiText(UiStringKey.Skip),
        episode = uiText(UiStringKey.Episode),
        episodeFallback = uiText(UiStringKey.Episode4da919),
        of = uiText(UiStringKey.Of),
        downloaded = uiText(UiStringKey.DownloadedBc4f6a).lowercase(Locale.ROOT),
    )
}
