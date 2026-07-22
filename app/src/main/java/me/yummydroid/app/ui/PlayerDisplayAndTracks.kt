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

internal data class VideoDisplayInfo(
    val width: Int,
    val height: Int,
    val frameRate: Float,
)

internal tailrec fun Context.findActivity(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
}

internal fun Context.supportsDisplayModeMatching(): Boolean {
    val uiModeManager = getSystemService(UiModeManager::class.java)
    val isTelevision = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    if (isTelevision) return true

    val displayManager = getSystemService(DisplayManager::class.java)
    return displayManager
        ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        ?.isNotEmpty() == true
}

internal fun Activity.applyVideoDisplayMode(enabled: Boolean, video: VideoDisplayInfo?) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || !supportsDisplayModeMatching()) return
    if (!enabled || video == null || video.width <= 0 || video.height <= 0) {
        clearPreferredDisplayMode()
        return
    }

    @Suppress("DEPRECATION")
    val display = windowManager.defaultDisplay ?: return
    val targetMode = display.supportedModes
        .filter { mode -> mode.physicalWidth > 0 && mode.physicalHeight > 0 }
        .minByOrNull { mode -> mode.displayModeScore(video) }

    val targetModeId = targetMode?.modeId ?: 0
    if (window.attributes.preferredDisplayModeId == targetModeId) return
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = targetModeId
    }
}

internal fun Activity.clearPreferredDisplayMode() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    if (window.attributes.preferredDisplayModeId == 0) return
    window.attributes = window.attributes.apply {
        preferredDisplayModeId = 0
    }
}

internal fun android.view.Display.Mode.displayModeScore(video: VideoDisplayInfo): Float {
    val modeLongSide = maxOf(physicalWidth, physicalHeight)
    val modeShortSide = minOf(physicalWidth, physicalHeight)
    val videoLongSide = maxOf(video.width, video.height)
    val videoShortSide = minOf(video.width, video.height)
    val resolutionPenalty = when {
        modeLongSide >= videoLongSide && modeShortSide >= videoShortSide ->
            (modeLongSide - videoLongSide) + (modeShortSide - videoShortSide)
        else ->
            100_000 + abs(modeLongSide - videoLongSide) + abs(modeShortSide - videoShortSide)
    }
    return resolutionPenalty + refreshRatePenalty(refreshRate, video.frameRate)
}

internal fun refreshRatePenalty(refreshRate: Float, frameRate: Float): Float {
    if (refreshRate <= 0f || frameRate <= 0f) return 0f
    val candidates = listOf(frameRate, frameRate * 2f, frameRate * 3f, frameRate / 2f)
    return candidates.minOf { abs(refreshRate - it) } * 100f
}

@OptIn(UnstableApi::class)
internal fun Player.currentVideoDisplayInfo(): VideoDisplayInfo? {
    (this as? ExoPlayer)?.videoFormat
        ?.takeIf { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            return VideoDisplayInfo(
                width = format.width,
                height = format.height,
                frameRate = format.frameRate,
            )
        }

    return currentTracks.groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex -> group.getTrackFormat(trackIndex) }
        }
        .firstOrNull { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            VideoDisplayInfo(
                width = format.width,
                height = format.height,
                frameRate = format.frameRate,
            )
        }
}

internal fun VideoSize.toVideoDisplayInfo(): VideoDisplayInfo? {
    if (width <= 0 || height <= 0) return null
    return VideoDisplayInfo(width = width, height = height, frameRate = 0f)
}

@OptIn(UnstableApi::class)
internal fun ExoPlayer.selectQuality(option: QualityOption) {
    val group = option.group ?: return
    trackSelectionParameters = trackSelectionParameters
        .buildUpon()
        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
        .setMaxVideoBitrate(Int.MAX_VALUE)
        .addOverride(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        .build()
}

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

internal fun List<QualityOption>.preferredOption(preferredQuality: PreferredQuality): QualityOption? {
    return takeIf { preferredQuality.height != null }?.selectForPreferredQuality(
        preferredQuality = preferredQuality,
        height = { it.height },
        bitrate = { it.bitrate },
    )
}

@OptIn(UnstableApi::class)
internal fun PlayerDecoderMode.mediaCodecSelector(): MediaCodecSelector {
    return when (this) {
        PlayerDecoderMode.Auto -> MediaCodecSelector.DEFAULT
        PlayerDecoderMode.Hardware -> MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaults = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
            defaults.filter { it.hardwareAccelerated }.ifEmpty { defaults }
        }
        PlayerDecoderMode.Software -> MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
            val defaults = MediaCodecSelector.DEFAULT.getDecoderInfos(
                mimeType,
                requiresSecureDecoder,
                requiresTunnelingDecoder,
            )
            defaults.filter { it.softwareOnly }.ifEmpty { defaults }
        }
    }
}

