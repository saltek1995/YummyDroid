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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsRelatedAnimeSection(
    relatedAnime: List<RelatedAnime>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    if (relatedAnime.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YummySpacing.xl, vertical = YummySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        val shape = YummyRadii.smallShape
        AccordionHeader(
            title = uiText("Порядок просмотра"),
            expanded = expanded,
            active = false,
            onClick = { onExpandedChange(!expanded) },
        )

        if (expanded) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = yummySurfaceColor(YummySurfaceRole.Panel),
                contentColor = yummySurfaceContentColor(YummySurfaceRole.Panel),
                border = yummySurfaceBorder(YummySurfaceRole.Panel),
                shape = shape,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = YummySpacing.lg, vertical = YummySpacing.md),
                    verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
                ) {
                    relatedAnime.forEachIndexed { index, related ->
                        RelatedAnimeOrderRow(
                            index = index + 1,
                            relatedAnime = related,
                            onClick = { onOpenAnime(related.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RelatedAnimeOrderRow(
    index: Int,
    relatedAnime: RelatedAnime,
    onClick: () -> Unit,
) {
    val isCompact = LocalConfiguration.current.screenWidthDp < 680
    val titleColor = if (relatedAnime.isCurrent) {
        YummyColors.offline
    } else {
        MaterialTheme.colorScheme.primary
    }
    val meta = listOfNotNull(
        relatedAnime.type.takeIf { it.isNotBlank() },
        relatedAnime.relation.takeIf { it.isNotBlank() },
        relatedAnime.year?.toString(),
    ).joinToString(", ")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(YummyRadii.smallShape, onClick),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = YummyRadii.smallShape,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            Text(
                text = "$index.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(34.dp),
            )
            if (isCompact) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
                ) {
                    Text(
                        text = relatedAnime.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = titleColor,
                    )
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = relatedAnime.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    modifier = Modifier.weight(1.3f),
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            relatedAnime.rating?.let { rating ->
                Surface(
                    color = YummyColors.offline,
                    contentColor = Color.White,
                    shape = YummyRadii.pillShape,
                ) {
                    Text(
                        text = formatRating(rating),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = YummySpacing.sm, vertical = YummySpacing.xs),
                    )
                }
            }
        }
    }
}

@Composable
internal fun DetailsExtrasTopSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    auth: AuthUiState,
    videos: List<VideoVariant>,
    onSetAnimeRating: (Int?) -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
) {
    when (extrasState) {
        LoadState.Loading -> {
            Unit
        }
        is LoadState.Error -> Unit
        is LoadState.Ready -> {
            DetailsTrailersSection(trailers = extrasState.data.trailers)
        }
    }
}

@Composable
internal fun DetailsCompactRatingSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    auth: AuthUiState,
    showRating: Boolean,
    onSetAnimeRating: (Int?) -> Unit,
) {
    if (!showRating) return
    when (extrasState) {
        LoadState.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        is LoadState.Error -> Unit
        is LoadState.Ready -> {
            val extras = extrasState.data
            CompactRatingScale(
                rating = extras.rating,
                isAuthorized = auth.profile != null,
                onSetAnimeRating = onSetAnimeRating,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 4.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun DetailsSubscriptionsHostSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    auth: AuthUiState,
    videos: List<VideoVariant>,
    allowSubscriptions: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
) {
    if (!allowSubscriptions) return
    when (extrasState) {
        LoadState.Loading -> Unit
        is LoadState.Error -> Unit
        is LoadState.Ready -> {
            DetailsSubscriptionsSection(
                auth = auth,
                videos = videos,
                subscriptions = extrasState.data.subscriptions,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                onToggleVideoSubscription = onToggleVideoSubscription,
            )
        }
    }
}

@Composable
internal fun DetailsRecommendationsSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    onOpenAnime: (Long) -> Unit,
) {
    if (extrasState !is LoadState.Ready) return
    DetailsAnimeRowSection(
        title = uiText("Похожие"),
        animes = extrasState.data.recommendations,
        onOpenAnime = onOpenAnime,
    )
}

@Composable
internal fun DetailsCommentsHostSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    totalComments: Long,
    isAuthorized: Boolean,
    scrollState: ScrollState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
) {
    when (extrasState) {
        LoadState.Loading -> Unit
        is LoadState.Error -> Unit
        is LoadState.Ready -> {
            DetailsCommentsSection(
                comments = extrasState.data.comments,
                totalComments = totalComments,
                commentsPaging = extrasState.data.commentsPaging,
                isAuthorized = isAuthorized,
                scrollState = scrollState,
                expanded = expanded,
                onExpandedChange = onExpandedChange,
                onAddAnimeComment = onAddAnimeComment,
                onLoadMoreAnimeComments = onLoadMoreAnimeComments,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsRatingSection(
    rating: AnimeRatingSummary,
    isAuthorized: Boolean,
    onSetAnimeRating: (Int?) -> Unit,
) {
    if (rating.votes <= 0L && !isAuthorized) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = uiText("Оценка"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = listOfNotNull(
                rating.average?.let { formatRating(it) },
                rating.votes.takeIf { it > 0L }?.let { "${formatViews(it)} ${localizedVotesWord(it)}" },
            ).joinToString(" • ").ifBlank { uiText("Пока нет оценок") },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (isAuthorized) {
            RatingScale(
                selected = rating.userRating,
                onSelected = onSetAnimeRating,
            )
        }
    }
}

@Composable
internal fun RatingScale(
    selected: Int?,
    onSelected: (Int) -> Unit,
    leftExitRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.58f)),
        shape = shape,
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            (1..10).forEach { value ->
                val active = selected != null && value <= selected
                val itemShape = when (value) {
                    1 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                    10 -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .stopHorizontalFocusEscape(value - 1, 10, leftExit = leftExitRequester)
                        .background(
                            color = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.90f) else Color.Transparent,
                            shape = itemShape,
                        )
                        .dpadClickable(itemShape) { onSelected(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "${uiText("Оценка")} $value",
                        modifier = Modifier.size(19.dp),
                        tint = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (value < 10) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsSubscriptionsSection(
    auth: AuthUiState,
    videos: List<VideoVariant>,
    subscriptions: List<VideoSubscription>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
) {
    if (auth.profile == null || videos.isEmpty()) return
    val groups = videos
        .filter { it.matchingDubbingKey.isNotBlank() }
        .groupBy { it.matchingDubbingKey }
        .values
        .mapNotNull { group -> group.minByOrNull { it.player } }
        .sortedBy { it.matchingDubbingTitle }
        .take(18)
    if (groups.isEmpty()) return
    val activeCount = groups.count { subscriptions.isVideoVoiceSubscribed(it) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccordionHeader(
            title = uiText("Подписка"),
            expanded = expanded,
            active = activeCount > 0,
            onClick = { onExpandedChange(!expanded) },
            trailingText = activeCount.takeIf { it > 0 }?.toString(),
        )

        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                groups.forEachIndexed { index, video ->
                    val subscribed = subscriptions.isVideoVoiceSubscribed(video)
                    val itemShape = RoundedCornerShape(8.dp)
                    Surface(
                        modifier = Modifier
                            .stopHorizontalFocusEscape(index, groups.size)
                            .focusRing(itemShape)
                            .dpadClickable(itemShape) { onToggleVideoSubscription(video) },
                        color = if (subscribed) {
                            yummySurfaceColor(YummySurfaceRole.ActiveRow)
                        } else {
                            Color.Transparent
                        },
                        contentColor = if (subscribed) {
                            yummySurfaceContentColor(YummySurfaceRole.ActiveRow)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        border = yummySurfaceBorder(if (subscribed) YummySurfaceRole.ActiveRow else YummySurfaceRole.Row),
                        shape = itemShape,
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = video.matchingDubbingTitle,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DetailsTrailersSection(trailers: List<AnimeTrailer>) {
    if (trailers.isEmpty()) return
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = uiText("Трейлеры"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            lazyItemsIndexed(
                trailers,
                key = { index, trailer -> "trailer:$index:${trailer.id}:${trailer.url}" },
            ) { index, trailer ->
                AssistChip(
                    onClick = { context.openUrl(trailer.url) },
                    label = {
                        Text(
                            text = trailer.title.ifBlank { trailer.player.ifBlank { uiText("Трейлер") } },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    modifier = Modifier
                        .stopHorizontalFocusEscape(index, trailers.size)
                        .focusRing(RoundedCornerShape(8.dp)),
                )
            }
        }
    }
}

@Composable
internal fun DetailsAnimeRowSection(
    title: String,
    animes: List<Anime>,
    onOpenAnime: (Long) -> Unit,
) {
    if (animes.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            lazyItemsIndexed(
                animes,
                key = { index, anime -> "details-anime-row:$title:$index:${anime.id}:${anime.title}" },
            ) { index, anime ->
                AnimeCard(
                    anime = anime,
                    onClick = { onOpenAnime(anime.id) },
                    modifier = Modifier
                        .width(172.dp)
                        .stopHorizontalFocusEscape(index, animes.size),
                )
            }
        }
    }
}

@Composable
internal fun DetailsCommentsSection(
    comments: List<AnimeComment>,
    totalComments: Long,
    commentsPaging: PagingUiState,
    isAuthorized: Boolean,
    scrollState: ScrollState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
) {
    if (comments.isEmpty() && !isAuthorized) return
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(
        expanded,
        comments.size,
        commentsPaging.canLoadMore,
        commentsPaging.isLoadingMore,
    ) {
        if (!expanded) return@LaunchedEffect
        snapshotFlow { scrollState.value to scrollState.maxValue }
            .collectLatest { (current, max) ->
                val nearBottom = max - current < 720
                if (nearBottom && commentsPaging.canLoadMore && !commentsPaging.isLoadingMore) {
                    onLoadMoreAnimeComments()
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val commentsProgressText = if (comments.isNotEmpty()) {
            if (totalComments > 0L) {
                "${comments.size} ${uiText("из")} ${formatViews(totalComments)} ${uiText("загружено")}"
            } else {
                "${comments.size} ${uiText("загружено")}"
            }
        } else {
            null
        }
        AccordionHeader(
            title = uiText("Комментарии"),
            expanded = expanded,
            active = false,
            onClick = { onExpandedChange(!expanded) },
            trailingText = commentsProgressText,
        )

        if (expanded) {
            if (isAuthorized) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(uiText("Комментарий")) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(1.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DialogActionButton(
                        text = uiText("Отправить"),
                        primary = true,
                        onClick = {
                            val text = draft.trim()
                            if (text.isNotBlank()) {
                                onAddAnimeComment(text)
                                draft = ""
                            }
                        },
                    )
                }
            }

            comments.forEach { comment ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val commentDate = remember(comment.createdAtSeconds) {
                            formatCommentTimestamp(comment.createdAtSeconds)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = comment.userName.ifBlank { uiText("Пользователь") },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (commentDate.isNotBlank()) {
                                Text(
                                    text = commentDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.End,
                                )
                            }
                        }
                        Text(
                            text = comment.text,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            when {
                commentsPaging.isLoadingMore -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                commentsPaging.error != null -> Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = commentsPaging.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    DialogActionButton(
                        text = uiText("Повторить"),
                        primary = true,
                        onClick = onLoadMoreAnimeComments,
                    )
                }
            }
        }
    }
}

internal fun UserAnimeListMark.icon() = when (this) {
    UserAnimeListMark.Watching -> Icons.Default.RemoveRedEye
    UserAnimeListMark.Planned -> Icons.Default.Cloud
    UserAnimeListMark.Watched -> Icons.Default.Flag
    UserAnimeListMark.Postponed -> Icons.Default.Schedule
    UserAnimeListMark.Dropped -> Icons.Default.VisibilityOff
}

internal fun UserAnimeListMark.siteColor() = when (this) {
    UserAnimeListMark.Watching -> Color(0xFFFF5E66)
    UserAnimeListMark.Planned -> Color(0xFFB66DFF)
    UserAnimeListMark.Watched -> Color(0xFF35D47A)
    UserAnimeListMark.Postponed -> Color(0xFFFFB71B)
    UserAnimeListMark.Dropped -> Color(0xFF9EA3AA)
}

internal val favoriteMarkColor = Color(0xFFC94DDB)
