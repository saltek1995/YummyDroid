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
internal fun DetailsHeroModern(
    details: AnimeDetails,
    isWide: Boolean,
    useThreeColumnHero: Boolean,
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadVideos: List<VideoVariant>,
    downloadedSummary: String?,
    episodeSummary: String,
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    modifier: Modifier = Modifier,
    detailsExtras: LoadState<AnimeDetailsExtras> = LoadState.Ready(AnimeDetailsExtras()),
    showMarkPanel: Boolean,
    showHeroRating: Boolean = false,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit = {},
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    canDownload: Boolean,
) {
    val wideHeroActionsFocusRequester = remember { FocusRequester() }

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        details.backdropUrl?.let { backdrop ->
            PosterImage(
                url = backdrop,
                contentDescription = null,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        alpha = if (isWide) 1f else 0.72f
                    },
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = if (isWide) 0.18f else 0.36f)),
            )
            if (!isWide) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.42f),
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Black.copy(alpha = 0.50f),
                                ),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.36f),
                                    Color.Black.copy(alpha = 0.28f),
                                    Color.Black.copy(alpha = 0.52f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.86f),
                                ),
                            ),
                        ),
                )
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (isWide) {
                                listOf(
                                    Color.Black.copy(alpha = 0.32f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.94f),
                                    MaterialTheme.colorScheme.background,
                                )
                            } else {
                                listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.32f),
                                    Color.Black.copy(alpha = 0.24f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.74f),
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                    MaterialTheme.colorScheme.background,
                                )
                            },
                        ),
                    ),
            )
        }
        if (!isWide) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF050912).copy(alpha = 0.10f)),
            )
        }

        if (isWide) {
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            val screenHeightDp = LocalConfiguration.current.screenHeightDp
            val compactWideHero = screenHeightDp < 560
            val posterWidth = when {
                compactWideHero -> 92.dp
                useThreeColumnHero -> 128.dp
                else -> 120.dp
            }
            val sidePanelWidth = when {
                compactWideHero -> 292.dp
                screenWidthDp >= 1280 -> 368.dp
                screenWidthDp >= 1100 -> 340.dp
                else -> 300.dp
            }
            val horizontalGap = if (compactWideHero) 10.dp else 14.dp
            val topPadding = if (compactWideHero) 8.dp else 14.dp
            val bottomPadding = if (compactWideHero) 8.dp else 12.dp
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 20.dp, top = topPadding, end = 20.dp, bottom = bottomPadding),
                verticalArrangement = Arrangement.spacedBy(if (compactWideHero) 5.dp else 9.dp),
            ) {
                DetailsHeroWideHeading(
                    details = details,
                    compact = compactWideHero,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 76.dp, end = 76.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(horizontalGap),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    DetailsPoster(
                        posterUrl = details.posterUrl,
                        title = details.title,
                        modifier = Modifier.width(posterWidth),
                    )
                    DetailsHeroText(
                        details = details,
                        compact = compactWideHero,
                        showHeading = false,
                        showGenres = false,
                        downloadedSummary = downloadedSummary,
                        episodeSummary = episodeSummary,
                        watchVideo = watchVideo,
                        resumeTarget = resumeTarget,
                        downloadVideos = downloadVideos,
                        auth = auth,
                        animeMark = animeMark,
                        detailsExtras = detailsExtras,
                        showMarkPanel = false,
                        showHeroRating = false,
                        onOpenLogin = onOpenLogin,
                        onOpenProfile = onOpenProfile,
                        onSelectListMark = onSelectListMark,
                        onToggleFavorite = onToggleFavorite,
                        onSetAnimeRating = onSetAnimeRating,
                        onPlayVideo = onPlayVideo,
                        onPlayVideoAt = onPlayVideoAt,
                        defaultDownloadQuality = defaultDownloadQuality,
                        onResolveDownloadQualities = onResolveDownloadQualities,
                        onDownloadAllVideos = onDownloadAllVideos,
                        onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                        canDownload = canDownload,
                        heroActionsFocusRequester = wideHeroActionsFocusRequester,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = if (compactWideHero) 156.dp else 0.dp),
                    )
                    DetailsHeroSidePanel(
                        ratingDetails = details.ratingDetails,
                        auth = auth,
                        animeMark = animeMark,
                        detailsExtras = detailsExtras,
                        showMarkPanel = showMarkPanel,
                        showHeroRating = showHeroRating,
                        onOpenLogin = onOpenLogin,
                        onOpenProfile = onOpenProfile,
                        onSelectListMark = onSelectListMark,
                        onToggleFavorite = onToggleFavorite,
                        onSetAnimeRating = onSetAnimeRating,
                        leftExitRequester = wideHeroActionsFocusRequester,
                        modifier = Modifier
                            .width(sidePanelWidth)
                            .heightIn(max = if (compactWideHero) 150.dp else 176.dp),
                    )
                }
            }
        } else {
            DetailsHeroMobile(
                details = details,
                watchVideo = watchVideo,
                resumeTarget = resumeTarget,
                downloadedSummary = downloadedSummary,
                episodeSummary = episodeSummary,
                downloadVideos = downloadVideos,
                onPlayVideo = onPlayVideo,
                onPlayVideoAt = onPlayVideoAt,
                defaultDownloadQuality = defaultDownloadQuality,
                onResolveDownloadQualities = onResolveDownloadQualities,
                onDownloadAllVideos = onDownloadAllVideos,
                onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                canDownload = canDownload,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Color(0xFF050912).copy(alpha = 0.14f))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF050912).copy(alpha = 0.42f),
                                Color(0xFF050912).copy(alpha = 0.30f),
                                Color.Black.copy(alpha = 0.62f),
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    )
                    .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 14.dp),
            )
        }
    }
}

