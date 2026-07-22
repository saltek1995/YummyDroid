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
internal fun BrowseScreen(
    state: YummyDroidUiState,
    catalogGridState: LazyGridState,
    scheduleListState: LazyListState,
    historyGridState: LazyGridState,
    homeFocusRequest: HomeFocusRequest?,
    activeFocusRequestNonce: Long,
    onHomeFocusRequestHandled: (HomeFocusRequest) -> Unit,
    onCatalogFocusedItemChange: (Int) -> Unit,
    onScheduleFocusedItemChange: (Int) -> Unit,
    onHistoryFocusedItemChange: (Int) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMoreAnime: () -> Unit,
    onBrowseSectionChange: (BrowseSection) -> Unit,
    onFiltersChange: (BrowseFilters) -> Unit,
    onResetFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    onClearDownloadHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAnime: (Long) -> Unit,
    onRequestHomeFocusReset: () -> Unit,
) {
    val isAuthorized = state.auth.profile != null
    val browsePagerSections = remember(isAuthorized) { visibleBrowseSections(isAuthorized) }
    val effectiveHomeSection = if (state.homeSection == BrowseSection.History && !isAuthorized) {
        BrowseSection.Catalog
    } else {
        state.homeSection
    }
    LaunchedEffect(state.homeSection, isAuthorized) {
        if (state.homeSection == BrowseSection.History && !isAuthorized) {
            onBrowseSectionChange(BrowseSection.Catalog)
        }
    }
    val isCatalog = effectiveHomeSection == BrowseSection.Catalog
    val isSearching = isCatalog && state.searchQuery.isNotBlank()
    val contentState = if (isSearching) state.searchResults else state.featured
    val pagingState = if (isSearching) state.searchPaging else state.featuredPaging
    val configuration = LocalConfiguration.current
    val isWide = configuration.screenWidthDp >= 720
    val isTelevision = remember(configuration.uiMode) {
        val uiMode = configuration.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
        uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    }
    val dpadLayerFocusRequestNonce = if (isTelevision) activeFocusRequestNonce else 0L
    var searchDialogOpen by remember { mutableStateOf(false) }
    var filtersDialogOpen by remember { mutableStateOf(false) }
    val browseModalInputActionHandler by rememberUpdatedState { action: InputAction ->
        if (action != InputAction.Back) {
            false
        } else {
            when {
                filtersDialogOpen -> {
                    filtersDialogOpen = false
                    true
                }
                searchDialogOpen -> {
                    searchDialogOpen = false
                    true
                }
                else -> false
            }
        }
    }
    DisposableEffect(searchDialogOpen, filtersDialogOpen, onRegisterModalInputActionHandler) {
        if (searchDialogOpen || filtersDialogOpen) {
            onRegisterModalInputActionHandler { action -> browseModalInputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
    val activeDownloadCount = state.downloadQueue.tasks.count { task ->
        task.state == DownloadTaskState.Queued ||
            task.state == DownloadTaskState.Running ||
            task.state == DownloadTaskState.Paused
    }
    val catalogFocusFirstRequest = FocusFirstRequest(
        persistentNonce = state.homeFocusResetNonce,
        transientRequest = homeFocusRequest?.takeIf { it.section == BrowseSection.Catalog },
    )
    val scheduleFocusFirstRequest = FocusFirstRequest(
        transientRequest = homeFocusRequest?.takeIf { it.section == BrowseSection.Schedule },
    )
    val historyFocusFirstRequest = FocusFirstRequest(
        transientRequest = homeFocusRequest?.takeIf { it.section == BrowseSection.History },
    )
    val browseSwipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    val latestOnBrowseSectionChange by rememberUpdatedState(onBrowseSectionChange)

    fun selectAdjacentBrowseSection(delta: Int) {
        val currentIndex = browsePagerSections.indexOf(effectiveHomeSection)
        if (currentIndex < 0) return
        val nextSection = browsePagerSections.getOrNull(currentIndex + delta) ?: return
        latestOnBrowseSectionChange(nextSection)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        BrowseTopBarModern(
            onOpenSearch = { searchDialogOpen = true },
            onOpenFilters = { filtersDialogOpen = true },
            onOpenSettings = onOpenSettings,
            onOpenDownloads = onOpenDownloads,
            auth = state.auth,
            activeFilters = state.filters.activeCount,
            activeSearch = isSearching,
            activeDownloadCount = activeDownloadCount,
            forcedOfflineMode = state.forcedOfflineMode,
            onOpenLogin = onOpenLogin,
            onOpenProfile = onOpenProfile,
            isWide = isWide,
            activeSection = effectiveHomeSection,
            visibleSections = browsePagerSections,
            onSectionSelected = onBrowseSectionChange,
            showCompactControls = false,
        )

        Box(modifier = Modifier.weight(1f)) {
            AnimatedContent(
                targetState = effectiveHomeSection,
                transitionSpec = {
                    val initialIndex = browsePagerSections.indexOf(initialState).takeIf { it >= 0 } ?: 0
                    val targetIndex = browsePagerSections.indexOf(targetState).takeIf { it >= 0 } ?: initialIndex
                    if (targetIndex >= initialIndex) {
                        slideInHorizontally { width -> width } togetherWith
                            slideOutHorizontally { width -> -width }
                    } else {
                        slideInHorizontally { width -> -width } togetherWith
                            slideOutHorizontally { width -> width }
                    }
                },
                label = "browse-section",
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(browseSwipeThresholdPx, effectiveHomeSection, browsePagerSections) {
                        var totalDrag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { change, dragAmount ->
                                totalDrag += dragAmount
                                change.consume()
                            },
                            onDragEnd = {
                                if (abs(totalDrag) >= browseSwipeThresholdPx) {
                                    selectAdjacentBrowseSection(if (totalDrag < 0f) 1 else -1)
                                }
                                totalDrag = 0f
                            },
                            onDragCancel = { totalDrag = 0f },
                        )
                    },
            ) { section ->
                when (section) {
                            BrowseSection.Catalog -> AnimeGridSection(
                                contentState = contentState,
                                pagingState = pagingState,
                                gridState = catalogGridState,
                                cardSize = state.settings.posterCardSize,
                                focusFirstRequest = catalogFocusFirstRequest,
                                focusCurrentRequestNonce = dpadLayerFocusRequestNonce,
                                onFocusFirstRequestHandled = { request ->
                                    request.transientRequest?.let(onHomeFocusRequestHandled)
                                },
                                onFocusedIndexChange = onCatalogFocusedItemChange,
                                emptyMessage = if (isSearching) uiText("Ничего не найдено") else uiText("Каталог пуст"),
                                onRetry = onRefresh,
                                onLoadMore = onLoadMoreAnime,
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.Schedule -> ScheduleSection(
                                state = state.schedule,
                                filters = state.filters,
                                catalog = state.filterCatalog.readyDataOrNull() ?: FilterCatalog.Empty,
                                listState = scheduleListState,
                                focusFirstRequest = scheduleFocusFirstRequest,
                                focusCurrentRequestNonce = dpadLayerFocusRequestNonce,
                                onFocusFirstRequestHandled = { request ->
                                    request.transientRequest?.let(onHomeFocusRequestHandled)
                                },
                                onFocusedIndexChange = onScheduleFocusedItemChange,
                                onRetry = onRefresh,
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.History -> AnimeGridSection(
                                contentState = state.historyAnime,
                                pagingState = PagingUiState(canLoadMore = false),
                                gridState = historyGridState,
                                cardSize = state.settings.posterCardSize,
                                focusFirstRequest = historyFocusFirstRequest,
                                focusCurrentRequestNonce = dpadLayerFocusRequestNonce,
                                onFocusFirstRequestHandled = { request ->
                                    request.transientRequest?.let(onHomeFocusRequestHandled)
                                },
                                onFocusedIndexChange = onHistoryFocusedItemChange,
                                emptyMessage = uiText("История пуста"),
                                onRetry = onRefresh,
                                onLoadMore = {},
                                onOpenAnime = onOpenAnime,
                            )
                            BrowseSection.Downloads -> DownloadsSection(
                                state = state,
                                focusCurrentRequestNonce = dpadLayerFocusRequestNonce,
                                onClearHistory = onClearDownloadHistory,
                                onCancelDownload = onCancelDownload,
                                onPauseDownload = onPauseDownload,
                                onResumeDownload = onResumeDownload,
                                onOpenAnime = onOpenAnime,
                            )
                }
            }
        }

        if (!isWide) {
            BrowseBottomBarModern(
                onOpenSearch = { searchDialogOpen = true },
                onOpenFilters = { filtersDialogOpen = true },
                onOpenSettings = onOpenSettings,
                onOpenDownloads = onOpenDownloads,
                auth = state.auth,
                activeFilters = state.filters.activeCount,
                activeSearch = isSearching,
                activeDownloadCount = activeDownloadCount,
                onOpenLogin = onOpenLogin,
                onOpenProfile = onOpenProfile,
                activeSection = effectiveHomeSection,
                visibleSections = browsePagerSections,
                onSectionSelected = onBrowseSectionChange,
            )
        }
    }

    if (searchDialogOpen) {
        SearchDialog(
            query = state.searchQuery,
            onQueryChange = onQueryChange,
            onDismiss = { searchDialogOpen = false },
            onExitDown = {
                searchDialogOpen = false
                onRequestHomeFocusReset()
            },
        )
    }

    if (filtersDialogOpen) {
        FiltersDialogAccordion(
            filters = state.filters,
            auth = state.auth,
            catalogState = state.filterCatalog,
            offlineEntries = state.offlineEntries.readyListOrEmpty(),
            forcedOfflineMode = state.forcedOfflineMode,
            onApply = onFiltersChange,
            onReset = onResetFilters,
            onDismiss = { filtersDialogOpen = false },
        )
    }
}

@Composable
internal fun AnimeGridSection(
    contentState: LoadState<List<Anime>>,
    pagingState: PagingUiState,
    gridState: LazyGridState,
    cardSize: PosterCardSize,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    onFocusFirstRequestHandled: (FocusFirstRequest) -> Unit = {},
    onFocusedIndexChange: (Int) -> Unit = {},
    emptyMessage: String,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val columnsCount = remember(screenWidth, cardSize) {
        cardSize.resolveCatalogColumns(screenWidth)
    }
    AnimeListStateContent(
        state = contentState,
        onRetry = onRetry,
        emptyMessage = emptyMessage,
    ) { animes ->
        val focusScope = rememberCoroutineScope()
        val itemFocusRequesters = remember(animes.size, columnsCount) {
            List(animes.size) { FocusRequester() }
        }
        var focusedAnimeIndex by rememberSaveable(columnsCount) { mutableIntStateOf(-1) }
        var handledPersistentFocusResetNonce by remember { mutableLongStateOf(0L) }
        var handledTransientFocusResetNonce by remember { mutableLongStateOf(0L) }
        var gridNavigationJob by remember(columnsCount) { mutableStateOf<Job?>(null) }
        val latestOnFocusFirstRequestHandled by rememberUpdatedState(onFocusFirstRequestHandled)
        val latestOnFocusedIndexChange by rememberUpdatedState(onFocusedIndexChange)

        fun updateFocusedAnimeIndex(index: Int) {
            focusedAnimeIndex = index
            latestOnFocusedIndexChange(index)
        }

        fun rowStartIndex(index: Int): Int {
            return if (columnsCount > 0) (index / columnsCount) * columnsCount else index
        }

        fun requestAnimeItemFocus(index: Int): Boolean {
            val requester = itemFocusRequesters.getOrNull(index) ?: return false
            return runCatching { requester.requestFocus() }.isSuccess
        }

        fun requestGridFocus(index: Int, alignRowToTop: Boolean) {
            if (index !in animes.indices) return
            gridNavigationJob?.cancel()
            updateFocusedAnimeIndex(index)
            if (alignRowToTop) {
                val rowStart = rowStartIndex(index)
                gridNavigationJob = focusScope.launch {
                    gridState.animateScrollToItem(rowStart, 0)
                    withFrameNanos { }
                    requestAnimeItemFocus(index)
                }
            } else {
                requestAnimeItemFocus(index)
                gridNavigationJob = null
            }
        }

        fun handleGridKey(key: Key): Boolean {
            if (columnsCount <= 0) return false
            val currentIndex = focusedAnimeIndex.takeIf { it in animes.indices } ?: 0
            val currentColumn = currentIndex % columnsCount
            val currentRow = currentIndex / columnsCount

            fun visiblePageRows(): Int {
                return gridState.layoutInfo.visibleItemsInfo
                    .asSequence()
                    .map { it.index }
                    .filter { it in animes.indices }
                    .map { it / columnsCount }
                    .distinct()
                    .count()
                    .minus(1)
                    .coerceAtLeast(1)
            }

            fun indexInRow(row: Int): Int {
                val maxRow = animes.lastIndex / columnsCount
                val rowStart = row.coerceIn(0, maxRow) * columnsCount
                return (rowStart + currentColumn).coerceAtMost(animes.lastIndex)
            }

            val targetIndex = when (key) {
                Key.DirectionLeft -> if (currentColumn > 0) currentIndex - 1 else currentIndex
                Key.DirectionRight -> if (currentColumn < columnsCount - 1 && currentIndex < animes.lastIndex) {
                    currentIndex + 1
                } else {
                    currentIndex
                }
                Key.DirectionUp -> {
                    val target = currentIndex - columnsCount
                    if (target >= 0) target else return false
                }
                Key.DirectionDown -> {
                    val target = currentIndex + columnsCount
                    if (target <= animes.lastIndex) {
                        target
                    } else {
                        if (pagingState.canLoadMore && !pagingState.isLoadingMore) onLoadMore()
                        return true
                    }
                }
                Key.PageDown -> {
                    val target = indexInRow(currentRow + visiblePageRows())
                    if (
                        target >= animes.lastIndex - columnsCount * 2 &&
                        pagingState.canLoadMore &&
                        !pagingState.isLoadingMore
                    ) {
                        onLoadMore()
                    }
                    target
                }
                Key.PageUp -> indexInRow(currentRow - visiblePageRows())
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter, Key.Spacebar -> {
                    onOpenAnime(animes[currentIndex].id)
                    return true
                }
                else -> return false
            }
            requestGridFocus(
                index = targetIndex,
                alignRowToTop = key == Key.DirectionUp ||
                    key == Key.DirectionDown ||
                    key == Key.PageUp ||
                    key == Key.PageDown,
            )
            return true
        }

        LaunchedEffect(focusFirstRequest, animes.size, columnsCount) {
            if (animes.isEmpty()) return@LaunchedEffect
            val shouldHandlePersistent = focusFirstRequest.persistentNonce > 0L &&
                focusFirstRequest.persistentNonce != handledPersistentFocusResetNonce
            val shouldHandleTransient = focusFirstRequest.transientNonce > 0L &&
                focusFirstRequest.transientNonce != handledTransientFocusResetNonce
            if (!shouldHandlePersistent && !shouldHandleTransient) return@LaunchedEffect
            val targetIndex = 0
            val targetRowStart = rowStartIndex(targetIndex)
            val previousNavigationJob = gridNavigationJob
            gridNavigationJob = null
            previousNavigationJob?.cancelAndJoin()
            updateFocusedAnimeIndex(targetIndex)
            gridState.scrollToItem(targetRowStart, 0)
            withFrameNanos { }
            requestAnimeItemFocus(targetIndex)
            withFrameNanos { }
            gridState.scrollToItem(targetRowStart, 0)
            if (shouldHandlePersistent) {
                handledPersistentFocusResetNonce = focusFirstRequest.persistentNonce
            }
            if (shouldHandleTransient) {
                handledTransientFocusResetNonce = focusFirstRequest.transientNonce
                latestOnFocusFirstRequestHandled(focusFirstRequest)
            }
        }

        LaunchedEffect(focusCurrentRequestNonce, animes.size, columnsCount) {
            if (
                focusCurrentRequestNonce <= 0L ||
                animes.isEmpty()
            ) {
                return@LaunchedEffect
            }
            val targetIndex = focusedAnimeIndex
                .takeIf { index -> index in animes.indices }
                ?: gridState.firstVisibleItemIndex.coerceIn(0, animes.lastIndex)
            withFrameNanos { }
            updateFocusedAnimeIndex(targetIndex)
            if (!requestAnimeItemFocus(targetIndex)) {
                gridState.scrollToItem(rowStartIndex(targetIndex), 0)
                withFrameNanos { }
                requestAnimeItemFocus(targetIndex)
            }
        }

        LaunchedEffect(animes.size) {
            if (animes.isEmpty()) {
                updateFocusedAnimeIndex(-1)
            } else if (focusedAnimeIndex > animes.lastIndex) {
                updateFocusedAnimeIndex(animes.lastIndex)
            }
        }

        LaunchedEffect(
            focusedAnimeIndex,
            animes.size,
            columnsCount,
            pagingState.canLoadMore,
            pagingState.isLoadingMore,
            pagingState.error,
        ) {
            if (
                focusedAnimeIndex < 0 ||
                columnsCount <= 0 ||
                !pagingState.canLoadMore ||
                pagingState.isLoadingMore ||
                pagingState.error != null
            ) {
                return@LaunchedEffect
            }
            val focusedRow = focusedAnimeIndex / columnsCount
            val lastLoadedRow = animes.lastIndex.coerceAtLeast(0) / columnsCount
            if (lastLoadedRow - focusedRow < 2) {
                onLoadMore()
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columnsCount),
            state = gridState,
            contentPadding = PaddingValues(24.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
            modifier = Modifier
                .fillMaxSize()
                .focusGroup()
                .onPreviewKeyEvent { event ->
                    event.type == KeyEventType.KeyDown && handleGridKey(event.key)
                },
        ) {
            itemsIndexed(animes, key = { index, anime -> "anime-grid:$index:${anime.id}:${anime.title}" }) { index, anime ->
                AnimeCard(
                    anime = anime,
                    onClick = { onOpenAnime(anime.id) },
                    focused = index == focusedAnimeIndex,
                    modifier = Modifier
                        .focusRequester(itemFocusRequesters[index])
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                updateFocusedAnimeIndex(index)
                            }
                        },
                )
            }

            if (pagingState.isLoadingMore || pagingState.canLoadMore || pagingState.error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PagingGridFooter(
                        paging = pagingState,
                        onLoadMore = onLoadMore,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScheduleSection(
    state: LoadState<List<ScheduleAnime>>,
    filters: BrowseFilters,
    catalog: FilterCatalog,
    listState: LazyListState,
    focusFirstRequest: FocusFirstRequest,
    focusCurrentRequestNonce: Long,
    onFocusFirstRequestHandled: (FocusFirstRequest) -> Unit = {},
    onFocusedIndexChange: (Int) -> Unit = {},
    onRetry: () -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    when (state) {
        LoadState.Loading -> LoadingPane(Modifier.fillMaxSize())
        is LoadState.Error -> ErrorPane(
            message = state.message,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize(),
        )
        is LoadState.Ready -> {
            var hidePastItems by rememberSaveable { mutableStateOf(true) }
            val filteredItems = remember(state.data, filters, catalog) {
                state.data.filteredAndSortedSchedule(filters, catalog)
            }
            val upcomingItems = remember(filteredItems) { upcomingScheduleItems(filteredItems) }
            val visibleItems = if (hidePastItems) upcomingItems else filteredItems
            val currentItemFocusRequester = remember { FocusRequester() }
            var focusedScheduleIndex by rememberSaveable { mutableIntStateOf(0) }
            var handledPersistentFocusResetNonce by remember { mutableLongStateOf(0L) }
            var handledTransientFocusResetNonce by remember { mutableLongStateOf(0L) }
            val latestOnFocusFirstRequestHandled by rememberUpdatedState(onFocusFirstRequestHandled)
            val latestOnFocusedIndexChange by rememberUpdatedState(onFocusedIndexChange)

            fun updateFocusedScheduleIndex(index: Int) {
                focusedScheduleIndex = index
                latestOnFocusedIndexChange(index)
            }

            LaunchedEffect(focusFirstRequest, visibleItems.size) {
                if (visibleItems.isEmpty()) return@LaunchedEffect
                val shouldHandlePersistent = focusFirstRequest.persistentNonce > 0L &&
                    focusFirstRequest.persistentNonce != handledPersistentFocusResetNonce
                val shouldHandleTransient = focusFirstRequest.transientNonce > 0L &&
                    focusFirstRequest.transientNonce != handledTransientFocusResetNonce
                if (
                    !shouldHandlePersistent &&
                    !shouldHandleTransient
                ) {
                    return@LaunchedEffect
                }
                listState.scrollToItem(0)
                updateFocusedScheduleIndex(0)
                withFrameNanos { }
                runCatching { currentItemFocusRequester.requestFocus() }
                if (shouldHandlePersistent) {
                    handledPersistentFocusResetNonce = focusFirstRequest.persistentNonce
                }
                if (shouldHandleTransient) {
                    handledTransientFocusResetNonce = focusFirstRequest.transientNonce
                    latestOnFocusFirstRequestHandled(focusFirstRequest)
                }
            }

            LaunchedEffect(visibleItems.size) {
                updateFocusedScheduleIndex(
                    when {
                    visibleItems.isEmpty() -> -1
                    focusedScheduleIndex < 0 -> 0
                    focusedScheduleIndex !in visibleItems.indices -> visibleItems.lastIndex
                    else -> focusedScheduleIndex
                    },
                )
            }

            LaunchedEffect(focusCurrentRequestNonce, visibleItems.size, focusedScheduleIndex) {
                if (
                    focusCurrentRequestNonce <= 0L ||
                    visibleItems.isEmpty()
                ) {
                    return@LaunchedEffect
                }
                val firstVisibleScheduleIndex = (listState.firstVisibleItemIndex - 1)
                    .coerceIn(0, visibleItems.lastIndex)
                val targetIndex = focusedScheduleIndex
                    .takeIf { index -> index in visibleItems.indices }
                    ?: firstVisibleScheduleIndex
                val targetListIndex = targetIndex + 1
                val targetIsVisible = listState.layoutInfo.visibleItemsInfo.any { item ->
                    item.index == targetListIndex
                }
                if (!targetIsVisible) {
                    listState.scrollToItem(targetListIndex, 0)
                }
                withFrameNanos { }
                updateFocusedScheduleIndex(targetIndex)
                runCatching { currentItemFocusRequester.requestFocus() }
            }

            if (state.data.isEmpty()) {
                EmptyPane(message = uiText("Расписание пока пустое"), modifier = Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        SchedulePastFilterToggle(
                            hidePastItems = hidePastItems,
                            hiddenCount = filteredItems.size - upcomingItems.size,
                            onToggle = { hidePastItems = !hidePastItems },
                        )
                    }

                    if (visibleItems.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (filteredItems.isEmpty()) {
                                        uiText("По выбранным фильтрам ничего не найдено")
                                    } else {
                                        uiText("Ближайших выходов пока нет")
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    lazyItemsIndexed(
                        visibleItems,
                        key = { index, item -> "schedule:$index:${item.anime.id}:${item.nextEpisodeAtSeconds}" },
                    ) { index, item ->
                        ScheduleRow(
                            item = item,
                            onOpenAnime = onOpenAnime,
                            modifier = Modifier
                                .then(
                                    if (index == focusedScheduleIndex) {
                                        Modifier.focusRequester(currentItemFocusRequester)
                                    } else {
                                        Modifier
                                    },
                                )
                                .onFocusChanged { focusState ->
                                    if (focusState.hasFocus) {
                                        updateFocusedScheduleIndex(index)
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SchedulePastFilterToggle(
    hidePastItems: Boolean,
    hiddenCount: Int,
    onToggle: () -> Unit,
) {
    val role = if (hidePastItems) YummySurfaceRole.ActiveRow else YummySurfaceRole.Row
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shape = RoundedCornerShape(8.dp)
        Surface(
            modifier = Modifier
                .height(36.dp)
                .dpadClickable(shape, onToggle),
            color = yummySurfaceColor(role),
            contentColor = yummySurfaceContentColor(role),
            border = yummySurfaceBorder(role),
            shape = shape,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(17.dp))
                Text(
                    text = if (hidePastItems) uiText("Прошедшие скрыты") else uiText("Прошедшие показаны"),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (hiddenCount > 0) {
            Text(
                text = if (hidePastItems) "$hiddenCount ${uiText("скрыто")}" else "$hiddenCount ${uiText("прошедших")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun ScheduleRow(
    item: ScheduleAnime,
    onOpenAnime: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(shape) { onOpenAnime(item.anime.id) },
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                url = item.anime.posterUrl,
                contentDescription = item.anime.title,
                modifier = Modifier
                    .width(72.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = item.anime.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${uiText("Вышло")} ${item.airedEpisodes}" +
                        if (item.totalEpisodes > 0) " ${uiText("из")} ${item.totalEpisodes}" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                item.nextEpisodeAtSeconds.takeIf { it > 0L }?.let { next ->
                    Text(
                        text = "${uiText("Следующая")}: ${formatScheduleTimestamp(next)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
