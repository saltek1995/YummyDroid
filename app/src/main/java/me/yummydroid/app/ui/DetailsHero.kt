package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoVariant

// DetailsHeroActionButtons
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsHeroActionButtons(
    policy: DetailsHeroActionPolicy,
    actions: DetailsHeroActions,
    dialogState: DetailsHeroActionDialogState,
    focus: DetailsHeroActionFocus,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        policy.primaryVideo?.let {
            DetailsHeroPrimaryAction(policy, actions, focus)
        }
        if (policy.showDownload) {
            DialogActionButton(
                text = uiText(UiStringKey.Download),
                modifier = focus.actionModifier(DetailsHeroFocusIndex.DownloadAction),
                onClick = dialogState::openDownload,
            )
        }
        if (policy.showReset) {
            DialogActionButton(
                text = uiText(UiStringKey.ResetWatchProgress),
                modifier = focus.resetModifier(policy.primaryVideo),
                onClick = dialogState::openReset,
            )
        }
    }
}
@Composable
private fun DetailsHeroPrimaryAction(
    policy: DetailsHeroActionPolicy,
    actions: DetailsHeroActions,
    focus: DetailsHeroActionFocus,
) {
    val primaryVideo = policy.primaryVideo ?: return
    val resumeTarget = policy.resumeTarget
    DialogActionButton(
        text = when {
            policy.primaryLoading -> uiText(UiStringKey.Loading)
            resumeTarget != null -> uiText(UiStringKey.Continue)
            else -> uiText(UiStringKey.Watch5af041)
        },
        primary = true,
        loading = policy.primaryLoading,
        modifier = focus.primaryModifier(),
        onClick = if (resumeTarget != null) {
            { actions.onPlayVideoAt(resumeTarget.video, resumeTarget.positionMs) }
        } else {
            { actions.onPlayVideo(primaryVideo) }
        },
    )
}
// DetailsHeroActionDialogs
@Composable
internal fun DetailsHeroActionDialogs(
    model: DetailsHeroModel,
    policy: DetailsHeroActionPolicy,
    actions: DetailsHeroActions,
    state: DetailsHeroActionDialogState,
) {
    val selectedDownloadVideo = policy.selectedDownloadVideo
    if (model.interactive && state.downloadOpen && selectedDownloadVideo != null) {
        DownloadPlanDialog(
            animeId = model.details.id,
            animeTitle = model.details.title,
            videos = model.downloadVideos,
            selectedVideo = selectedDownloadVideo,
            selected = model.defaultDownloadQuality,
            onResolveSampledQualities = actions.onResolveSampledDownloadQualities,
            onConfirm = { plan ->
                state.downloadOpen = false
                actions.onDownloadAllVideos(plan)
            },
            onDismiss = { state.downloadOpen = false },
        )
    }
    if (model.interactive && state.resetOpen) {
        ResetWatchProgressDialog(
            onConfirm = {
                state.resetOpen = false
                actions.onResetWatchProgress()
            },
            onDismiss = { state.resetOpen = false },
        )
    }
}

@Composable
private fun ResetWatchProgressDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = androidx.compose.ui.Modifier.yummyDialogMotion(),
        onDismissRequest = onDismiss,
        title = { Text(uiText(UiStringKey.ResetWatchProgress)) },
        text = { Text(uiText(UiStringKey.DeleteWatchProgressForAllEpisodesOfThisAnime)) },
        confirmButton = {
            DialogActionButton(
                text = uiText(UiStringKey.Reset),
                primary = true,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            DialogActionButton(text = uiText(UiStringKey.Cancel), onClick = onDismiss)
        },
    )
}
// DetailsHeroActionDialogState
internal class DetailsHeroActionDialogState {
    var downloadOpen by mutableStateOf(false)
    var resetOpen by mutableStateOf(false)

    fun openDownload() {
        resetOpen = false
        downloadOpen = true
    }

    fun openReset() {
        downloadOpen = false
        resetOpen = true
    }

    fun closeAll() {
        downloadOpen = false
        resetOpen = false
    }

