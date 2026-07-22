package me.yummydroid.app.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.net.toUri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.R as Media3R
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.text.Collator
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import me.yummydroid.app.AppLog
import me.yummydroid.app.AppRoute
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AppBackAction
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.BuildConfig
import me.yummydroid.app.canHandleRootHomeBackToTop
import me.yummydroid.app.HCaptchaActivity
import me.yummydroid.app.InputAction
import me.yummydroid.app.InputActionEvent
import me.yummydroid.app.LoadState
import me.yummydroid.app.DownloadTaskState
import me.yummydroid.app.formatByteSize
import me.yummydroid.app.formatCommentTimestamp
import me.yummydroid.app.formatDuration
import me.yummydroid.app.formatPlaybackTime
import me.yummydroid.app.formatRating
import me.yummydroid.app.formatScheduleTimestamp
import me.yummydroid.app.formatViews
import me.yummydroid.app.formatWatchedAtTimestamp
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.PipPlayerHandle
import me.yummydroid.app.PlaybackRecoveryCandidate
import me.yummydroid.app.PlayerPipController
import me.yummydroid.app.R
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.resolveAppBackAction
import me.yummydroid.app.UpdateDownloadService
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AnimeGenreFilter
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.AnimeSort
import me.yummydroid.app.data.AnimeStatusFilter
import me.yummydroid.app.data.AnimeTrailer
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.APP_USER_AGENT
import me.yummydroid.app.data.BrowseFilters
import me.yummydroid.app.data.ContentLanguage
import me.yummydroid.app.data.DEFAULT_SITE_BASE_URL
import me.yummydroid.app.data.FilterCatalog
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.OfflineAnimeEntry
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PlayerBufferPreset
import me.yummydroid.app.data.PlayerDecoderMode
import me.yummydroid.app.data.PlayerSpeed
import me.yummydroid.app.data.PosterCardSize
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.RelatedAnime
import me.yummydroid.app.data.RatingDetails
import me.yummydroid.app.data.ResolvedSubtitleTrack
import me.yummydroid.app.data.ResolvedVideoStream
import me.yummydroid.app.data.ScheduleAnime
import me.yummydroid.app.data.SiteDomainResolver
import me.yummydroid.app.data.SourceQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.UserProfile
import me.yummydroid.app.data.VideoSkipSegment
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.data.bestSourceQualityPerHeight
import me.yummydroid.app.data.cleanVideoSourceLabel
import me.yummydroid.app.data.defaultVideoResolveClient
import me.yummydroid.app.data.downloadedEpisodeCountForVoice
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.isSubscribedTo
import me.yummydroid.app.data.isNewerThanVersion
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingDubbingKey
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.ageRatingFilterOptions
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.selectForPreferredQuality
import me.yummydroid.app.data.seasonFilterOptions
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.statusFilterOptions
import me.yummydroid.app.data.translateFilterOptions
import me.yummydroid.app.data.userMarkFilterOptions
import me.yummydroid.app.data.normalizeSiteBaseUrl
import me.yummydroid.app.data.normalizedSiteBaseUrls
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyAlpha
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySizes
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.data.preferredProfileSubscription
import me.yummydroid.app.data.profileDisplayKey
import me.yummydroid.app.data.profileVoiceTitle

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
    val mediaItemBuilder = MediaItem.Builder().setUri(stream.url)
    stream.mimeType?.let { mediaItemBuilder.setMimeType(it) }
    val subtitleConfigurations = stream.subtitles.mapNotNull { it.toMedia3SubtitleConfiguration() }
    if (subtitleConfigurations.isNotEmpty()) {
        mediaItemBuilder.setSubtitleConfigurations(subtitleConfigurations)
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
            setMediaItem(mediaItemBuilder.build(), startPositionMs.coerceAtLeast(0L))
            playWhenReady = false
            prepare()
        }
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
    val qualities = filter { it.isSameEpisodeAs(currentVideo) && it.matchingVoiceKey == currentVideo.matchingVoiceKey }
        .flatMap { it.sourceQualities }
    return qualities.sourceQualityOptions()
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
            key = "source:${quality.height}:${quality.bitrate}",
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
        ?: "локально"
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
    val cleaned = replace("скачано", "", ignoreCase = true)
        .replace("downloaded", "", ignoreCase = true)
    val height = Regex("""(?i)(2160|1440|1080|720|576|540|480|360|240|144)p""")
        .find(cleaned)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (height != null) return "height:$height"
    return cleaned
        .lowercase(Locale.ROOT)
        .replace(Regex("""[\s•|:_\-]+"""), "")
        .trim()
}

