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

@Composable
fun YummyDroidApp(
    state: YummyDroidUiState,
    isInPictureInPicture: Boolean,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAnime: () -> Unit,
    onBrowseSectionChange: (BrowseSection) -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onSettingsChange: (AppSettings) -> Unit,
    onOpenAnime: (Long) -> Unit,
    onFilterByGenre: (FilterOption) -> Unit,
    onFilterByYear: (Int) -> Unit,
    onFilterByStudio: (FilterOption) -> Unit,
    onFilterByCreator: (FilterOption) -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onPlayVideoAtQuality: (VideoVariant, Long, PreferredQuality) -> Unit,
    onRetryVideo: () -> Unit,
    onPlaybackFailed: (VideoVariant, Long) -> Unit,
    onPrepareFallbackSource: (VideoVariant) -> Unit,
    onSwitchToPreparedFallbackSource: (VideoVariant, Long) -> Boolean,
    onRecoveryPrebufferReady: (Long, Long) -> Boolean,
    onRecoveryPrebufferFailed: (Long) -> Unit,
    onPlaybackStarted: (VideoVariant) -> Unit,
    onPlaybackEnded: (VideoVariant) -> Unit,
    onPlaybackProgress: (VideoVariant, Long, Long) -> Unit,
    canUsePictureInPicture: Boolean,
    onEnterPictureInPicture: () -> Unit,
    onLogin: (String, String, String?) -> Unit,
    onCaptchaSolved: (String) -> Unit,
    onCaptchaCanceled: (String?) -> Unit,
    onLogout: () -> Unit,
    onOpenLibraryFilter: () -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onUnsubscribeVideoSubscription: (VideoSubscription) -> Unit,
    onRefreshVideoSubscriptions: () -> Unit,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadVideo: (VideoVariant, PreferredQuality) -> Unit,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    onDeleteOfflineVideo: (Long, Long, String?) -> Unit,
    onDeleteOfflineAnime: (Long) -> Unit,
    onClearAppContentCache: () -> Unit,
    onClearDownloadHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onCheckForUpdates: () -> Unit,
    onBack: () -> Unit,
    registerInputActionHandler: (((InputActionEvent) -> Boolean)?) -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var loginDialogOpen by remember { mutableStateOf(false) }
    var profileDialogOpen by remember { mutableStateOf(false) }
    var settingsDialogOpen by remember { mutableStateOf(false) }
    var autoUpdatePromptDismissed by remember { mutableStateOf(false) }
    var modalInputActionHandler by remember { mutableStateOf<((InputAction) -> Boolean)?>(null) }
    var playerInputActionHandler by remember { mutableStateOf<((InputActionEvent) -> Boolean)?>(null) }
    var homeFocusRequestSerial by remember { mutableLongStateOf(0L) }
    var homeFocusRequest by remember { mutableStateOf<HomeFocusRequest?>(null) }
    var catalogFocusedItemIndex by remember { mutableIntStateOf(-1) }
    var scheduleFocusedItemIndex by remember { mutableIntStateOf(-1) }
    var historyFocusedItemIndex by remember { mutableIntStateOf(-1) }
    CaptchaChallengeEffect(
        requestNonce = state.auth.captchaRequestNonce,
        onSolved = onCaptchaSolved,
        onCanceled = onCaptchaCanceled,
    )
    val catalogGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val scheduleListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val historyGridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    var appLayers by remember { mutableStateOf(emptyList<AppScreenLayer>()) }
    val renderedAppLayers = appLayers.syncedWith(state)
    SideEffect {
        if (appLayers != renderedAppLayers) {
            appLayers = renderedAppLayers
        }
    }
    val activeLayerKey = renderedAppLayers.lastOrNull()?.key
    var activeLayerFocusNonce by remember { mutableLongStateOf(0L) }
    LaunchedEffect(activeLayerKey) {
        focusManager.clearFocus(force = true)
        if (activeLayerKey != AppScreenKey.Home) {
            homeFocusRequest = null
        }
        modalInputActionHandler = null
        playerInputActionHandler = null
        activeLayerFocusNonce += 1L
    }
    val openAnimeFromCatalog = remember(onOpenAnime) {
        { animeId: Long ->
            onOpenAnime(animeId)
        }
    }
    val playAdjacentEpisode = playAdjacentEpisode@{ forward: Boolean ->
        val route = state.route as? AppRoute.Player ?: return@playAdjacentEpisode false
        val adjacent = findAdjacentPlayerVideo(
            currentVideo = route.video,
            allVideos = state.videos.readyListOrEmpty(),
            selectedGroup = state.selectedVideoGroup,
            forward = forward,
        ) ?: return@playAdjacentEpisode false
        onSelectVideoGroup(adjacent.groupKey)
        onPlayVideoAtQuality(adjacent, 0L, route.preferredQuality)
        true
    }
    val pendingUpdate = state.updateState
        .readyDataOrNull()
        ?.takeIf { it.isNewerThanInstalled() && !autoUpdatePromptDismissed && !settingsDialogOpen }
    val hasTopAppModal = loginDialogOpen ||
        profileDialogOpen ||
        settingsDialogOpen ||
        pendingUpdate != null

    fun closeTopAppModalFromBack(): Boolean {
        return when {
            pendingUpdate != null -> {
                autoUpdatePromptDismissed = true
                true
            }
            settingsDialogOpen -> {
                settingsDialogOpen = false
                true
            }
            profileDialogOpen -> {
                profileDialogOpen = false
                true
            }
            loginDialogOpen -> {
                loginDialogOpen = false
                true
            }
            else -> false
        }
    }

    fun canScrollRootHomeToTop(): Boolean {
        val isRootHome = state.route == AppRoute.Home && !state.canNavigateBack
        return when (state.homeSection) {
            BrowseSection.Catalog -> canHandleRootHomeBackToTop(
                isRootHome = isRootHome,
                homeSection = BrowseSection.Catalog,
                firstVisibleItemIndex = catalogGridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = catalogGridState.firstVisibleItemScrollOffset,
                focusedItemIndex = catalogFocusedItemIndex,
            )
            BrowseSection.Schedule -> canHandleRootHomeBackToTop(
                isRootHome = isRootHome,
                homeSection = BrowseSection.Schedule,
                firstVisibleItemIndex = scheduleListState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = scheduleListState.firstVisibleItemScrollOffset,
                focusedItemIndex = scheduleFocusedItemIndex,
            )
            BrowseSection.History -> canHandleRootHomeBackToTop(
                isRootHome = isRootHome,
                homeSection = BrowseSection.History,
                firstVisibleItemIndex = historyGridState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = historyGridState.firstVisibleItemScrollOffset,
                focusedItemIndex = historyFocusedItemIndex,
            )
            BrowseSection.Downloads -> false
        }
    }

    fun requestHomeFocusFirstItem(section: BrowseSection = state.homeSection) {
        if (section == BrowseSection.Downloads) return
        homeFocusRequestSerial += 1L
        homeFocusRequest = HomeFocusRequest(section = section, nonce = homeFocusRequestSerial)
    }

    fun markHomeFocusFirstItemRequestHandled(request: HomeFocusRequest) {
        if (homeFocusRequest == request) {
            homeFocusRequest = null
        }
    }

    fun scrollRootHomeToTopFromBack(): Boolean {
        if (!canScrollRootHomeToTop()) return false
        requestHomeFocusFirstItem()
        return true
    }

    fun handleBackAction(event: InputActionEvent): Boolean {
        val backAction = resolveAppBackAction(
            hasModal = modalInputActionHandler != null || hasTopAppModal,
            canHidePlayerControls = state.route is AppRoute.Player &&
                !isInPictureInPicture &&
                playerInputActionHandler != null,
            canNavigateBack = state.canNavigateBack,
            canScrollRootHomeToTop = canScrollRootHomeToTop(),
        )
        if (event.isRepeated && backAction != AppBackAction.LetSystemHandle) {
            return true
        }

        return when (backAction) {
            AppBackAction.CloseModal -> {
                modalInputActionHandler?.invoke(InputAction.Back) == true || closeTopAppModalFromBack()
            }
            AppBackAction.HidePlayerControls -> {
                if (playerInputActionHandler?.invoke(event) == true) {
                    true
                } else if (state.canNavigateBack) {
                    onBack()
                    true
                } else {
                    scrollRootHomeToTopFromBack()
                }
            }
            AppBackAction.NavigateBack -> {
                onBack()
                true
            }
            AppBackAction.ScrollRootHomeToTop -> scrollRootHomeToTopFromBack()
            AppBackAction.LetSystemHandle -> false
        }
    }

    val inputActionHandler by rememberUpdatedState {
            event: InputActionEvent ->
        val action = event.action
        if (action == InputAction.Back) {
            return@rememberUpdatedState handleBackAction(event)
        }
        modalInputActionHandler?.let { handler ->
            if (handler(action)) return@rememberUpdatedState true
        }
        if (state.route is AppRoute.Player) {
            when {
                playerInputActionHandler?.invoke(event) == true -> true
                action == InputAction.PreviousEpisode -> playAdjacentEpisode(false)
                action == InputAction.NextEpisode -> playAdjacentEpisode(true)
                else -> false
            }
        } else {
            when (action) {
                InputAction.Up,
                InputAction.Down,
                InputAction.Left,
                InputAction.Right -> false
                InputAction.PreviousEpisode -> playAdjacentEpisode(false)
                InputAction.NextEpisode -> playAdjacentEpisode(true)
                InputAction.Play,
                InputAction.Pause,
                InputAction.PlayPause -> false
                InputAction.Back -> false
                InputAction.Confirm -> false
            }
        }
    }

    val shouldHandleSystemBack = resolveAppBackAction(
        hasModal = modalInputActionHandler != null || hasTopAppModal,
        canHidePlayerControls = state.route is AppRoute.Player &&
            !isInPictureInPicture &&
            playerInputActionHandler != null,
        canNavigateBack = state.canNavigateBack,
        canScrollRootHomeToTop = canScrollRootHomeToTop(),
    ) != AppBackAction.LetSystemHandle

    BackHandler(enabled = shouldHandleSystemBack) {
        inputActionHandler(InputActionEvent(InputAction.Back))
    }

    DisposableEffect(Unit) {
        registerInputActionHandler { action -> inputActionHandler(action) }
        onDispose { registerInputActionHandler(null) }
    }

    @Composable
    fun AppLayerContainer(
        layerKey: AppScreenKey,
        active: Boolean,
        zIndex: Float,
        requestRootFocusWhenActive: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        val layerFocusRequester = remember(layerKey) { FocusRequester() }
        LaunchedEffect(active) {
            if (!active) {
                focusManager.clearFocus(force = true)
            }
        }
        LaunchedEffect(active, activeLayerFocusNonce, requestRootFocusWhenActive) {
            if (active && requestRootFocusWhenActive) {
                withFrameNanos { }
                runCatching { layerFocusRequester.requestFocus() }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(zIndex)
                .focusRequester(layerFocusRequester)
                .focusProperties { canFocus = active }
                .focusable(enabled = active),
        ) {
            content()
        }
    }

    @Composable
    fun HomeLayerScreen(layer: AppScreenLayer, active: Boolean, zIndex: Float) {
        AppLayerContainer(
            layerKey = AppScreenKey.Home,
            active = active,
            zIndex = zIndex,
            requestRootFocusWhenActive = false,
        ) {
            key(AppScreenKey.Home) {
                BrowseScreen(
                    state = layer.state,
                    catalogGridState = catalogGridState,
                    scheduleListState = scheduleListState,
                    historyGridState = historyGridState,
                    homeFocusRequest = if (active) homeFocusRequest else null,
                    activeFocusRequestNonce = if (active) activeLayerFocusNonce else 0L,
                    onHomeFocusRequestHandled = if (active) {
                        { request -> markHomeFocusFirstItemRequestHandled(request) }
                    } else {
                        {}
                    },
                    onCatalogFocusedItemChange = if (active) {
                        { index -> catalogFocusedItemIndex = index }
                    } else {
                        {}
                    },
                    onScheduleFocusedItemChange = if (active) {
                        { index -> scheduleFocusedItemIndex = index }
                    } else {
                        {}
                    },
                    onHistoryFocusedItemChange = if (active) {
                        { index -> historyFocusedItemIndex = index }
                    } else {
                        {}
                    },
                    onRegisterModalInputActionHandler = if (active) {
                        { handler -> modalInputActionHandler = handler }
                    } else {
                        {}
                    },
                    onQueryChange = if (active) onQueryChange else { _ -> },
                    onRefresh = if (active) onRefresh else ({}),
                    onLoadMoreAnime = if (active) onLoadMoreAnime else ({}),
                    onBrowseSectionChange = if (active) onBrowseSectionChange else { _ -> },
                    onFiltersChange = if (active) onFiltersChange else { _ -> },
                    onResetFilters = if (active) onResetFilters else ({}),
                    onOpenSettings = if (active) {
                        { settingsDialogOpen = true }
                    } else {
                        {}
                    },
                    onOpenDownloads = if (active) {
                        { onBrowseSectionChange(BrowseSection.Downloads) }
                    } else {
                        {}
                    },
                    onClearDownloadHistory = if (active) onClearDownloadHistory else ({}),
                    onCancelDownload = if (active) onCancelDownload else { _ -> },
                    onPauseDownload = if (active) onPauseDownload else { _ -> },
                    onResumeDownload = if (active) onResumeDownload else { _ -> },
                    onOpenLogin = if (active) {
                        { loginDialogOpen = true }
                    } else {
                        {}
                    },
                    onOpenProfile = if (active) {
                        { profileDialogOpen = true }
                    } else {
                        {}
                    },
                    onOpenAnime = if (active) openAnimeFromCatalog else { _ -> },
                    onRequestHomeFocusReset = if (active) {
                        { requestHomeFocusFirstItem() }
                    } else {
                        {}
                    },
                )
            }
        }
    }

    @Composable
    fun DetailsLayerScreen(layer: AppScreenLayer, active: Boolean, zIndex: Float) {
        val layerKey = layer.key as? AppScreenKey.Details ?: return
        AppLayerContainer(
            layerKey = layerKey,
            active = active,
            zIndex = zIndex,
        ) {
            key(layerKey) {
                DetailsScreenModern(
                    state = layer.state,
                    onRefresh = if (active) onRefresh else ({}),
                    onOpenAnime = if (active) onOpenAnime else { _ -> },
                    onOpenLogin = if (active) {
                        { loginDialogOpen = true }
                    } else {
                        {}
                    },
                    onOpenProfile = if (active) {
                        { profileDialogOpen = true }
                    } else {
                        {}
                    },
                    onGenreFilterSelected = if (active) onFilterByGenre else { _ -> },
                    onYearFilterSelected = if (active) onFilterByYear else { _ -> },
                    onStudioFilterSelected = if (active) onFilterByStudio else { _ -> },
                    onCreatorFilterSelected = if (active) onFilterByCreator else { _ -> },
                    onSelectVideoGroup = if (active) onSelectVideoGroup else { _ -> },
                    onPlayVideo = if (active) onPlayVideo else { _ -> },
                    onPlayVideoAt = if (active) onPlayVideoAt else { _, _ -> },
                    onSelectAnimeListMark = if (active) onSelectAnimeListMark else { _ -> },
                    onToggleFavorite = if (active) onToggleFavorite else ({}),
                    onSetAnimeRating = if (active) onSetAnimeRating else { _ -> },
                    onAddAnimeComment = if (active) onAddAnimeComment else { _ -> },
                    onLoadMoreAnimeComments = if (active) onLoadMoreAnimeComments else ({}),
                    onToggleVideoSubscription = if (active) onToggleVideoSubscription else { _ -> },
                    onResolveDownloadQualities = if (active) {
                        onResolveDownloadQualities
                    } else {
                        { _, _, _ -> emptyList() }
                    },
                    onDownloadVideo = if (active) onDownloadVideo else { _, _ -> },
                    onDownloadAllVideos = if (active) onDownloadAllVideos else { _, _ -> },
                    onDeleteOfflineVideo = if (active) onDeleteOfflineVideo else { _, _, _ -> },
                    onRegisterModalInputActionHandler = if (active) {
                        { handler -> modalInputActionHandler = handler }
                    } else {
                        {}
                    },
                )
            }
        }
    }

    @Composable
    fun PlayerLayerScreen(layer: AppScreenLayer, active: Boolean, zIndex: Float) {
        val route = layer.state.route as? AppRoute.Player ?: return
        AppLayerContainer(
            layerKey = AppScreenKey.Player,
            active = active,
            zIndex = zIndex,
            requestRootFocusWhenActive = false,
        ) {
            key(AppScreenKey.Player) {
                PlayerScreen(
                    animeTitle = route.animeTitle,
                    video = route.video,
                    settings = layer.state.settings,
                    startPositionMs = route.startPositionMs,
                    preferredQuality = route.preferredQuality,
                    allVideos = layer.state.videos.readyListOrEmpty(),
                    selectedGroup = layer.state.selectedVideoGroup,
                    streamState = layer.state.playerStream,
                    pendingPlaybackRecovery = layer.state.pendingPlaybackRecovery,
                    isInPictureInPicture = isInPictureInPicture,
                    forcedOfflineMode = layer.state.forcedOfflineMode,
                    allowSubscriptions = layer.state.auth.profile != null &&
                        !layer.state.forcedOfflineMode &&
                        (layer.state.details.readyDataOrNull()?.canShowVideoSubscriptions() == true),
                    subscriptions = layer.state.detailsExtras.readyDataOrNull()?.subscriptions.orEmpty(),
                    onSelectGroup = if (active) onSelectVideoGroup else { _ -> },
                    onPlayVideo = if (active) onPlayVideo else { _ -> },
                    onPlayVideoAt = if (active) onPlayVideoAt else { _, _ -> },
                    onPlayVideoAtQuality = if (active) onPlayVideoAtQuality else { _, _, _ -> },
                    onToggleVideoSubscription = if (active) onToggleVideoSubscription else { _ -> },
                    onRetry = if (active) onRetryVideo else ({}),
                    onPlaybackFailed = if (active) onPlaybackFailed else { _, _ -> },
                    onPrepareFallbackSource = if (active) onPrepareFallbackSource else { _ -> },
                    onSwitchToPreparedFallbackSource = if (active) onSwitchToPreparedFallbackSource else { _, _ -> false },
                    onRecoveryPrebufferReady = if (active) onRecoveryPrebufferReady else { _, _ -> false },
                    onRecoveryPrebufferFailed = if (active) onRecoveryPrebufferFailed else { _ -> },
                    onPlaybackStarted = if (active) onPlaybackStarted else { _ -> },
                    onPlaybackEnded = if (active) onPlaybackEnded else { _ -> },
                    onPlaybackProgress = if (active) onPlaybackProgress else { _, _, _ -> },
                    canUsePictureInPicture = active && canUsePictureInPicture,
                    onEnterPictureInPicture = if (active) onEnterPictureInPicture else ({}),
                    onSettingsChange = if (active) onSettingsChange else { _ -> },
                    onBack = if (active) onBack else ({}),
                    onRegisterPlayerInputActionHandler = if (active) {
                        { handler -> playerInputActionHandler = handler }
                    } else {
                        {}
                    },
                )
            }
        }
    }

    CompositionLocalProvider(LocalUiLanguage provides state.settings.contentLanguage) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (state.route is AppRoute.Player) {
                        Modifier
                    } else {
                        Modifier
                            .navigationBarsPadding()
                    },
                )
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) {
                        return@onKeyEvent false
                    }

                    when (event.key) {
                        Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
                        Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
                        Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
                        Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
                        else -> false
                    }
                },
        ) {
        renderedAppLayers.forEachIndexed { index, layer ->
            val active = index == renderedAppLayers.lastIndex
            when (layer.key) {
                AppScreenKey.Home -> HomeLayerScreen(
                    layer = layer,
                    active = active,
                    zIndex = index.toFloat(),
                )
                is AppScreenKey.Details -> DetailsLayerScreen(
                    layer = layer,
                    active = active,
                    zIndex = index.toFloat(),
                )
                AppScreenKey.Player -> PlayerLayerScreen(
                    layer = layer,
                    active = active,
                    zIndex = index.toFloat(),
                )
            }
        }

        if (loginDialogOpen) {
            LoginDialog(
                auth = state.auth,
                siteBaseUrl = state.siteBaseUrl,
                onLogin = onLogin,
                onDismiss = { loginDialogOpen = false },
            )
        }

        if (profileDialogOpen) {
            ProfileDialog(
                auth = state.auth,
                siteBaseUrl = state.siteBaseUrl,
                subscriptionsState = state.globalSubscriptions,
                onOpenLogin = {
                    profileDialogOpen = false
                    loginDialogOpen = true
                },
                onOpenLibrary = {
                    profileDialogOpen = false
                    onOpenLibraryFilter()
                },
                onOpenAnime = { animeId ->
                    profileDialogOpen = false
                    onOpenAnime(animeId)
                },
                onUnsubscribeVideoSubscription = onUnsubscribeVideoSubscription,
                onRefreshVideoSubscriptions = onRefreshVideoSubscriptions,
                onLogout = {
                    profileDialogOpen = false
                    onLogout()
                },
                onRegisterModalInputActionHandler = { handler -> modalInputActionHandler = handler },
                onDismiss = { profileDialogOpen = false },
            )
        }

        if (settingsDialogOpen) {
            SettingsDialog(
                settings = state.settings,
                offlineEntries = state.offlineEntries,
                updateState = state.updateState,
                onSettingsChange = onSettingsChange,
                onDeleteOfflineVideo = onDeleteOfflineVideo,
                onDeleteOfflineAnime = onDeleteOfflineAnime,
                onClearAppContentCache = onClearAppContentCache,
                onCheckForUpdates = onCheckForUpdates,
                onRegisterModalInputActionHandler = { handler -> modalInputActionHandler = handler },
                onDismiss = { settingsDialogOpen = false },
            )
        }
        if (pendingUpdate != null) {
            UpdateCheckDialog(
                updateState = LoadState.Ready(pendingUpdate),
                onInstallUpdate = { info ->
                    autoUpdatePromptDismissed = true
                    UpdateDownloadService.start(context, info.apkUrl, info.version)
                },
                onDismiss = { autoUpdatePromptDismissed = true },
            )
        }
        if (state.forcedOfflineMode && state.route !is AppRoute.Player) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp),
            ) {
                OfflineModeChip()
            }
        }
        }
    }

}