    fun handleInput(action: InputAction): Boolean {
        if (action != InputAction.Back) return false
        return when {
            downloadOpen -> {
                downloadOpen = false
                true
            }
            resetOpen -> {
                resetOpen = false
                true
            }
            else -> false
        }
    }
}

@Composable
internal fun rememberDetailsHeroActionDialogState(
    interactive: Boolean,
    onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
): DetailsHeroActionDialogState {
    val state = remember { DetailsHeroActionDialogState() }
    LaunchedEffect(interactive) {
        if (!interactive) state.closeAll()
    }
    val inputActionHandler by rememberUpdatedState { action: InputAction -> state.handleInput(action) }
    DisposableEffect(interactive, state.downloadOpen, state.resetOpen, onRegisterModalInputActionHandler) {
        if (interactive && (state.downloadOpen || state.resetOpen)) {
            onRegisterModalInputActionHandler { action -> inputActionHandler(action) }
        } else {
            onRegisterModalInputActionHandler(null)
        }
        onDispose { onRegisterModalInputActionHandler(null) }
    }
    return state
}
// DetailsHeroActionFocus
internal class DetailsHeroActionFocus(
    private val primaryRequester: FocusRequester,
    private val gridState: VisualFocusGridState?,
) {
    fun primaryModifier(): Modifier {
        return if (gridState == null) {
            Modifier.focusRequester(primaryRequester)
        } else {
            actionModifier(DetailsHeroFocusIndex.PrimaryAction)
        }
    }

    fun actionModifier(index: Int): Modifier {
        val state = gridState ?: return Modifier
        return Modifier.visualFocusGridItem(
            state = state,
            index = index,
            horizontal = true,
            vertical = true,
            blockKey = DetailsFocusBlockKey.HeroActions,
            blockEntryIndex = index,
        )
    }

    fun resetModifier(watchVideo: VideoVariant?): Modifier = when {
        gridState != null -> actionModifier(DetailsHeroFocusIndex.ResetAction)
        watchVideo == null -> Modifier.focusRequester(primaryRequester)
        else -> Modifier
    }
}

@Composable
internal fun rememberDetailsHeroActionFocus(
    policy: DetailsHeroActionPolicy,
    externalPrimaryFocusRequester: FocusRequester?,
    focusRequestNonce: Long,
    heroFocusGridState: VisualFocusGridState?,
): DetailsHeroActionFocus {
    val primaryVideoId = policy.primaryVideo?.id ?: -1L
    val resumeVideoId = policy.resumeTarget?.video?.id ?: -1L
    val internalRequester = remember(primaryVideoId, resumeVideoId) { FocusRequester() }
    val primaryRequester = externalPrimaryFocusRequester
        ?: heroFocusGridState?.requester(policy.primaryFocusIndex)
        ?: internalRequester
    val inputModeManager = LocalInputModeManager.current

    UiControlEffect(
        focusRequestNonce,
        primaryVideoId,
        resumeVideoId,
        policy.showReset,
        inputModeManager.inputMode,
        enabled = focusRequestNonce > 0L && inputModeManager.inputMode != InputMode.Touch,
    ) {
        repeat(4) {
            withFrameNanos { }
            if (primaryRequester.requestFocusSafely()) return@UiControlEffect
        }
    }
    return DetailsHeroActionFocus(primaryRequester, heroFocusGridState)
}
// DetailsHeroActions
@Composable
internal fun DetailsHeroActionPanel(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    externalPrimaryFocusRequester: FocusRequester? = null,
    heroFocusGridState: VisualFocusGridState? = null,
) {
    val policy = resolveDetailsHeroActionPolicy(
        watchVideo = model.watchVideo,
        resumeTarget = model.resumeTarget,
        canDownload = model.canDownload,
        hasDownloadVideos = model.downloadVideos.isNotEmpty(),
        hasWatchProgress = model.hasWatchProgress,
        playbackHistoryLoading = model.playbackHistoryLoading,
    )
    if (!policy.showPanel) return
    val dialogState = rememberDetailsHeroActionDialogState(
        interactive = model.interactive,
        onRegisterModalInputActionHandler = actions.onRegisterModalInputActionHandler,
    )
    val focus = rememberDetailsHeroActionFocus(
        policy = policy,
        externalPrimaryFocusRequester = externalPrimaryFocusRequester,
        focusRequestNonce = model.activeFocusRequestNonce,
        heroFocusGridState = heroFocusGridState,
    )
    DetailsHeroActionButtons(policy, actions, dialogState, focus)
    DetailsHeroActionDialogs(model, policy, actions, dialogState)
}