@Composable
internal fun DetailsHeroWideHeading(
    details: AnimeDetails,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp),
    ) {
        Text(
            text = details.title,
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnimeMarkPanelModern(
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    leftExitRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val profile = auth.profile
    val mark = animeMark.readyDataOrNull() ?: UserAnimeMark()

    if (profile == null) {
        Box(modifier = modifier) {
        DialogActionButton(
            text = uiText("Войти"),
                primary = true,
                onClick = onOpenLogin,
                enabled = !auth.loading,
            )
        }
        return
    }

    AnimeMarkSegmentedControl(
        mark = mark,
        onSelectListMark = onSelectListMark,
        onToggleFavorite = onToggleFavorite,
        leftExitRequester = leftExitRequester,
        modifier = modifier,
    )
}

@Composable
internal fun AnimeMarkSegmentedControl(
    mark: UserAnimeMark,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    leftExitRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier.widthIn(max = 392.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.30f)),
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val listMarks = UserAnimeListMark.displayOrder
            val totalMarks = listMarks.size + 1
            listMarks.forEachIndexed { index, listMark ->
                AnimeMarkSegment(
                    icon = listMark.icon(),
                    title = listMark.title,
                    color = listMark.siteColor(),
                    selected = mark.list == listMark,
                    onClick = { onSelectListMark(listMark) },
                    index = index,
                    total = totalMarks,
                    leftExitRequester = leftExitRequester,
                    modifier = Modifier.weight(1f),
                )
                MarkDivider()
            }
            AnimeMarkSegment(
                icon = Icons.Default.Favorite,
                title = uiText("Любимые"),
                color = favoriteMarkColor,
                selected = mark.isFavorite,
                onClick = onToggleFavorite,
                index = totalMarks - 1,
                total = totalMarks,
                leftExitRequester = leftExitRequester,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun DetailsHeroSidePanel(
    ratingDetails: RatingDetails,
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    showMarkPanel: Boolean,
    showHeroRating: Boolean,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    leftExitRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showHeroRating && detailsExtras is LoadState.Ready) {
                CompactRatingScale(
                    rating = detailsExtras.data.rating,
                    isAuthorized = auth.profile != null,
                    onSetAnimeRating = onSetAnimeRating,
                    leftExitRequester = leftExitRequester,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showMarkPanel) {
                AnimeMarkPanelModern(
                    auth = auth,
                    animeMark = animeMark,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    onSelectListMark = onSelectListMark,
                    onToggleFavorite = onToggleFavorite,
                    leftExitRequester = leftExitRequester,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DetailsRatingStrip(
                ratingDetails = ratingDetails,
                leftExitRequester = leftExitRequester,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun CompactRatingScale(
    rating: AnimeRatingSummary,
    isAuthorized: Boolean,
    onSetAnimeRating: (Int?) -> Unit,
    leftExitRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    if (!isAuthorized) return
    RatingScale(
        selected = rating.userRating,
        onSelected = onSetAnimeRating,
        leftExitRequester = leftExitRequester,
        modifier = modifier,
    )
}

@Composable
internal fun AnimeMarkSegment(
    icon: ImageVector,
    title: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = -1,
    total: Int = 0,
    leftExitRequester: FocusRequester? = null,
) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .stopHorizontalFocusEscape(index, total, leftExit = leftExitRequester)
            .background(if (selected) color else Color.Transparent)
            .focusRing(shape)
            .dpadClickable(shape, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) Color.White else color,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
internal fun MarkDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    )
}

internal fun UserProfile.siteProfileUrl(siteBaseUrl: String): String {
    val base = siteBaseUrl.trim().ifBlank { DEFAULT_SITE_BASE_URL }.trimEnd('/')
    return "$base/users/id$id"
}

internal fun sitePageUrl(siteBaseUrl: String, path: String): String {
    val base = siteBaseUrl.trim().ifBlank { DEFAULT_SITE_BASE_URL }.trimEnd('/')
    return "$base/${path.trim().trimStart('/')}"
}

internal fun Context.openUrl(url: String) {
    val normalized = url.trim()
    if (normalized.isBlank()) return
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, normalized.toUri()))
    }.onFailure {
        Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun DetailsHeroMobile(
    details: AnimeDetails,
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadedSummary: String?,
    episodeSummary: String,
    downloadVideos: List<VideoVariant>,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    canDownload: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            text = details.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black,
            maxLines = when {
                details.title.length > 90 -> 6
                details.title.length > 55 -> 5
                else -> 3
            },
            overflow = TextOverflow.Clip,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            DetailsPoster(
                posterUrl = details.posterUrl,
                title = details.title,
                modifier = Modifier.width(112.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailsHeroMeta(
                    details = details,
                    compact = true,
                    downloadedSummary = downloadedSummary,
                    episodeSummary = episodeSummary,
                    modifier = Modifier.fillMaxWidth(),
                )
                DetailsHeroActions(
                    watchVideo = watchVideo,
                    resumeTarget = resumeTarget,
                    downloadVideos = downloadVideos,
                    onPlayVideo = onPlayVideo,
                    onPlayVideoAt = onPlayVideoAt,
                    defaultDownloadQuality = defaultDownloadQuality,
                    onResolveDownloadQualities = onResolveDownloadQualities,
                    onDownloadAllVideos = onDownloadAllVideos,
                    onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                    canDownload = canDownload,
                )
            }
        }
    }
}

@Composable
internal fun DetailsHeroMeta(
    details: AnimeDetails,
    compact: Boolean,
    downloadedSummary: String?,
    episodeSummary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (details.meta.isNotBlank()) {
            Text(
                text = details.meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (compact) 3 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            details.rating?.let { rating ->
                AssistChip(
                    onClick = {},
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(formatRating(rating)) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                )
            }
            AssistChip(
                onClick = {},
                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(formatViews(details.views)) },
            )
        }

        if (episodeSummary.isNotBlank()) {
            Text(
                text = episodeSummary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        downloadedSummary?.let { summary ->
            Text(
                text = summary,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DetailsHeroText(
    details: AnimeDetails,
    compact: Boolean,
    modifier: Modifier = Modifier,
    showHeading: Boolean = true,
    showGenres: Boolean = true,
    downloadedSummary: String? = null,
    episodeSummary: String = details.episodeSummary,
    watchVideo: VideoVariant? = null,
    resumeTarget: HeroResumeTarget? = null,
    downloadVideos: List<VideoVariant> = emptyList(),
    auth: AuthUiState = AuthUiState(),
    animeMark: LoadState<UserAnimeMark?> = LoadState.Ready(null),
    detailsExtras: LoadState<AnimeDetailsExtras> = LoadState.Ready(AnimeDetailsExtras()),
    showMarkPanel: Boolean = false,
    showHeroRating: Boolean = false,
    onOpenLogin: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    onSelectListMark: (UserAnimeListMark) -> Unit = {},
    onToggleFavorite: () -> Unit = {},
    onSetAnimeRating: (Int?) -> Unit = {},
    onPlayVideo: (VideoVariant) -> Unit = {},
    onPlayVideoAt: (VideoVariant, Long) -> Unit = { _, _ -> },
    defaultDownloadQuality: PreferredQuality = PreferredQuality.Auto,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality> = { _, _, _ -> emptyList() },
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit = { _, _ -> },
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit = {},
    canDownload: Boolean = true,
    heroActionsFocusRequester: FocusRequester? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp),
    ) {
        if (showHeading) {
            Text(
                text = details.title,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black,
                maxLines = if (compact) 3 else 5,
                overflow = TextOverflow.Clip,
            )

            if (details.meta.isNotBlank()) {
                Text(
                    text = details.meta,
                    style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else if (details.meta.isNotBlank()) {
            Text(
                text = details.meta,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
            details.rating?.let { rating ->
                AssistChip(
                    onClick = {},
                    leadingIcon = { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(formatRating(rating)) },
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                )
            }
            AssistChip(
                onClick = {},
                leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(formatViews(details.views)) },
            )
        }

        if (showGenres && details.genres.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(details.genres.take(if (compact) 6 else 12)) { genre ->
                    AssistChip(onClick = {}, label = { Text(genre) })
                }
            }
        }

        if (episodeSummary.isNotBlank()) {
            Text(
                text = episodeSummary,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        downloadedSummary?.let { summary ->
            Text(
                text = summary,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showMarkPanel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailsHeroActions(
                    watchVideo = watchVideo,
                    resumeTarget = resumeTarget,
                    downloadVideos = downloadVideos,
                    onPlayVideo = onPlayVideo,
                    onPlayVideoAt = onPlayVideoAt,
                    defaultDownloadQuality = defaultDownloadQuality,
                    onResolveDownloadQualities = onResolveDownloadQualities,
                    onDownloadAllVideos = onDownloadAllVideos,
                    onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                    canDownload = canDownload,
                    externalPrimaryFocusRequester = heroActionsFocusRequester,
                )
                AnimeMarkPanelModern(
                    auth = auth,
                    animeMark = animeMark,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    onSelectListMark = onSelectListMark,
                    onToggleFavorite = onToggleFavorite,
                    leftExitRequester = heroActionsFocusRequester,
                    modifier = Modifier.widthIn(max = 392.dp),
                )
            }
        } else {
            DetailsHeroActions(
                watchVideo = watchVideo,
                resumeTarget = resumeTarget,
                downloadVideos = downloadVideos,
                onPlayVideo = onPlayVideo,
                onPlayVideoAt = onPlayVideoAt,
                defaultDownloadQuality = defaultDownloadQuality,
                onResolveDownloadQualities = onResolveDownloadQualities,
                onDownloadAllVideos = onDownloadAllVideos,
                onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                canDownload = canDownload,
                externalPrimaryFocusRequester = heroActionsFocusRequester,
            )
        }

        if (showHeroRating && detailsExtras is LoadState.Ready) {
            DetailsRatingSection(
                rating = detailsExtras.data.rating,
                isAuthorized = auth.profile != null,
                onSetAnimeRating = onSetAnimeRating,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsHeroActions(
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    downloadVideos: List<VideoVariant>,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    defaultDownloadQuality: PreferredQuality,
    onResolveDownloadQualities: suspend (VideoVariant, List<VideoVariant>, Boolean) -> List<PreferredQuality>,
    onDownloadAllVideos: (String?, PreferredQuality) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    canDownload: Boolean,
    externalPrimaryFocusRequester: FocusRequester? = null,
) {
    if (watchVideo == null) return
    var downloadDialogOpen by remember { mutableStateOf(false) }
    val downloadDialogInputActionHandler by rememberUpdatedState { action: InputAction ->
        if (action == InputAction.Back && downloadDialogOpen) {
            downloadDialogOpen = false
            true
        } else {
            false
        }
    }
    DisposableEffect(downloadDialogOpen, onRegisterModalInputActionHandler) {
        if (downloadDialogOpen) {
            onRegisterModalInputActionHandler { action -> downloadDialogInputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
    val internalPrimaryActionFocusRequester = remember(watchVideo.id, resumeTarget?.video?.id) { FocusRequester() }
    val primaryActionFocusRequester = externalPrimaryFocusRequester ?: internalPrimaryActionFocusRequester

    LaunchedEffect(watchVideo.id, resumeTarget?.video?.id) {
        delay(120)
        runCatching { primaryActionFocusRequester.requestFocus() }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (resumeTarget != null) {
            DialogActionButton(
                text = uiText("Продолжить"),
                primary = true,
                modifier = Modifier.focusRequester(primaryActionFocusRequester),
                onClick = { onPlayVideoAt(resumeTarget.video, resumeTarget.positionMs) },
            )
        } else {
            DialogActionButton(
                text = uiText("Смотреть"),
                primary = true,
                modifier = Modifier.focusRequester(primaryActionFocusRequester),
                onClick = { onPlayVideo(watchVideo) },
            )
        }
        if (canDownload && downloadVideos.isNotEmpty()) {
            DialogActionButton(
                text = uiText("Скачать всё"),
                onClick = { downloadDialogOpen = true },
            )
        }
    }

    if (downloadDialogOpen) {
        DownloadSelectionDialog(
            title = uiText("Скачать все серии"),
            videos = downloadVideos,
            selectedVideo = resumeTarget?.video ?: watchVideo,
            selected = defaultDownloadQuality,
            allEpisodes = true,
            onResolveQualities = onResolveDownloadQualities,
            confirmText = uiText("Скачать"),
            onConfirm = { voiceVideo, quality ->
                downloadDialogOpen = false
                onDownloadAllVideos(voiceVideo.groupKey, quality)
            },
            onDismiss = { downloadDialogOpen = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsFactsSection(
    details: AnimeDetails,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onGenreClick: (FilterOption) -> Unit,
    onYearClick: (Int) -> Unit,
    onStudioClick: (FilterOption) -> Unit,
    onCreatorClick: (FilterOption) -> Unit,
) {
    val description = details.description.trim()
    val facts = buildList<Pair<String, String>> {
        add("Тип" to details.type)
        add("Ограничение" to details.minAge)
        add("Статус" to details.status)
        details.year?.let { add("Год выхода" to it.toString()) }
        if (details.otherTitles.isNotEmpty()) add("Альт. названия" to details.otherTitles.take(4).joinToString(" | "))
        details.original.takeIf { it.isPresentFactValue() }?.let { add("Первоисточник" to it) }
        if (details.studios.isNotEmpty()) add("Студия" to details.studios.joinToString { it.title })
        if (details.creators.isNotEmpty()) add("Режиссёр" to details.creators.joinToString { it.title })
        if (details.genreTags.isNotEmpty()) add("Жанры" to details.genreTags.joinToString { it.title })
        details.nextEpisodeText.takeIf { it.isPresentFactValue() }?.let { add("До выхода" to it) }
        details.durationSeconds.takeIf { it > 0 }?.let { seconds ->
            formatDuration(seconds)?.let { add("Длительность" to it) }
        }
        details.commentsCount.takeIf { it > 0L }?.let { add("Комментарии" to formatViews(it)) }
        details.listsCount.takeIf { it > 0L }?.let { add("В списках" to formatViews(it)) }
    }.filter { (_, value) -> value.isPresentFactValue() }

    if (facts.isEmpty() && description.isBlank()) return
    val shape = RoundedCornerShape(8.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.46f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRing(shape)
                    .dpadClickable(shape) { onExpandedChange(!expanded) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = uiText("Описание"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    facts.forEach { (label, value) ->
                        DetailsFactRow(label = uiText(label)) {
                            when (label) {
                                "Год выхода" -> details.year?.let { year ->
                                    ClickableFactText(text = value, onClick = { onYearClick(year) })
                                }
                                "Студия" -> FactChips(options = details.studios, onClick = onStudioClick)
                                "Режиссёр" -> FactChips(options = details.creators, onClick = onCreatorClick)
                                "Жанры" -> FactChips(options = details.genreTags, onClick = onGenreClick)
                                else -> Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = if (facts.isEmpty()) 0.dp else 4.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailsRatingStrip(
    ratingDetails: RatingDetails,
    leftExitRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val entries = buildList {
        ratingDetails.myAnimeList?.let { add("MAL" to formatRating(it)) }
        ratingDetails.shikimori?.let { add("Шики" to formatRating(it)) }
        ratingDetails.kinopoisk?.let { add("КП" to formatRating(it)) }
        ratingDetails.worldArt?.let { add("WA" to formatRating(it)) }
        ratingDetails.aniDub?.let { add("AniDUB" to formatRating(it)) }
    }
    if (entries.isEmpty()) return

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        lazyItemsIndexed(entries, key = { index, entry -> "rating-strip:$index:${entry.first}" }) { index, entry ->
            val (label, value) = entry
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = "$label $value",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                modifier = Modifier.stopHorizontalFocusEscape(index, entries.size, leftExit = leftExitRequester),
            )
        }
    }
}

@Composable
internal fun DetailsFactRow(
    label: String,
    content: @Composable () -> Unit,
) {
    val labelWidth = if (LocalConfiguration.current.screenWidthDp < 430) 116.dp else 156.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth),
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

internal fun String.isPresentFactValue(): Boolean {
    val normalized = trim()
    return normalized.isNotBlank() &&
        !normalized.equals("unknown", ignoreCase = true) &&
        !normalized.equals("null", ignoreCase = true) &&
        normalized != "-" &&
        normalized != "—"
}

@Composable
internal fun ClickableFactText(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.dpadClickable(RoundedCornerShape(6.dp), onClick),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FactChips(
    options: List<FilterOption>,
    onClick: (FilterOption) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val shape = RoundedCornerShape(8.dp)
            Surface(
                modifier = Modifier
                    .widthIn(max = 236.dp)
                    .focusRing(shape)
                    .dpadClickable(shape) { onClick(option) },
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f)),
                shape = shape,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
