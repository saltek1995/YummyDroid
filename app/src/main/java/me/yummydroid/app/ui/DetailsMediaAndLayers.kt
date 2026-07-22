package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable
import me.yummydroid.app.AppRoute
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.downloadedEpisodeCountForVoice
import me.yummydroid.app.data.episodeOrderValue
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.matchingVoiceTitle
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.qualityHeight
import me.yummydroid.app.data.sourceProviderRank
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatWatchedAtTimestamp
import me.yummydroid.app.InputAction
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.YummyDroidUiState

@Composable
internal fun DetailsScreenshotsSection(
    screenshots: List<String>,
    onRegisterInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    if (screenshots.isEmpty()) return
    val visibleScreenshots = remember(screenshots) { screenshots.take(24) }
    var selectedIndex by remember(visibleScreenshots) { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = uiText("Кадры"),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            lazyItemsIndexed(
                visibleScreenshots,
                key = { index, screenshot -> "screenshot:$index:$screenshot" },
            ) { index, screenshot ->
                val shape = RoundedCornerShape(8.dp)
                PosterImage(
                    url = screenshot,
                    contentDescription = null,
                    modifier = Modifier
                        .width(320.dp)
                        .aspectRatio(16f / 9f)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .stopHorizontalFocusEscape(index, visibleScreenshots.size)
                        .dpadClickable(shape) { selectedIndex = index },
                )
            }
        }
    }

    selectedIndex?.let { index ->
        ScreenshotViewerDialog(
            screenshots = visibleScreenshots,
            initialIndex = index,
            onDismiss = { selectedIndex = null },
            onRegisterInputActionHandler = onRegisterInputActionHandler,
        )
    }
}

