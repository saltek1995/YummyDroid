package me.yummydroid.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.YummyDroidUiState
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.data.isSameEpisodeAs
import me.yummydroid.app.data.matchingEpisodeKey
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.normalizedVoiceKey
import me.yummydroid.app.data.siteDefaultVideo
import me.yummydroid.app.ui.theme.yummyAppBackground

// DetailsContentRuntime
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DetailsContentRuntime(
    model: DetailsContentModel,
    actions: DetailsContentActions,
) {
    val presentation = rememberDetailsContentPresentation(model)
    val focusGridState = rememberVisualFocusGridState(
        size = presentation.focusLayout.size,
        key = model.details.id,
    )
    val layerFocusState = rememberDetailsLayerFocusState()
    DetailsContentFocusEffects(
        model = model,
        actions = actions,
        presentation = presentation,
        focusGridState = focusGridState,
        layerFocusState = layerFocusState,
    )
    CompositionLocalProvider(LocalBringIntoViewSpec provides DetailsBringIntoViewSpec) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { focusState ->
                    layerFocusState.hasFocus = focusState.isFocused || focusState.hasFocus
                }
                .focusGroup()
                .navigationVerticalScroll(model.screenUiState.scrollState),
        ) {
            DetailsContentSections(model, actions, presentation, focusGridState)
        }
    }
}

// DetailsPlaybackPolicy
internal data class HeroResumeTarget(
    val video: VideoVariant,
    val positionMs: Long,
)

internal fun List<VideoVariant>.heroStartVideo(selectedGroup: String?): VideoVariant? {
    if (isEmpty()) return null
    val preferredGroup = selectedGroup?.takeIf { groupKey -> any { it.groupKey == groupKey } }
        ?: siteDefaultVideo()?.groupKey
    val preferredVoice = matchingVoiceKeyForGroup(preferredGroup)
    return sortedForPlayer(preferredGroup, preferredVoice).firstOrNull()
        ?: siteDefaultVideo()
}

internal fun PlaybackProgress?.resolveResumeTarget(
    videos: List<VideoVariant>,
): HeroResumeTarget? {
    val progress = this ?: return null
    if (progress.positionMs <= 0L || videos.isEmpty()) return null
    val video = videos.firstOrNull { candidate ->
        candidate.matchesPlaybackProgress(progress, requireGroup = true)
    } ?: videos.firstOrNull { candidate ->
        candidate.matchesPlaybackProgress(progress, requireGroup = false)
    } ?: return null

    val safePosition = progress.safeResumePosition()
    if (safePosition <= 0L) return null
    return HeroResumeTarget(video, safePosition)
}

internal fun Iterable<PlaybackProgress>.resolveLatestResumeTarget(
    videos: List<VideoVariant>,
    preferredGroupKey: String? = null,
): HeroResumeTarget? {
    if (videos.isEmpty()) return null
    val target = asSequence()
        .sortedByDescending { progress -> progress.updatedAtMs }
        .mapNotNull { progress -> progress.resolveResumeTarget(videos) }
        .firstOrNull()
        ?: return null
    val preferredVideo = preferredGroupKey
        ?.takeIf { groupKey -> videos.any { it.groupKey == groupKey } }
        ?.let { groupKey ->
            val preferredVoiceKey = videos.matchingVoiceKeyForGroup(groupKey)
            videos.sortedForPlayer(groupKey, preferredVoiceKey)
                .firstOrNull { video -> video.isSameEpisodeAs(target.video) }
        }
    return target.copy(video = preferredVideo ?: target.video)
}

private fun PlaybackProgress.safeResumePosition(): Long {
    val duration = durationMs.takeIf { it > 0L } ?: return positionMs.coerceAtLeast(0L)
    return positionMs.coerceIn(0L, (duration - 5_000L).coerceAtLeast(0L))
}

internal fun List<PlaybackProgress>.progressFor(video: VideoVariant): PlaybackProgress? {
    return firstOrNull { progress -> video.matchesPlaybackProgress(progress, requireGroup = true) }
        ?: firstOrNull { progress -> video.matchesPlaybackProgress(progress, requireGroup = false) }
}

