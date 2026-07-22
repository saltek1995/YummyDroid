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
internal fun DownloadsSection(
    state: YummyDroidUiState,
    focusCurrentRequestNonce: Long,
    onClearHistory: () -> Unit,
    onCancelDownload: (Long) -> Unit,
    onPauseDownload: (Long) -> Unit,
    onResumeDownload: (Long) -> Unit,
    onOpenAnime: (Long) -> Unit,
) {
    val offlineEntries = state.offlineEntries.readyListOrEmpty()
    val tasks = state.downloadQueue.tasks

    if (tasks.isEmpty() && offlineEntries.isEmpty()) {
        EmptyPane(
            message = uiText("Скачанных серий пока нет"),
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val downloadFocusRequester = remember { FocusRequester() }
    val downloadsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var focusedDownloadKey by rememberSaveable { mutableStateOf<String?>(null) }
    val downloadFocusKeys = remember(tasks, offlineEntries) {
        tasks.map { task -> "task:${task.id}" } +
            offlineEntries.map { entry -> "offline:${entry.anime.id}" }
    }
    val downloadFocusListIndexes = remember(tasks, offlineEntries) {
        val indexes = mutableMapOf<String, Int>()
        var listIndex = 0
        if (tasks.isNotEmpty()) {
            listIndex += 1
            tasks.forEach { task ->
                indexes["task:${task.id}"] = listIndex
                listIndex += 1
            }
        }
        if (offlineEntries.isNotEmpty()) {
            listIndex += 1
            offlineEntries.forEach { entry ->
                indexes["offline:${entry.anime.id}"] = listIndex
                listIndex += 1
            }
        }
        indexes
    }
    val activeDownloadFocusKey = focusedDownloadKey
        ?.takeIf { key -> key in downloadFocusKeys }
        ?: downloadFocusKeys.firstOrNull()

    LaunchedEffect(focusCurrentRequestNonce, activeDownloadFocusKey) {
        if (focusCurrentRequestNonce <= 0L || activeDownloadFocusKey == null) {
            return@LaunchedEffect
        }
        val targetListIndex = downloadFocusListIndexes[activeDownloadFocusKey]
        val targetIsVisible = targetListIndex == null ||
            downloadsListState.layoutInfo.visibleItemsInfo.any { item -> item.index == targetListIndex }
        if (targetListIndex != null && !targetIsVisible) {
            downloadsListState.scrollToItem(targetListIndex, 0)
        }
        withFrameNanos { }
        runCatching { downloadFocusRequester.requestFocus() }
    }

    LazyColumn(
        state = downloadsListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (tasks.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = uiText("Очередь загрузок"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.weight(1f),
                    )
                    if (tasks.any { !it.isActive && it.state != DownloadTaskState.Paused }) {
                        DialogActionButton(
                            text = uiText("Очистить"),
                            onClick = onClearHistory,
                        )
                    }
                }
            }
            items(tasks, key = { it.id }) { task ->
                val focusKey = "task:${task.id}"
                DownloadTaskCard(
                    task = task,
                    onOpenAnime = { onOpenAnime(task.animeId) },
                    onCancelDownload = { onCancelDownload(task.id) },
                    onPauseDownload = { onPauseDownload(task.id) },
                    onResumeDownload = { onResumeDownload(task.id) },
                    modifier = Modifier
                        .then(
                            if (focusKey == activeDownloadFocusKey) {
                                Modifier.focusRequester(downloadFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                focusedDownloadKey = focusKey
                            }
                        },
                )
            }
        }

        if (offlineEntries.isNotEmpty()) {
            item {
                Text(
                    text = uiText("Доступно офлайн"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(top = if (tasks.isEmpty()) 0.dp else 12.dp),
                )
            }
            lazyItemsIndexed(
                offlineEntries,
                key = { index, entry -> "offline-entry:$index:${entry.anime.id}:${entry.anime.title}" },
            ) { _, entry ->
                val focusKey = "offline:${entry.anime.id}"
                OfflineAnimeRow(
                    entry = entry,
                    onOpenAnime = onOpenAnime,
                    modifier = Modifier
                        .then(
                            if (focusKey == activeDownloadFocusKey) {
                                Modifier.focusRequester(downloadFocusRequester)
                            } else {
                                Modifier
                            },
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.hasFocus) {
                                focusedDownloadKey = focusKey
                            }
                        },
                )
            }
        }
    }
}

@Composable
internal fun DownloadTaskCard(
    task: me.yummydroid.app.DownloadTaskUi,
    onOpenAnime: () -> Unit,
    onCancelDownload: () -> Unit,
    onPauseDownload: () -> Unit,
    onResumeDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = YummyRadii.smallShape
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(shape, onOpenAnime),
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOf(task.episodeTitle, task.qualityTitle).joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = task.state.localizedTitle(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (task.state == DownloadTaskState.Failed) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    fontWeight = FontWeight.Bold,
                )
                if (task.state == DownloadTaskState.Running || task.state == DownloadTaskState.Queued) {
                    IconButton(
                        onClick = onPauseDownload,
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.Pause, contentDescription = uiText("Поставить на паузу"))
                    }
                }
                if (task.canResume) {
                    IconButton(
                        onClick = onResumeDownload,
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = uiText("Возобновить загрузку"))
                    }
                }
                if (task.isActive || task.state == DownloadTaskState.Paused || task.state == DownloadTaskState.Failed) {
                    IconButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.focusRing(RoundedCornerShape(8.dp)),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = uiText("Отменить загрузку"))
                    }
                }
            }

            if (task.isActive || task.state == DownloadTaskState.Completed) {
                LinearProgressIndicator(
                    progress = { task.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (task.message.isNotBlank()) {
                Text(
                    text = task.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val transferText = task.transferStatusText()
            if (transferText.isNotBlank()) {
                Text(
                    text = transferText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun me.yummydroid.app.DownloadTaskUi.transferStatusText(): String {
    if (!isActive && state != DownloadTaskState.Completed && state != DownloadTaskState.Paused && state != DownloadTaskState.Failed) return ""
    val percent = "${(progress.coerceIn(0f, 1f) * 100f).roundToInt()}%"
    val size = when {
        totalBytes > 0L && downloadedBytes > 0L -> "${formatByteSize(downloadedBytes)} / ${formatByteSize(totalBytes)}"
        downloadedBytes > 0L && isActive -> "${formatByteSize(downloadedBytes)} / ${uiText("неизвестно")}"
        downloadedBytes > 0L -> formatByteSize(downloadedBytes)
        else -> ""
    }
    val speed = if (isActive && bytesPerSecond > 0L) "${formatByteSize(bytesPerSecond)}/${uiText("с")}" else ""
    return listOf(percent, size, speed)
        .filter { it.isNotBlank() }
        .joinToString(" • ")
}

@Composable
internal fun OfflineAnimeRow(
    entry: OfflineAnimeEntry,
    onOpenAnime: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = YummyRadii.smallShape
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(shape) { onOpenAnime(entry.anime.id) },
        color = yummySurfaceColor(YummySurfaceRole.Row),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Row),
        border = yummySurfaceBorder(YummySurfaceRole.Row),
        shape = shape,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PosterImage(
                url = entry.anime.posterUrl,
                contentDescription = entry.anime.title,
                modifier = Modifier
                    .width(58.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.anime.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${entry.downloadedVideos.size} ${localizedEpisodesWord(entry.downloadedVideos.size)} • ${formatByteSize(entry.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun DownloadTaskState.localizedTitle(): String = when (this) {
    DownloadTaskState.Queued -> uiText("В очереди")
    DownloadTaskState.Running -> uiText("Загрузка")
    DownloadTaskState.Paused -> uiText("Пауза")
    DownloadTaskState.Added -> uiText("Добавлено")
    DownloadTaskState.Completed -> uiText("Скачано")
    DownloadTaskState.Failed -> uiText("Ошибка")
    DownloadTaskState.Cancelled -> uiText("Отменено")
}