@OptIn(UnstableApi::class)
internal fun PlayerBufferPreset.toLoadControl(): DefaultLoadControl {
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(minBufferMs, maxBufferMs, playbackBufferMs, rebufferMs)
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}

@OptIn(UnstableApi::class)
internal fun PlayerBufferPreset.toRecoveryPrebufferLoadControl(): DefaultLoadControl {
    val targetBufferMs = recoveryPrebufferTargetMs().toInt()
    val resolvedMinBufferMs = maxOf(minBufferMs, targetBufferMs)
    val resolvedMaxBufferMs = maxOf(maxBufferMs, resolvedMinBufferMs)
    return DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            resolvedMinBufferMs,
            resolvedMaxBufferMs,
            targetBufferMs,
            maxOf(rebufferMs, targetBufferMs),
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()
}

internal fun PlayerBufferPreset.recoveryPrebufferTargetMs(): Long {
    return maxOf(PLAYBACK_RECOVERY_PREBUFFER_MIN_MS, switchFallbackThresholdMs)
}

@OptIn(UnstableApi::class)
internal fun Player.currentQualityKey(): String? {
    (this as? ExoPlayer)?.videoFormat
        ?.takeIf { format -> format.width > 0 || format.height > 0 }
        ?.let { format ->
            return "${format.height}:${format.bitrate}:${format.qualityLabel()}"
        }

    return currentTracks
        .groups
        .asSequence()
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
        .flatMap { group ->
            (0 until group.length)
                .asSequence()
                .filter { trackIndex -> group.isTrackSelected(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    "${format.height}:${format.bitrate}:${format.qualityLabel()}"
                }
        }
        .firstOrNull()
}

internal data class QualityOption(
    val group: Tracks.Group?,
    val trackIndex: Int,
    val label: String,
    val height: Int,
    val bitrate: Int,
    val key: String,
    val localFile: OfflineVideoFile? = null,
    val preferredQuality: PreferredQuality? = null,
)

internal data class SubtitleOption(
    val group: Tracks.Group,
    val trackIndex: Int,
    val label: String,
    val language: String?,
    val selectionFlags: Int,
    val key: String,
)

@OptIn(UnstableApi::class)
internal fun Tracks.videoQualityOptions(): List<QualityOption> {
    return groups
        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSupported }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSupported(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    QualityOption(
                        group = group,
                        trackIndex = trackIndex,
                        label = format.qualityLabel(),
                        height = format.height,
                        bitrate = format.bitrate,
                        key = "${format.height}:${format.bitrate}:${format.qualityLabel()}",
                        preferredQuality = PreferredQuality.fromHeight(format.height),
                    )
                }
        }
        .sortedWith(
            compareByDescending<QualityOption> { it.height.takeIf { height -> height > 0 } ?: 0 }
                .thenByDescending { it.bitrate.takeIf { bitrate -> bitrate > 0 } ?: 0 }
                .thenBy { it.label },
        )
        .distinctBy { it.qualityOptionIdentity() }
}

@OptIn(UnstableApi::class)
internal fun Tracks.subtitleOptions(texts: PlayerControlTexts): List<SubtitleOption> {
    return groups
        .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
        .flatMap { group ->
            (0 until group.length)
                .filter { trackIndex -> group.isTrackSupported(trackIndex) }
                .map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    SubtitleOption(
                        group = group,
                        trackIndex = trackIndex,
                        label = format.subtitleLabel(texts, trackIndex),
                        language = format.language,
                        selectionFlags = format.selectionFlags,
                        key = "${format.id.orEmpty()}:${format.language.orEmpty()}:${format.label.orEmpty()}:$trackIndex",
                    )
                }
        }
        .distinctBy { it.subtitleOptionIdentity() }
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
    val explicitLabel = label?.takeIf { it.isNotBlank() }
    val languageLabel = language
        ?.takeIf { it.isNotBlank() && it != C.LANGUAGE_UNDETERMINED }
        ?.let { languageTag ->
            runCatching { Locale.forLanguageTag(languageTag).getDisplayLanguage(Locale.getDefault()) }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
        }
    return explicitLabel
        ?: languageLabel
        ?: "${texts.subtitles} ${trackIndex + 1}"
}

@OptIn(UnstableApi::class)
internal fun androidx.media3.common.Format.qualityLabel(): String {
    return when {
        height > 0 -> "${height}p"
        width > 0 -> "${width}px"
        else -> "Видео"
    }
}