internal fun QualityOption.withDownloadedLabel(): QualityOption {
    if (localFile == null || label.contains("скачано", ignoreCase = true)) return this
    return copy(label = "$label • скачано")
}

internal fun mergeVideoQualityOptions(
    onlineOptions: List<QualityOption>,
    localOptions: List<QualityOption>,
    offlineMode: Boolean,
): List<QualityOption> {
    val uniqueLocalOptions = localOptions.distinctBy { it.qualityOptionIdentity() }
    if (offlineMode) {
        return uniqueLocalOptions
            .map { it.withDownloadedLabel() }
            .sortedByQuality()
    }

    val localByIdentity = uniqueLocalOptions.associateBy { it.qualityOptionIdentity() }
    val onlineWithLocalFiles = onlineOptions.map { online ->
        val local = localByIdentity[online.qualityOptionIdentity()] ?: return@map online
        online.copy(
            label = if (online.label.contains("скачано", ignoreCase = true)) {
                online.label
            } else {
                "${online.label} • скачано"
            },
            localFile = local.localFile,
        )
    }
    val onlineIdentities = onlineOptions.mapTo(mutableSetOf()) { it.qualityOptionIdentity() }
    val localOnlyOptions = uniqueLocalOptions
        .filterNot { it.qualityOptionIdentity() in onlineIdentities }
        .map { it.withDownloadedLabel() }

    return (onlineWithLocalFiles + localOnlyOptions)
        .distinctBy { it.qualityOptionIdentity() }
        .sortedByQuality()
}

internal fun List<QualityOption>.sortedByQuality(): List<QualityOption> {
    return sortedWith(
        compareByDescending<QualityOption> { it.height.coerceAtLeast(0) }
            .thenByDescending { it.bitrate.coerceAtLeast(0) }
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

internal fun SubtitleOption.subtitleOptionIdentity(): String {
    return listOf(
        language.orEmpty().lowercase(Locale.ROOT),
        label.lowercase(Locale.ROOT),
        key.lowercase(Locale.ROOT),
    ).joinToString(":").replace(Regex("""\s+"""), "")
}

internal fun SubtitleOption.matchesSelectedSubtitleKey(selectedSubtitleKey: String?): Boolean {
    val selected = selectedSubtitleKey?.takeIf { it.isNotBlank() } ?: return false
    return key == selected || subtitleOptionIdentity() == selected
}

internal fun VideoVariant.playbackSubtitle(texts: PlayerControlTexts): String {
    val voice = dubbing.cleanVideoSourceLabel()
    return listOf(voice, localizedEpisodeTitle(texts.episode, texts.episodeFallback))
        .filterNot { it.isNullOrBlank() }
        .joinToString(" • ")
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

    val episodeVideos = videos
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
    Toast.makeText(
        context,
        "Озвучка «${previousVideo.matchingVoiceTitle}» недоступна для ${nextVideo.episodeTitle}. Включена «${nextVideo.matchingVoiceTitle}».",
        Toast.LENGTH_LONG,
    ).show()
}

internal data class PlayerControlTexts(
    val title: String,
    val watch: String,
    val voice: String,
    val quality: String,
    val subtitles: String,
    val subtitlesOff: String,
    val subscription: String,
    val subscribed: String,
    val skip: String,
    val episode: String,
    val episodeFallback: String,
    val downloaded: String,
)

internal val defaultPlayerControlTexts = PlayerControlTexts(
    title = "Просмотр",
    watch = "Смотреть",
    voice = "Озвучка",
    quality = "Качество",
    subtitles = "Субтитры",
    subtitlesOff = "Выкл.",
    subscription = "Подписка",
    subscribed = "Подписан",
    skip = "Пропустить",
    episode = "Серия",
    episodeFallback = "Эпизод",
    downloaded = "скачано",
)

@Composable
internal fun rememberPlayerControlTexts(): PlayerControlTexts {
    return PlayerControlTexts(
        title = uiText("Просмотр"),
        watch = uiText("Смотреть"),
        voice = uiText("Озвучка"),
        quality = uiText("Качество"),
        subtitles = uiText("Субтитры"),
        subtitlesOff = uiText("Выкл."),
        subscription = uiText("Подписка"),
        subscribed = uiText("Подписан"),
        skip = uiText("Пропустить"),
        episode = uiText("Серия"),
        episodeFallback = uiText("Эпизод"),
        downloaded = uiText("Скачано").lowercase(Locale.ROOT),
    )
}
