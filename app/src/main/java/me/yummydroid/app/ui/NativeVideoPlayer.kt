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
@Composable
internal fun NativeVideoPlayer(
    stream: ResolvedVideoStream,
    animeTitle: String,
    currentVideo: VideoVariant,
    settings: AppSettings,
    startPositionMs: Long,
    playbackPreferredQuality: PreferredQuality,
    pendingPlaybackRecovery: PlaybackRecoveryCandidate?,
    groups: Map<String, List<VideoVariant>>,
    selectedKey: String?,
    previousVideo: VideoVariant?,
    nextVideo: VideoVariant?,
    allowSubscription: Boolean,
    subscriptionActive: Boolean,
    onToggleSubscription: () -> Unit,
    onSelectGroup: (String, VideoVariant?, Long) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPrepareFallbackSource: (VideoVariant) -> Unit,
    onSwitchToPreparedFallbackSource: (VideoVariant, Long) -> Boolean,
    onRecoveryPrebufferReady: (Long, Long) -> Boolean,
    onRecoveryPrebufferFailed: (Long) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    isInPictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    onRegisterPlayerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
    offlineMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = remember(context) { context.findActivity() }
    val fallbackScope = rememberCoroutineScope()
    val playerControlTexts = rememberPlayerControlTexts()
    val currentSettings by rememberUpdatedState(settings)
    val currentProgressCallback by rememberUpdatedState(onPlaybackProgress)
    val currentProgressVideo by rememberUpdatedState(currentVideo)
    val latestCurrentVideo by rememberUpdatedState(currentVideo)
    val latestPreviousVideo by rememberUpdatedState(previousVideo)
    val latestNextVideo by rememberUpdatedState(nextVideo)
    val latestPlayVideoAt by rememberUpdatedState(onPlayVideoAt)
    val latestPrepareFallbackSource by rememberUpdatedState(onPrepareFallbackSource)
    val latestSwitchToPreparedFallbackSource by rememberUpdatedState(onSwitchToPreparedFallbackSource)
    val latestRecoveryPrebufferReady by rememberUpdatedState(onRecoveryPrebufferReady)
    val latestRecoveryPrebufferFailed by rememberUpdatedState(onRecoveryPrebufferFailed)
    var fallbackSuppressedUntilMs by remember(stream.url, currentVideo.id) {
        mutableLongStateOf(SystemClock.elapsedRealtime() + PLAYBACK_SEEK_BUFFER_GRACE_MS)
    }
    var bufferResetSignal by remember(stream.url, currentVideo.id) { mutableIntStateOf(0) }
    val httpClient = remember { defaultVideoResolveClient() }
    val renderersFactory = remember(context, settings.decoderMode) {
        DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(settings.decoderMode.mediaCodecSelector())
    }
    val player = remember(
        stream.url,
        stream.headers,
        stream.subtitles,
        startPositionMs,
        httpClient,
        renderersFactory,
        settings.playerBufferPreset,
    ) {
        createVideoPlayer(
            context = context,
            stream = stream,
            startPositionMs = startPositionMs,
            httpClient = httpClient,
            renderersFactory = renderersFactory,
            loadControl = settings.playerBufferPreset.toLoadControl(),
        )
    }
    DisposableEffect(
        pendingPlaybackRecovery?.id,
        pendingPlaybackRecovery?.stream?.url,
        player,
        settings.playerBufferPreset,
        httpClient,
        renderersFactory,
    ) {
        val recovery = pendingPlaybackRecovery
        if (
            recovery == null ||
            recovery.stream.url.isBlank() ||
            recovery.stream.url == stream.url ||
            stream.url.startsWith("file:", ignoreCase = true) ||
            stream.url.startsWith("content:", ignoreCase = true) ||
            recovery.stream.url.startsWith("file:", ignoreCase = true) ||
            recovery.stream.url.startsWith("content:", ignoreCase = true)
        ) {
            onDispose {}
        } else {
            val targetBufferMs = settings.playerBufferPreset.recoveryPrebufferTargetMs()
            val probeStartPositionMs = recovery.positionMs.coerceAtLeast(0L)
            var finished = false
            val probePlayer = runCatching {
                createVideoPlayer(
                    context = context,
                    stream = recovery.stream,
                    startPositionMs = probeStartPositionMs,
                    httpClient = httpClient,
                    renderersFactory = renderersFactory,
                    loadControl = settings.playerBufferPreset.toRecoveryPrebufferLoadControl(),
                ).apply {
                    volume = 0f
                    playWhenReady = false
                }
            }.getOrElse { throwable ->
                AppLog.w("YummyDroidPlayer", "Recovery prebuffer failed to start", throwable)
                latestRecoveryPrebufferFailed(recovery.id)
                null
            }

            if (probePlayer == null) {
                onDispose {}
            } else {
                fun failRecovery(throwable: Throwable? = null) {
                    if (finished) return
                    finished = true
                    if (throwable != null) {
                        AppLog.w("YummyDroidPlayer", "Recovery prebuffer failed", throwable)
                    }
                    latestRecoveryPrebufferFailed(recovery.id)
                }

                fun bufferedAheadMs(): Long {
                    val bufferedPosition = probePlayer.bufferedPosition.takeIf { it != C.TIME_UNSET } ?: 0L
                    return (bufferedPosition - probeStartPositionMs).coerceAtLeast(0L)
                }

                fun maybeSwitchAfterBuffer(): Boolean {
                    if (finished) return true
                    if (probePlayer.playbackState != Player.STATE_READY) return false
                    if (bufferedAheadMs() < targetBufferMs) return false
                    finished = true
                    latestRecoveryPrebufferReady(
                        recovery.id,
                        player.currentPosition.coerceAtLeast(0L),
                    )
                    return true
                }

                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        maybeSwitchAfterBuffer()
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        failRecovery(error)
                    }
                }
                probePlayer.addListener(listener)
                val prebufferJob = fallbackScope.launch {
                    val startedAtMs = SystemClock.elapsedRealtime()
                    while (!finished) {
                        delay(PLAYBACK_RECOVERY_PREBUFFER_POLL_MS)
                        if (maybeSwitchAfterBuffer()) break
                        if (SystemClock.elapsedRealtime() - startedAtMs >= PLAYBACK_RECOVERY_PREBUFFER_TIMEOUT_MS) {
                            failRecovery()
                        }
                    }
                }

                onDispose {
                    prebufferJob.cancel()
                    probePlayer.removeListener(listener)
                    probePlayer.release()
                }
            }
        }
    }
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    val onlineQualityOptions = remember(tracks) { tracks.videoQualityOptions() }
    val subtitleOptions = remember(tracks, playerControlTexts) { tracks.subtitleOptions(playerControlTexts) }
    val sourceQualityOptions = remember(
        groups,
        selectedKey,
        currentVideo.matchingEpisodeKey,
        currentVideo.matchingVoiceKey,
    ) {
        val sourceVideos = groups[selectedKey].orEmpty().ifEmpty { groups[currentVideo.matchingVoiceKey].orEmpty() }
        sourceVideos.sourceQualityOptionsFor(currentVideo)
    }
    val streamQualityOptions = remember(stream.availableQualities) {
        stream.availableQualities.sourceQualityOptions()
    }
    val localQualityOptions = remember(
        currentVideo.matchingEpisodeKey,
        currentVideo.matchingVoiceKey,
        currentVideo.localPlaybackUrl,
        currentVideo.localFiles,
    ) {
        currentVideo.localQualityOptions()
    }
    val qualityOptions = remember(onlineQualityOptions, sourceQualityOptions, streamQualityOptions, localQualityOptions, offlineMode) {
        mergeVideoQualityOptions(
            onlineOptions = onlineQualityOptions + sourceQualityOptions + streamQualityOptions,
            localOptions = localQualityOptions,
            offlineMode = offlineMode,
        )
    }
    val streamSelectedQualityKey = remember(currentVideo.id, stream.url, stream.selectedVideoHeight) {
        stream.selectedVideoHeight
            ?.takeIf { it > 0 }
            ?.let { "height:$it" }
    }
    val latestQualityOptions by rememberUpdatedState(qualityOptions)
    val latestPlaybackPreferredQuality by rememberUpdatedState(playbackPreferredQuality)
    val latestStreamSelectedQualityKey by rememberUpdatedState(streamSelectedQualityKey)
    var selectedQualityKey by remember(currentVideo.id, stream.url, stream.selectedVideoHeight) {
        val preferredOption = qualityOptions.preferredOption(
            playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: settings.defaultQuality,
        )
        mutableStateOf(
            currentVideo.selectedLocalQualityKey(stream.url)
                ?: streamSelectedQualityKey?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
                ?: preferredOption?.qualityOptionIdentity(),
        )
    }
    var selectedSubtitleKey by remember(currentVideo.id, stream.url) {
        mutableStateOf(SUBTITLE_OFF_KEY)
    }
    var subtitleSelectionTouched by remember(currentVideo.id, stream.url) {
        mutableStateOf(false)
    }
    var playerView by remember { mutableStateOf<PlayerView?>(null) }
    DisposableEffect(player, isInPictureInPicture) {
        onRegisterPlayerInputActionHandler { event ->
            val view = playerView
            if (view == null || isInPictureInPicture) {
                false
            } else {
                view.handleRemoteInputAction(event)
            }
        }
        onDispose { onRegisterPlayerInputActionHandler(null) }
    }
    val pipPlayerHandle = remember(player) {
        object : PipPlayerHandle {
            override val isPlaying: Boolean
                get() = player.isPlaying

            override val canPlayPreviousEpisode: Boolean
                get() = latestPreviousVideo != null

            override val canPlayNextEpisode: Boolean
                get() = latestNextVideo != null

            override fun play() {
                player.play()
            }

            override fun pause() {
                player.pause()
            }

            override fun playPreviousEpisode() {
                latestPreviousVideo?.let { previous ->
                    showVoiceFallbackToast(context, latestCurrentVideo, previous)
                    player.pause()
                    latestPlayVideoAt(previous, 0L)
                }
            }

            override fun playNextEpisode() {
                latestNextVideo?.let { next ->
                    showVoiceFallbackToast(context, latestCurrentVideo, next)
                    player.pause()
                    latestPlayVideoAt(next, 0L)
                }
            }

            override fun setPictureInPictureMode(enabled: Boolean) {
                playerView?.applyPictureInPictureControllerMode(enabled)
            }

            override fun hideAppControls() {
                playerView?.hideController()
            }
        }
    }
    LaunchedEffect(previousVideo?.id, nextVideo?.id, player) {
        PlayerPipController.notifyPlayingChanged()
    }

    LaunchedEffect(qualityOptions) {
        val currentKey = selectedQualityKey
        if (currentKey != null && qualityOptions.none { it.matchesSelectedQualityKey(currentKey) }) {
            val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: settings.defaultQuality
            selectedQualityKey = streamSelectedQualityKey
                ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
                ?: qualityOptions.preferredOption(preferredQuality)?.qualityOptionIdentity()
        }
    }

    LaunchedEffect(qualityOptions, playbackPreferredQuality, settings.defaultQuality, stream.url, streamSelectedQualityKey) {
        if (selectedQualityKey != null && qualityOptions.any { it.matchesSelectedQualityKey(selectedQualityKey) }) {
            return@LaunchedEffect
        }
        val resolvedSourceKey = streamSelectedQualityKey
            ?.takeIf { key -> qualityOptions.any { it.matchesSelectedQualityKey(key) } }
        val preferredOption = qualityOptions.preferredOption(
            playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto } ?: settings.defaultQuality,
        )
        val preferredKey = resolvedSourceKey ?: preferredOption?.qualityOptionIdentity()
        if (preferredKey != null && selectedQualityKey != preferredKey) {
            preferredOption?.takeIf { it.group != null }?.let(player::selectQuality)
            selectedQualityKey = preferredKey
            playerView?.findViewById<TextView>(R.id.yummy_player_quality)
                ?.setTag(R.id.yummy_player_quality, preferredKey)
        }
    }

    LaunchedEffect(player, qualityOptions, playbackPreferredQuality, settings.defaultQuality, stream.url) {
        val preferredQuality = playbackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
            ?: settings.defaultQuality.takeUnless { it == PreferredQuality.Auto }
        val preferredOption = preferredQuality?.let { qualityOptions.preferredOption(it) }
        if (preferredOption?.group != null) {
            player.selectQuality(preferredOption)
        }
    }

    LaunchedEffect(player, subtitleOptions, selectedSubtitleKey, subtitleSelectionTouched) {
        if (!subtitleSelectionTouched && selectedSubtitleKey == SUBTITLE_OFF_KEY) {
            val defaultOption = subtitleOptions.defaultSubtitleOption() ?: return@LaunchedEffect
            player.selectSubtitle(defaultOption)
            val stableKey = defaultOption.subtitleOptionIdentity()
            selectedSubtitleKey = stableKey
            playerView?.findViewById<TextView>(R.id.yummy_player_subtitles)
                ?.setTag(R.id.yummy_player_subtitles, stableKey)
            return@LaunchedEffect
        }
        if (selectedSubtitleKey == SUBTITLE_OFF_KEY) return@LaunchedEffect
        if (subtitleOptions.none { it.matchesSelectedSubtitleKey(selectedSubtitleKey) }) {
            selectedSubtitleKey = SUBTITLE_OFF_KEY
            player.disableSubtitles()
            playerView?.findViewById<TextView>(R.id.yummy_player_subtitles)
                ?.setTag(R.id.yummy_player_subtitles, SUBTITLE_OFF_KEY)
        }
    }

    LaunchedEffect(player, settings.playerSpeed) {
        player.setPlaybackSpeed(settings.playerSpeed.value)
    }

    LaunchedEffect(player) {
        while (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_ENDED) {
            delay(24)
        }
        if (player.playbackState == Player.STATE_READY) {
            playerView?.hideController()
            player.play()
        }
    }

    LaunchedEffect(player, settings.matchDisplayModeToVideo, tracks) {
        activity?.applyVideoDisplayMode(
            enabled = settings.matchDisplayModeToVideo,
            video = player.currentVideoDisplayInfo(),
        )
    }

    LaunchedEffect(player, currentVideo.id) {
        while (true) {
            delay(PLAYBACK_PROGRESS_SAVE_INTERVAL_MS)
            if (player.playbackState != Player.STATE_IDLE) {
                currentProgressCallback(
                    currentProgressVideo,
                    player.currentPosition.coerceAtLeast(0L),
                    player.duration.normalizedDurationMs(),
                )
            }
        }
    }

    LaunchedEffect(player, currentVideo.id, stream.url, settings.playerBufferPreset, bufferResetSignal) {
        if (
            stream.url.startsWith("file:", ignoreCase = true) ||
            currentVideo.localPlaybackUrl.isNotBlank()
        ) {
            return@LaunchedEffect
        }

        var lastBufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
        var stagnantSinceMs: Long? = null
        var prepareRequested = false
        while (true) {
            delay(PLAYBACK_BUFFER_STALL_POLL_MS)
            val nowMs = SystemClock.elapsedRealtime()
            val positionMs = player.currentPosition.coerceAtLeast(0L)
            val durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            val bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
            val bufferAheadMs = (bufferedPositionMs - positionMs).coerceAtLeast(0L)
            val bufferIsGrowing = bufferedPositionMs > lastBufferedPositionMs + PLAYBACK_BUFFER_GROWTH_EPSILON_MS
            val nearPlaybackEnd = durationMs
                ?.minus(positionMs)
                ?.coerceAtLeast(0L)
                ?.let { remainingMs ->
                    remainingMs <= maxOf(
                        PLAYBACK_BUFFER_END_IGNORE_MS,
                        settings.playerBufferPreset.switchFallbackThresholdMs * 2,
                    )
                } == true
            val bufferedToEnd = durationMs
                ?.let { bufferedPositionMs >= it - PLAYBACK_BUFFER_END_EPSILON_MS } == true
            val canInspectBuffer = nowMs >= fallbackSuppressedUntilMs &&
                player.playbackState == Player.STATE_READY &&
                (player.isPlaying || player.playWhenReady) &&
                !nearPlaybackEnd &&
                !bufferedToEnd

            if (
                canInspectBuffer &&
                !bufferIsGrowing &&
                bufferAheadMs <= settings.playerBufferPreset.prepareFallbackThresholdMs
            ) {
                val stagnantFromMs = stagnantSinceMs ?: nowMs.also { stagnantSinceMs = it }
                val stagnantForMs = nowMs - stagnantFromMs
                if (!prepareRequested && stagnantForMs >= PLAYBACK_BUFFER_STALL_CONFIRM_MS) {
                    prepareRequested = true
                    latestPrepareFallbackSource(currentVideo)
                }
                if (
                    bufferAheadMs <= settings.playerBufferPreset.switchFallbackThresholdMs &&
                    stagnantForMs >= PLAYBACK_BUFFER_STALL_SWITCH_MS &&
                    latestSwitchToPreparedFallbackSource(currentVideo, positionMs)
                ) {
                    return@LaunchedEffect
                }
            } else {
                stagnantSinceMs = null
                if (
                    bufferIsGrowing ||
                    bufferAheadMs > settings.playerBufferPreset.prepareFallbackThresholdMs * 2
                ) {
                    prepareRequested = false
                }
            }
            lastBufferedPositionMs = maxOf(lastBufferedPositionMs, bufferedPositionMs)
        }
    }

    DisposableEffect(player) {
        var fallbackReported = false
        var autoAdvanceReported = false
        var playbackStartedReported = false
        var playbackEndedReported = false
        var bufferingFallbackJob: Job? = null
        PlayerPipController.registerPlayer(pipPlayerHandle)
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlayerPipController.notifyPlayingChanged()
                if (isPlaying && !playbackStartedReported) {
                    playbackStartedReported = true
                    onPlaybackStarted(currentVideo)
                }
            }

            override fun onTracksChanged(currentTracks: Tracks) {
                tracks = currentTracks
                selectedSubtitleKey = currentTracks.currentSubtitleKey() ?: SUBTITLE_OFF_KEY
                playerView?.findViewById<TextView>(R.id.yummy_player_subtitles)
                    ?.setTag(R.id.yummy_player_subtitles, selectedSubtitleKey)
                val resolvedSourceKey = latestStreamSelectedQualityKey
                if (resolvedSourceKey != null && latestQualityOptions.any { it.matchesSelectedQualityKey(resolvedSourceKey) }) {
                    selectedQualityKey = resolvedSourceKey
                    playerView?.findViewById<TextView>(R.id.yummy_player_quality)
                        ?.setTag(R.id.yummy_player_quality, resolvedSourceKey)
                    return
                }
                val explicitPreferredQuality = latestPlaybackPreferredQuality.takeUnless { it == PreferredQuality.Auto }
                    ?: currentSettings.defaultQuality.takeUnless { it == PreferredQuality.Auto }
                val preferredOption = explicitPreferredQuality
                    ?.let { latestQualityOptions.preferredOption(it) }
                if (preferredOption != null) {
                    val preferredKey = preferredOption.qualityOptionIdentity()
                    selectedQualityKey = preferredKey
                    playerView?.findViewById<TextView>(R.id.yummy_player_quality)
                        ?.setTag(R.id.yummy_player_quality, preferredKey)
                    return
                }
                val actualQualityKey = player.currentQualityKey()
                selectedQualityKey = currentTracks.videoQualityOptions()
                    .firstOrNull { it.matchesSelectedQualityKey(actualQualityKey) }
                    ?.qualityOptionIdentity()
                    ?: actualQualityKey?.qualityIdentityFromLabel()
                    ?: actualQualityKey
                playerView?.findViewById<TextView>(R.id.yummy_player_quality)
                    ?.setTag(R.id.yummy_player_quality, selectedQualityKey)
                activity?.applyVideoDisplayMode(
                    enabled = currentSettings.matchDisplayModeToVideo,
                    video = player.currentVideoDisplayInfo(),
                )
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                activity?.applyVideoDisplayMode(
                    enabled = currentSettings.matchDisplayModeToVideo,
                    video = player.currentVideoDisplayInfo() ?: videoSize.toVideoDisplayInfo(),
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_BUFFERING && playbackStartedReported && !fallbackReported) {
                    bufferingFallbackJob?.cancel()
                    bufferingFallbackJob = fallbackScope.launch {
                        val delayMs = maxOf(
                            PLAYBACK_BUFFERING_FALLBACK_DELAY_MS,
                            fallbackSuppressedUntilMs - SystemClock.elapsedRealtime(),
                        )
                        delay(delayMs.coerceAtLeast(0L))
                        if (
                            SystemClock.elapsedRealtime() >= fallbackSuppressedUntilMs &&
                            player.playbackState == Player.STATE_BUFFERING &&
                            !fallbackReported
                        ) {
                            fallbackReported = true
                            onPlaybackFailed(currentVideo, player.currentPosition.coerceAtLeast(0L))
                        }
                    }
                } else if (playbackState != Player.STATE_BUFFERING) {
                    bufferingFallbackJob?.cancel()
                    bufferingFallbackJob = null
                }

                if (playbackState == Player.STATE_ENDED && !playbackEndedReported) {
                    playbackEndedReported = true
                    onPlaybackEnded(currentVideo)
                }
                if (
                    playbackState == Player.STATE_ENDED &&
                    currentSettings.autoplayNextEpisode &&
                    !autoAdvanceReported
                ) {
                    autoAdvanceReported = true
                    nextVideo?.let { next ->
                        showVoiceFallbackToast(context, currentVideo, next)
                        currentProgressCallback(next, 1_000L, 0L)
                        playerView?.hideController()
                        onPlayVideoAt(next, 0L)
                    }
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                fallbackSuppressedUntilMs = SystemClock.elapsedRealtime() + PLAYBACK_SEEK_BUFFER_GRACE_MS
                bufferResetSignal += 1
            }

            override fun onPlayerError(error: PlaybackException) {
                val httpError = error.cause as? HttpDataSource.InvalidResponseCodeException
                if (httpError != null) {
                    val uri = httpError.dataSpec.uri
                    AppLog.w(
                        "YummyDroidPlayer",
                        "Playback HTTP ${httpError.responseCode}: host=${uri.host}, file=${uri.lastPathSegment}, headers=${httpError.headerFields.keys}",
                    )
                } else {
                    AppLog.w("YummyDroidPlayer", "Playback failed: ${error.errorCodeName}", error)
                }
                if (!fallbackReported) {
                    bufferingFallbackJob?.cancel()
                    bufferingFallbackJob = null
                    fallbackReported = true
                    onPlaybackFailed(currentVideo, player.currentPosition.coerceAtLeast(0L))
                }
            }
        }
        player.addListener(listener)
        onDispose {
            bufferingFallbackJob?.cancel()
            currentProgressCallback(
                currentProgressVideo,
                player.currentPosition.coerceAtLeast(0L),
                player.duration.normalizedDurationMs(),
            )
            player.removeListener(listener)
            PlayerPipController.unregisterPlayer(pipPlayerHandle)
            playerView?.clearTimelineScrubState()
            playerView?.unbindSkipControls()
            activity?.clearPreferredDisplayMode()
            player.release()
        }
    }

    key(
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp,
    ) {
        AndroidView(
            factory = { viewContext ->
                val parent = FrameLayout(viewContext)
                LayoutInflater.from(viewContext).inflate(R.layout.yummy_player_view, parent, false) as PlayerView
            },
            update = { view ->
                playerView = view
                view.player = player
                view.controllerAutoShow = false
                view.setControllerAnimationEnabled(false)
                view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                view.installVideoZoomGestures(token = "${currentVideo.id}:${stream.url}")
                view.keepScreenOn = true
                view.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                view.requestFocus()
                val previousPictureInPictureMode = view.tagValue<Boolean>(R.id.yummy_player_view)
                if (previousPictureInPictureMode != isInPictureInPicture) {
                    view.setTag(R.id.yummy_player_view, isInPictureInPicture)
                    view.applyPictureInPictureControllerMode(isInPictureInPicture)
                }
                if (isInPictureInPicture) {
                    view.hideController()
                } else {
                    view.bindYummyController(
                        player = player,
                        animeTitle = animeTitle,
                        currentVideo = currentVideo,
                        isLocalPlayback = stream.url.startsWith("file:", ignoreCase = true) ||
                            currentVideo.localPlaybackUrl.isNotBlank(),
                        groups = groups,
                        selectedKey = selectedKey,
                        previousVideo = previousVideo,
                        nextVideo = nextVideo,
                        allowSubscription = allowSubscription,
                        subscriptionActive = subscriptionActive,
                        onToggleSubscription = onToggleSubscription,
                        qualityOptions = qualityOptions,
                        selectedQualityKey = selectedQualityKey,
                        onSelectedQualityKeyChange = { selectedQualityKey = it },
                        subtitleOptions = subtitleOptions,
                        selectedSubtitleKey = selectedSubtitleKey,
                        onSelectedSubtitleKeyChange = {
                            subtitleSelectionTouched = true
                            selectedSubtitleKey = it
                        },
                        onSelectLocalQuality = { localFile ->
                            val positionMs = player.currentPosition.coerceAtLeast(0L)
                            player.pause()
                            onPlayVideoAt(currentVideo.withOfflineFile(localFile), positionMs)
                        },
                        onSelectPreferredQuality = { preferredQuality ->
                            val positionMs = player.currentPosition.coerceAtLeast(0L)
                            player.pause()
                            onPlayVideoAtQuality(currentVideo.withoutLocalPlayback(), positionMs, preferredQuality)
                        },
                        onSelectGroup = onSelectGroup,
                        onPlayVideo = onPlayVideo,
                        onPlayVideoAt = onPlayVideoAt,
                        canUsePictureInPicture = canUsePictureInPicture,
                        onEnterPictureInPicture = onEnterPictureInPicture,
                        settings = settings,
                        texts = playerControlTexts,
                        onSettingsChange = onSettingsChange,
                        onBack = onBack,
                    )
                    if (previousPictureInPictureMode != false) {
                        view.restoreControllerAfterPictureInPicture()
                    }
                }
            },
            modifier = modifier,
        )
    }
}
