package me.yummydroid.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.canShowVideoSubscriptions

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