internal data class DetailsHeroActionPolicy(
    val primaryVideo: VideoVariant?,
    val resumeTarget: HeroResumeTarget?,
    val selectedDownloadVideo: VideoVariant?,
    val showDownload: Boolean,
    val showReset: Boolean,
    val primaryLoading: Boolean,
) {
    val showPanel: Boolean
        get() = primaryVideo != null || showReset

    val primaryFocusIndex: Int
        get() = if (primaryVideo != null) {
            DetailsHeroFocusIndex.PrimaryAction
        } else {
            DetailsHeroFocusIndex.ResetAction
        }
}

internal fun resolveDetailsHeroActionPolicy(
    watchVideo: VideoVariant?,
    resumeTarget: HeroResumeTarget?,
    canDownload: Boolean,
    hasDownloadVideos: Boolean,
    hasWatchProgress: Boolean,
    playbackHistoryLoading: Boolean = false,
): DetailsHeroActionPolicy = DetailsHeroActionPolicy(
    primaryVideo = watchVideo,
    resumeTarget = resumeTarget,
    selectedDownloadVideo = resumeTarget?.video ?: watchVideo,
    showDownload = watchVideo != null && canDownload && hasDownloadVideos,
    showReset = hasWatchProgress,
    primaryLoading = watchVideo != null && resumeTarget == null && playbackHistoryLoading,
)
// DetailsHeroActionsModel
internal data class DetailsHeroActions(
    val onOpenLogin: () -> Unit,
    val onGenreFilterSelected: (Long, FilterOption) -> Unit,
    val onYearFilterSelected: (Long, Int) -> Unit,
    val onStudioFilterSelected: (Long, FilterOption) -> Unit,
    val onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    val onSelectListMark: (UserAnimeListMark) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onRetry: () -> Unit,
    val onSetAnimeRating: (Int?) -> Unit,
    val onPlayVideo: (VideoVariant) -> Unit,
    val onPlayVideoAt: (VideoVariant, Long) -> Unit,
    val onResolveSampledDownloadQualities: suspend (
        Set<String>,
        List<VideoVariant>,
    ) -> Map<String, List<PreferredQuality>>,
    val onDownloadAllVideos: (DownloadPlan) -> Unit,
    val onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    val onResetWatchProgress: () -> Unit,
)
// DetailsHeroFocus
internal const val DETAILS_HERO_FOCUS_GRAPH_SIZE = 80