internal fun VideoVariant.matchesPlaybackProgress(
    progress: PlaybackProgress,
    requireGroup: Boolean,
): Boolean {
    if (matchesProgressVideoId(progress)) return true
    if (!matchesProgressSource(progress, requireGroup)) return false
    return matchesProgressEpisode(progress.episode)
}

private fun VideoVariant.matchesProgressVideoId(progress: PlaybackProgress): Boolean {
    return progress.videoId > 0L && id == progress.videoId
}

private fun VideoVariant.matchesProgressSource(
    progress: PlaybackProgress,
    requireGroup: Boolean,
): Boolean {
    return if (requireGroup) {
        progress.groupKey.isNotBlank() && groupKey == progress.groupKey
    } else {
        matchesProgressVoice(progress)
    }
}

private fun VideoVariant.matchesProgressEpisode(progressEpisode: String): Boolean {
    if (progressEpisode.isBlank()) return false
    return episode.matchesProgressEpisode(progressEpisode) ||
        matchingEpisodeKey.matchesProgressEpisode(progressEpisode)
}

private fun VideoVariant.matchesProgressVoice(progress: PlaybackProgress): Boolean {
    val progressVoiceKey = progress.groupKey
        .substringAfter('|', progress.groupKey)
        .normalizedVoiceKey()
    return progressVoiceKey.isBlank() || matchingVoiceKey == progressVoiceKey
}

internal fun String.matchesProgressEpisode(progressEpisode: String): Boolean {
    val current = trim()
    val saved = progressEpisode.trim()
    if (current == saved) return true
    val currentNumber = current.replace(',', '.').toDoubleOrNull()
    val savedNumber = saved.replace(',', '.').toDoubleOrNull()
    return currentNumber != null && savedNumber != null && currentNumber == savedNumber
}

// DetailsScreenRuntime
internal class DetailsScreenUiState {
    val scrollState = ScrollState(0)
    var relatedExpanded by mutableStateOf(false)
    var subscriptionsExpanded by mutableStateOf(false)
    var commentsExpanded by mutableStateOf(false)
    var retainedFocusKey by mutableStateOf<Any?>(null)
    var suppressInitialFocusOnReactivation by mutableStateOf(false)
}