@Composable
internal fun ScreenshotViewerDialog(
    screenshots: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onRegisterInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
) {
    if (screenshots.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, screenshots.lastIndex),
        pageCount = { screenshots.size },
    )
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    var verticalDrag by remember { mutableFloatStateOf(0f) }

    fun closeViewer() {
        if (!isClosing) {
            isClosing = true
            onDismiss()
        }
    }

    fun showPrevious() {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    fun showNext() {
        if (pagerState.currentPage < screenshots.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
        }
    }

    val inputActionHandler by rememberUpdatedState { action: InputAction ->
        when (action) {
            InputAction.Back,
            InputAction.Up,
            InputAction.Down -> {
                closeViewer()
                true
            }
            InputAction.Left -> {
                showPrevious()
                true
            }
            InputAction.Right -> {
                showNext()
                true
            }
            InputAction.Confirm,
            InputAction.Play,
            InputAction.Pause,
            InputAction.PlayPause,
            InputAction.PreviousEpisode,
            InputAction.NextEpisode -> false
        }
    }

    DisposableEffect(onRegisterInputActionHandler) {
        onRegisterInputActionHandler { action -> inputActionHandler(action) }
        onDispose { onRegisterInputActionHandler(null) }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = ::closeViewer,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .navigationBarsPadding()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (abs(verticalDrag) > 120f) {
                                closeViewer()
                            }
                            verticalDrag = 0f
                        },
                        onDragCancel = { verticalDrag = 0f },
                    ) { _, dragAmount ->
                        verticalDrag += dragAmount
                    }
                }
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            showPrevious()
                            true
                        }
                        Key.DirectionRight -> {
                            showNext()
                            true
                        }
                        Key.DirectionUp,
                        Key.DirectionDown -> {
                            closeViewer()
                            true
                        }
                        Key.Escape,
                        Key.NavigateOut -> {
                            closeViewer()
                            true
                        }
                        else -> false
                    }
                },
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { index ->
                val zoomableState = rememberZoomableState()
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(screenshots[index])
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .zoomable(zoomableState),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.56f), RoundedCornerShape(50))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${screenshots.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

internal data class HeroResumeTarget(
    val video: VideoVariant,
    val positionMs: Long,
)

internal data class FocusFirstRequest(
    val persistentNonce: Long = 0L,
)

internal data class AppScreenLayer(
    val key: AppScreenKey,
    val state: YummyDroidUiState,
)

internal sealed interface AppScreenKey {
    data object Home : AppScreenKey
    data class Details(val animeId: Long) : AppScreenKey
    data object Player : AppScreenKey
}

internal const val APP_LAYER_STACK_LIMIT = 40

internal fun List<AppScreenLayer>.syncedWith(state: YummyDroidUiState): List<AppScreenLayer> {
    return when (val route = state.route) {
        AppRoute.Home -> {
            val updatedLayer = AppScreenLayer(
                key = AppScreenKey.Home,
                state = state,
            )
            val existingIndex = indexOfLast { it.key == AppScreenKey.Home }
            if (existingIndex >= 0) {
                take(existingIndex) + updatedLayer
            } else {
                listOf(updatedLayer)
            }
        }
        is AppRoute.Details -> {
            val key = AppScreenKey.Details(route.animeId)
            val updatedLayer = AppScreenLayer(
                key = key,
                state = state,
            )
            val baseLayers = if (any { it.key == AppScreenKey.Home }) {
                this
            } else {
                listOf(
                    AppScreenLayer(
                        key = AppScreenKey.Home,
                        state = state.copy(route = AppRoute.Home),
                    ),
                ) + this
            }
            val existingIndex = baseLayers.indexOfLast { it.key == key }
            val updatedLayers = if (existingIndex >= 0) {
                baseLayers.take(existingIndex) + updatedLayer
            } else {
                baseLayers + updatedLayer
            }
            updatedLayers.trimAppScreenLayers()
        }
        is AppRoute.Player -> {
            val updatedLayer = AppScreenLayer(
                key = AppScreenKey.Player,
                state = state,
            )
            val existingIndex = indexOfLast { it.key == AppScreenKey.Player }
            val updatedLayers = if (existingIndex >= 0) {
                take(existingIndex) + updatedLayer
            } else {
                this + updatedLayer
            }
            updatedLayers.trimAppScreenLayers()
        }
    }
}

internal fun List<AppScreenLayer>.trimAppScreenLayers(): List<AppScreenLayer> {
    if (size <= APP_LAYER_STACK_LIMIT) return this
    val homeLayer = firstOrNull { it.key == AppScreenKey.Home }
    val tailLimit = APP_LAYER_STACK_LIMIT - if (homeLayer != null) 1 else 0
    val tail = filterNot { it.key == AppScreenKey.Home }.takeLast(tailLimit.coerceAtLeast(0))
    return if (homeLayer != null) listOf(homeLayer) + tail else tail
}

@Composable
internal fun List<VideoVariant>.downloadedEpisodeSummary(): String? {
    val allEpisodes = distinctBy { it.matchingEpisodeKey }
    val downloaded = filter { it.isOfflineAvailable }
        .distinctBy { it.matchingEpisodeKey }
        .sortedWith(compareBy<VideoVariant> { it.episodeOrderValue() ?: Double.MAX_VALUE }.thenBy { it.index })
    if (downloaded.isEmpty()) return null

    return if (allEpisodes.isNotEmpty() && downloaded.size >= allEpisodes.size) {
        "${uiText("Загружено")} ${downloaded.size}"
    } else {
        val episodeWord = uiText("серия")
        val labels = downloaded.joinToString(", ") { it.shortEpisodeLabel(episodeWord) }
        "${uiText("Загружено")}: $labels"
    }
}

@Composable
internal fun AnimeDetails.effectiveEpisodeSummary(videos: List<VideoVariant>): String {
    val actualEpisodes = remember(videos) {
        val bySlot = videos.map { it.matchingEpisodeKey }
            .filter { it.isNotBlank() }
            .distinct()
            .size
        if (bySlot > 0) {
            bySlot
        } else {
            videos.distinctBy { variant ->
                variant.episode.takeIf { it.isNotBlank() } ?: variant.index.toString()
            }.size
        }
    }
    val aired = if (episodeCount > 0) {
        maxOf(episodeAired, actualEpisodes).coerceAtMost(episodeCount)
    } else {
        maxOf(episodeAired, actualEpisodes)
    }
    return when {
        aired > 0 && episodeCount > 0 -> "${uiText("Вышло")} $aired ${uiText("из")} $episodeCount"
        aired > 0 -> "${uiText("Вышло")} $aired"
        episodeSummary.isNotBlank() -> episodeSummary
        episodeCount > 0 -> "$episodeCount ${localizedEpisodesWord(episodeCount)}"
        else -> ""
    }
}

internal fun VideoVariant.shortEpisodeLabel(episodeWord: String): String {
    return episode.takeIf { it.isNotBlank() }?.let { "$episodeWord $it" } ?: episodeTitle.lowercase(Locale.ROOT)
}

internal fun VideoVariant.localizedEpisodeTitle(episodeWord: String, fallback: String): String {
    return episode.takeIf { it.isNotBlank() }?.let { "$episodeWord $it" } ?: fallback
}

@Composable
internal fun VideoVariant.localizedEpisodeTitle(): String {
    return localizedEpisodeTitle(
        episodeWord = uiText("Серия"),
        fallback = uiText("Эпизод"),
    )
}

internal fun List<VideoVariant>.downloadVoiceOptions(selectedVideo: VideoVariant?): List<VideoVariant> {
    return groupBy { it.matchingVoiceKey }
        .values
        .mapNotNull { group ->
            group.minWithOrNull(
                compareBy<VideoVariant> { if (selectedVideo != null && it.groupKey == selectedVideo.groupKey) 0 else 1 }
                    .thenBy { sourceProviderRank(it.player) }
                    .thenByDescending { it.isOfflineAvailable }
                    .thenBy { it.index }
                    .thenBy { it.id },
            )
        }
        .sortedWith(
            compareBy<VideoVariant> {
                if (selectedVideo != null && it.matchingVoiceKey == selectedVideo.matchingVoiceKey) 0 else 1
            }.thenBy { it.matchingVoiceTitle },
        )
}

internal fun List<VideoVariant>.downloadEpisodeCandidates(video: VideoVariant): List<VideoVariant> {
    return filter { it.isSameEpisodeAs(video) }.ifEmpty { listOf(video) }
}

@Composable
internal fun VideoVariant.downloadVoiceSubtitle(videos: List<VideoVariant>): String {
    val count = videos
        .asSequence()
        .filter { it.matchingVoiceKey == matchingVoiceKey }
        .map { it.matchingEpisodeKey }
        .distinct()
        .count()
        .coerceAtLeast(1)
    return "$count ${localizedEpisodesWord(count)}"
}

internal fun VideoVariant.downloadedVoiceEpisodeCount(videos: List<VideoVariant>): Int {
    return downloadedEpisodeCountForVoice(videos)
}

internal fun VideoVariant.downloadedQualityEpisodeCount(
    videos: List<VideoVariant>,
    quality: PreferredQuality,
): Int {
    val targetHeight = quality.height
    return videos
        .asSequence()
        .filter { it.matchingVoiceKey == matchingVoiceKey }
        .filter { candidate ->
            candidate.offlineFiles.any { file ->
                targetHeight == null || file.qualityHeight() == targetHeight
            }
        }
        .map { it.matchingEpisodeKey }
        .distinct()
        .count()
}

internal fun List<VideoVariant>.heroStartVideo(selectedGroup: String?): VideoVariant? {
    if (isEmpty()) return null
    val preferredGroup = selectedGroup?.takeIf { groupKey -> any { it.groupKey == groupKey } }
    return sortedForPlayer(preferredGroup).firstOrNull()
        ?: sortedForPlayer().firstOrNull()
}

internal fun PlaybackProgress?.resolveResumeTarget(videos: List<VideoVariant>): HeroResumeTarget? {
    val progress = this ?: return null
    if (progress.positionMs <= 0L || videos.isEmpty()) return null
    val video = videos.firstOrNull { candidate -> candidate.matchesPlaybackProgress(progress, requireGroup = true) }
        ?: videos.firstOrNull { candidate -> candidate.matchesPlaybackProgress(progress, requireGroup = false) }
        ?: return null

    val durationMs = progress.durationMs.takeIf { it > 0L }
    val safePosition = if (durationMs != null) {
        progress.positionMs.coerceIn(0L, (durationMs - 5_000L).coerceAtLeast(0L))
    } else {
        progress.positionMs.coerceAtLeast(0L)
    }
    if (safePosition <= 0L) return null
    return HeroResumeTarget(video, safePosition)
}

internal fun List<PlaybackProgress>.progressFor(video: VideoVariant): PlaybackProgress? {
    return firstOrNull { progress -> video.matchesPlaybackProgress(progress, requireGroup = true) }
        ?: firstOrNull { progress -> video.matchesPlaybackProgress(progress, requireGroup = false) }
}

internal fun VideoVariant.matchesPlaybackProgress(
    progress: PlaybackProgress,
    requireGroup: Boolean,
): Boolean {
    if (progress.videoId > 0L && id == progress.videoId) return true
    if (requireGroup && (progress.groupKey.isBlank() || groupKey != progress.groupKey)) return false
    if (progress.episode.isBlank()) return false
    return episode.matchesProgressEpisode(progress.episode) ||
        matchingEpisodeKey == progress.episode ||
        matchingEpisodeKey.matchesProgressEpisode(progress.episode)
}

internal fun PlaybackProgress.watchedAtText(): String? {
    return formatWatchedAtTimestamp(updatedAtMs)
}

internal fun String.matchesProgressEpisode(progressEpisode: String): Boolean {
    val current = trim()
    val saved = progressEpisode.trim()
    if (current == saved) return true
    val currentNumber = current.replace(',', '.').toDoubleOrNull()
    val savedNumber = saved.replace(',', '.').toDoubleOrNull()
    return currentNumber != null && savedNumber != null && currentNumber == savedNumber
}