internal object DetailsHeroFocusIndex {
    const val PrimaryAction = 0
    const val DownloadAction = 1
    const val ResetAction = 2
    const val RatingBadge = 3
    const val Poster = 4
    const val MarkStart = 24
    const val FactGenreStart = 32
    const val FactYear = 40
    const val FactStudioStart = 41
    const val FactCreatorStart = 47
}
// DetailsHeroModel
internal data class DetailsHeroModel(
    val details: AnimeDetails,
    val interactive: Boolean,
    val activeFocusRequestNonce: Long,
    val isWide: Boolean,
    val watchVideo: VideoVariant?,
    val resumeTarget: HeroResumeTarget?,
    val downloadVideos: List<VideoVariant>,
    val downloadedSummary: String?,
    val episodeSummary: String,
    val apiEpisodeCount: Int,
    val auth: AuthUiState,
    val animeMark: LoadState<UserAnimeMark?>,
    val detailsExtras: LoadState<AnimeDetailsExtras>,
    val showMarkPanel: Boolean,
    val showHeroRating: Boolean,
    val defaultDownloadQuality: PreferredQuality,
    val canDownload: Boolean,
    val hasWatchProgress: Boolean,
    val playbackHistoryLoading: Boolean,
)
// DetailsHeroRuntime
@Composable
internal fun DetailsHeroModern(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    modifier: Modifier = Modifier,
    focusGridState: VisualFocusGridState? = null,
) {
    val localHeroFocusGridState = rememberVisualFocusGridState(
        size = DETAILS_HERO_FOCUS_GRAPH_SIZE,
        key = model.details.id,
        allowLoosePerpendicularMatch = true,
    )
    Box(modifier = modifier) {
        model.details.backdropUrl?.let { backdrop ->
            PosterImage(
                url = backdrop,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(heroBackdropScrim()),
            )
        }
        DetailsHeroSiteLayout(
            model = model,
            actions = actions,
            heroFocusGridState = focusGridState ?: localHeroFocusGridState,
            modifier = Modifier
                .align(if (model.isWide) Alignment.BottomStart else Alignment.TopStart)
                .fillMaxWidth()
                .then(if (!model.isWide) Modifier.statusBarsPadding() else Modifier),
        )
    }
}

@Composable
private fun heroBackdropScrim(): Brush {
    val background = MaterialTheme.colorScheme.background
    return remember(background) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.18f),
                background.copy(alpha = 0.48f),
                background.copy(alpha = 0.86f),
            ),
        )
    }
}
// DetailsHeroSiteLayout
@Composable
internal fun DetailsHeroSiteLayout(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    heroFocusGridState: VisualFocusGridState,
    modifier: Modifier = Modifier,
) {
    val windowSize = currentWindowSizeDp()
    val responsiveWindowSize = currentResponsiveWindowSizeDp()
    BoxWithConstraints(modifier = modifier) {
        val geometry = resolveDetailsHeroLayoutGeometry(
            maxWidth = maxWidth,
            windowHeight = windowSize.height,
            responsiveWidth = responsiveWindowSize.width,
            responsiveHeight = responsiveWindowSize.height,
        )
        if (geometry.expanded) {
            DetailsHeroWideLayout(model, actions, heroFocusGridState, geometry)
        } else {
            DetailsHeroCompactLayout(model, actions, heroFocusGridState, geometry)
        }
    }
}

@Composable
private fun DetailsHeroWideLayout(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    focusGridState: VisualFocusGridState,
    geometry: DetailsHeroLayoutGeometry,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = geometry.horizontalPadding,
                vertical = geometry.verticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(geometry.gap),
        verticalAlignment = Alignment.Top,
    ) {
        DetailsHeroInfoBlock(
            model = model,
            actions = actions,
            compact = geometry.compact,
            isWide = true,
            focusGridState = focusGridState,
            modifier = Modifier.weight(1f),
        )
        DetailsHeroMediaCard(
            model = model,
            actions = actions,
            focusGridState = focusGridState,
            markMaxWidth = geometry.markMaxWidth,
            modifier = Modifier.width(geometry.posterWidth),
        )
    }
}

@Composable
private fun DetailsHeroCompactLayout(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    focusGridState: VisualFocusGridState,
    geometry: DetailsHeroLayoutGeometry,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = geometry.verticalPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(geometry.gap),
    ) {
        DetailsHeroMediaCard(
            model = model,
            actions = actions,
            focusGridState = focusGridState,
            markMaxWidth = geometry.markMaxWidth,
            modifier = Modifier.fillMaxWidth(),
        )
        DetailsHeroInfoBlock(
            model = model,
            actions = actions,
            compact = geometry.compact,
            isWide = false,
            focusGridState = focusGridState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = geometry.horizontalPadding),
        )
    }
}

@Composable
private fun DetailsHeroInfoBlock(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    compact: Boolean,
    isWide: Boolean,
    focusGridState: VisualFocusGridState,
    modifier: Modifier,
) {
    DetailsHeroSiteInfo(
        model = model,
        actions = actions,
        compact = compact,
        isWide = isWide,
        heroFocusGridState = focusGridState,
        modifier = modifier,
    )
}