@Composable
internal fun DetailsScreenModern(
    state: YummyDroidUiState,
    screenUiState: DetailsScreenUiState,
    interactive: Boolean,
    activeFocusRequestNonce: Long,
    retainedFocusRequestNonce: Long = 0L,
    onRefresh: () -> Unit,
    onOpenAnime: (Long) -> Unit,
    onOpenLogin: () -> Unit,
    onGenreFilterSelected: (Long, FilterOption) -> Unit,
    onYearFilterSelected: (Long, Int) -> Unit,
    onStudioFilterSelected: (Long, FilterOption) -> Unit,
    onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    onSelectVideoGroup: (String) -> Unit,
    onPlayVideo: (VideoVariant) -> Unit,
    onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
    onPlayVideoAt: (VideoVariant, Long) -> Unit,
    onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    onSetAnimeRating: (Int?) -> Unit,
    onAddAnimeComment: (String) -> Unit,
    onLoadMoreAnimeComments: () -> Unit,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
    onResolveSampledDownloadQualities: suspend (Set<String>, List<VideoVariant>) -> Map<String, List<PreferredQuality>>,
    onDownloadAllVideos: (DownloadPlan) -> Unit,
    onResetAnimeWatchProgress: (Long) -> Unit,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    onRegisterDpadFocusRecoveryHandler: ((() -> Boolean)?) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .yummyAppBackground(),
    ) {
        DetailsStateContent(
            state = state.details,
            onRetry = onRefresh,
            emptyMessage = uiText(UiStringKey.AnimeCardNotFound),
        ) { details ->
            DetailsContentRuntime(
                model = DetailsContentModel(
                    details = details,
                    screenUiState = screenUiState,
                    interactive = interactive,
                    activeFocusRequestNonce = activeFocusRequestNonce,
                    retainedFocusRequestNonce = retainedFocusRequestNonce,
                    settings = state.settings,
                    videos = state.videos,
                    selectedGroup = state.selectedVideoGroup,
                    auth = state.auth,
                    animeMark = state.animeMark,
                    detailsExtras = state.detailsExtras,
                    commentSubmission = state.commentSubmission,
                    forcedOfflineMode = state.forcedOfflineMode,
                    playbackProgress = state.playbackProgress,
                    playbackHistory = state.playbackHistory,
                    playbackHistoryLoading = state.playbackHistoryLoading,
                ),
                actions = DetailsContentActions(
                    onOpenAnime = onOpenAnime,
                    onOpenLogin = onOpenLogin,
                    onGenreFilterSelected = onGenreFilterSelected,
                    onYearFilterSelected = onYearFilterSelected,
                    onStudioFilterSelected = onStudioFilterSelected,
                    onCreatorFilterSelected = onCreatorFilterSelected,
                    onSelectVideoGroup = onSelectVideoGroup,
                    onPlayVideo = onPlayVideo,
                    onPlayVideoWithResumeChoice = onPlayVideoWithResumeChoice,
                    onPlayVideoAt = onPlayVideoAt,
                    onSelectAnimeListMark = onSelectAnimeListMark,
                    onToggleFavorite = onToggleFavorite,
                    onSetAnimeRating = onSetAnimeRating,
                    onAddAnimeComment = onAddAnimeComment,
                    onLoadMoreAnimeComments = onLoadMoreAnimeComments,
                    onToggleVideoSubscription = onToggleVideoSubscription,
                    onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
                    onDownloadAllVideos = onDownloadAllVideos,
                    onResetAnimeWatchProgress = onResetAnimeWatchProgress,
                    onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
                    onRegisterDpadFocusRecoveryHandler = onRegisterDpadFocusRecoveryHandler,
                    onRetry = onRefresh,
                ),
            )
        }
        if (state.forcedOfflineMode) {
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

// DetailsBringIntoViewPolicy
@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal val DetailsBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val targetEnd = offset + size
        val edgeGuard = (containerSize * 0.06f).coerceAtMost(56f)
        val visibleStart = edgeGuard
        val visibleEnd = containerSize - edgeGuard
        return when {
            offset < visibleStart -> offset - visibleStart
            targetEnd > visibleEnd -> targetEnd - visibleEnd
            else -> 0f
        }
    }
}

// DetailsContentFocus
internal class DetailsLayerFocusState {
    var hasFocus by mutableStateOf(false)
}

@Composable
internal fun rememberDetailsLayerFocusState(): DetailsLayerFocusState = remember {
    DetailsLayerFocusState()
}

@Composable
internal fun DetailsContentFocusEffects(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
    layerFocusState: DetailsLayerFocusState,
) {
    RetainDetailsFocusKeyEffect(model.screenUiState, focusGridState)
    RegisterDetailsFocusRecoveryEffect(model.screenUiState, actions, focusGridState, layerFocusState)
    RequestDetailsFocusEffect(model, presentation, focusGridState)
}

@Composable
private fun RetainDetailsFocusKeyEffect(
    screenUiState: DetailsScreenUiState,
    focusGridState: VisualFocusGridState,
) {
    val lastFocusedDetailsKey = focusGridState.lastFocusedKey
    LaunchedEffect(lastFocusedDetailsKey) {
        if (lastFocusedDetailsKey != null) {
            screenUiState.retainedFocusKey = lastFocusedDetailsKey
        }
    }
}

@Composable
private fun RegisterDetailsFocusRecoveryEffect(
    screenUiState: DetailsScreenUiState,
    actions: DetailsContentActions,
    focusGridState: VisualFocusGridState,
    layerFocusState: DetailsLayerFocusState,
) {
    fun recoverFirstDetailsFocusIfMissing(): Boolean {
        if (layerFocusState.hasFocus && focusGridState.focusedIndex != null) return false
        val restored = focusGridState.requestFocusByKey(screenUiState.retainedFocusKey) == true ||
            focusGridState.requestRetainedOrFirstAvailableFocus()
        if (restored) screenUiState.suppressInitialFocusOnReactivation = false
        return restored
    }

    DisposableEffect(focusGridState, actions.onRegisterDpadFocusRecoveryHandler) {
        actions.onRegisterDpadFocusRecoveryHandler(::recoverFirstDetailsFocusIfMissing)
        onDispose { actions.onRegisterDpadFocusRecoveryHandler(null) }
    }
}

@Composable
private fun RequestDetailsFocusEffect(
    model: DetailsContentModel,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    val screenUiState = model.screenUiState
    val detailsId = model.details.id
    val focusLayoutSize = presentation.focusLayout.size
    val hasHeroActions = presentation.watchVideo != null || presentation.hasWatchProgress
    val shouldRestore = model.retainedFocusRequestNonce > 0L &&
        (screenUiState.retainedFocusKey != null || screenUiState.suppressInitialFocusOnReactivation)
    val shouldRequestInitial = model.activeFocusRequestNonce > 0L && !hasHeroActions
    UiControlEffect(
        model.activeFocusRequestNonce,
        model.retainedFocusRequestNonce,
        detailsId,
        hasHeroActions,
        focusLayoutSize,
        enabled = shouldRestore || shouldRequestInitial,
    ) {
        if (shouldRestore) {
            repeat(8) {
                withFrameNanos { }
                val restored = focusGridState.requestFocusByKey(screenUiState.retainedFocusKey) == true ||
                    (screenUiState.retainedFocusKey == null && focusGridState.requestLastFocusedFocus())
                if (restored) {
                    screenUiState.suppressInitialFocusOnReactivation = false
                    return@UiControlEffect
                }
            }
        }
        if (!shouldRequestInitial) return@UiControlEffect
        repeat(8) {
            withFrameNanos { }
            if (focusGridState.requestFirstAvailableFocus()) return@UiControlEffect
        }
    }
}

// DetailsFocusLayoutPolicy
internal fun buildDetailsFocusLayout(counts: DetailsFocusCounts): DetailsFocusLayout {
    var nextIndex = DETAILS_HERO_FOCUS_GRAPH_SIZE
    val offsets = mutableMapOf<DetailsFocusBlock, Int>()

    fun allocate(block: DetailsFocusBlock, count: Int) {
        offsets[block] = nextIndex
        nextIndex += count.coerceAtLeast(0)
    }

    allocate(DetailsFocusBlock.Screenshots, counts.screenshots)
    allocate(DetailsFocusBlock.RelatedAnime, counts.relatedAnime)
    allocate(DetailsFocusBlock.Episodes, counts.episodes)
    allocate(DetailsFocusBlock.Subscriptions, counts.subscriptions)
    allocate(DetailsFocusBlock.Recommendations, counts.recommendations)
    allocate(DetailsFocusBlock.Comments, counts.comments)
    return DetailsFocusLayout(
        size = nextIndex.coerceAtLeast(DETAILS_HERO_FOCUS_GRAPH_SIZE),
        offsets = offsets,
    )
}

internal fun detailsExpandedListFocusCount(itemCount: Int, expanded: Boolean): Int {
    if (itemCount <= 0) return 0
    return 1 + if (expanded) itemCount else 0
}

internal fun detailsSubscriptionFocusItemCount(
    isAuthorized: Boolean,
    videoCount: Int,
    voiceGroupCount: Int,
    allowSubscriptions: Boolean,
    extrasReady: Boolean,
    expanded: Boolean,
): Int {
    val canShowItems = allowSubscriptions && isAuthorized && videoCount > 0 && extrasReady && voiceGroupCount > 0
    if (!canShowItems) return 0
    return detailsExpandedListFocusCount(voiceGroupCount, expanded)
}

internal fun detailsCommentsFocusItemCount(
    extrasReady: Boolean,
    commentCount: Int,
    isAuthorized: Boolean,
    expanded: Boolean,
    hasPagingError: Boolean = false,
): Int {
    if (!extrasReady || (commentCount <= 0 && !isAuthorized)) return 0
    if (!expanded) return 1
    return 1 +
        (if (isAuthorized) 2 else 0) +
        commentCount.coerceAtLeast(0) +
        (if (hasPagingError) 1 else 0)
}

internal fun detailsHorizontalEdgeNavigationIsBlocked(
    localIndex: Int,
    itemCount: Int,
    direction: VisualGridDirection,
): Boolean {
    if (itemCount <= 0) return false
    return when (direction) {
        VisualGridDirection.Left -> localIndex == 0
        VisualGridDirection.Right -> localIndex == itemCount - 1
        VisualGridDirection.Up,
        VisualGridDirection.Down -> false
    }
}

internal fun detailsHorizontalEdgeBlockedDirections(
    localIndex: Int,
    itemCount: Int,
): Set<VisualGridDirection> {
    val blockLeft = detailsHorizontalEdgeNavigationIsBlocked(localIndex, itemCount, VisualGridDirection.Left)
    val blockRight = detailsHorizontalEdgeNavigationIsBlocked(localIndex, itemCount, VisualGridDirection.Right)
    return when {
        blockLeft && blockRight -> DetailsBothHorizontalEdgeDirections
        blockLeft -> DetailsLeftHorizontalEdgeDirection
        blockRight -> DetailsRightHorizontalEdgeDirection
        else -> emptySet()
    }
}

private val DetailsLeftHorizontalEdgeDirection = setOf(VisualGridDirection.Left)
private val DetailsRightHorizontalEdgeDirection = setOf(VisualGridDirection.Right)
private val DetailsBothHorizontalEdgeDirections = setOf(VisualGridDirection.Left, VisualGridDirection.Right)

// DetailsFocusModels
internal enum class DetailsFocusBlock {
    Screenshots,
    RelatedAnime,
    Episodes,
    Subscriptions,
    Recommendations,
    Comments,
}

internal data class DetailsFocusLayout(
    val size: Int,
    private val offsets: Map<DetailsFocusBlock, Int>,
) {
    fun offset(block: DetailsFocusBlock): Int = offsets.getValue(block)
}

internal data class DetailsFocusCounts(
    val screenshots: Int,
    val relatedAnime: Int,
    val episodes: Int,
    val subscriptions: Int,
    val recommendations: Int,
    val comments: Int,
)

internal object DetailsFocusBlockKey {
    const val HeroPoster = "details:hero-poster"
    const val HeroActions = "details:hero-actions"
    const val HeroStats = "details:hero-stats"
    const val HeroFacts = "details:hero-facts"
    const val HeroMarks = "details:hero-marks"
    const val Screenshots = "details:screenshots"
    const val RelatedAnime = "details:related-anime"
    const val Episodes = "details:episodes"
    const val Subscriptions = "details:subscriptions"
    const val Recommendations = "details:recommendations"
    const val Comments = "details:comments"
}

// DetailsFocusResolver
internal fun resolveDetailsFocusLayout(
    details: AnimeDetails,
    videos: LoadState<List<VideoVariant>>,
    readyVideos: List<VideoVariant>,
    auth: AuthUiState,
    detailsExtras: LoadState<AnimeDetailsExtras>,
    forcedOfflineMode: Boolean,
    relatedExpanded: Boolean,
    subscriptionsExpanded: Boolean,
    commentsExpanded: Boolean,
): DetailsFocusLayout {
    val extras = (detailsExtras as? LoadState.Ready)?.data
    val subscriptionCount = if (forcedOfflineMode) {
        0
    } else {
        detailsSubscriptionFocusItemCount(
            isAuthorized = auth.profile != null,
            videoCount = readyVideos.size,
            voiceGroupCount = if (extras == null) 0 else readyVideos.detailsSubscriptionSourceGroups().size,
            allowSubscriptions = details.canShowVideoSubscriptions(),
            extrasReady = extras != null,
            expanded = subscriptionsExpanded,
        )
    }
    return buildDetailsFocusLayout(
        DetailsFocusCounts(
            screenshots = details.screenshots.take(24).size,
            relatedAnime = detailsExpandedListFocusCount(details.relatedAnime.size, relatedExpanded),
            episodes = if (videos is LoadState.Ready && videos.data.isNotEmpty()) EpisodeGridFocusCapacity else 0,
            subscriptions = subscriptionCount,
            recommendations = if (forcedOfflineMode) 0 else extras?.recommendations?.size ?: 0,
            comments = if (forcedOfflineMode) {
                0
            } else {
                detailsCommentsFocusItemCount(
                    extrasReady = extras != null,
                    commentCount = extras?.comments?.size ?: 0,
                    isAuthorized = auth.profile != null,
                    expanded = commentsExpanded,
                    hasPagingError = extras?.commentsPaging?.error != null,
                )
            },
        ),
    )
}
