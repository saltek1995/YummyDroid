package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.DownloadPlan
import me.yummydroid.app.InputAction
import me.yummydroid.app.LoadState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeDetails
import me.yummydroid.app.data.AppSettings
import me.yummydroid.app.data.FilterOption
import me.yummydroid.app.data.PlaybackProgress
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.RelatedAnime
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.data.canShowVideoSubscriptions
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.siteVoiceOrderIndex
import me.yummydroid.app.formatRating
import me.yummydroid.app.readyListOrEmpty
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.HorizontalScrollEdgeContentPadding
import me.yummydroid.app.ui.components.HorizontalScrollEdgeFrame
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor

// DetailsAnimeRowSection
@Composable
internal fun DetailsAnimeRowSection(
    title: String,
    animes: List<Anime>,
    onOpenAnime: (Long, Any?) -> Unit,
    entryFocusRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (animes.isEmpty()) return
    val rowState = remember(title, animes.size, animes.firstOrNull()?.id) { LazyListState() }
    SyncDetailsAnimeRowFocus(rowState, animes.size, focusGridState, focusIndexOffset, focusBlockKey)
    RegisterVirtualFocusRowEntry(rowState, focusGridState, focusIndexOffset, focusBlockKey)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusEntryGroup(entryFocusRequester)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        HorizontalScrollEdgeFrame(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LazyRow(
                state = rowState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = HorizontalScrollEdgeContentPadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                lazyItemsIndexed(
                    animes,
                    key = { index, anime -> "details-anime-row:$title:$index:${anime.id}:${anime.title}" },
                ) { index, anime ->
                    DetailsAnimeRowItem(
                        anime = anime,
                        index = index,
                        itemCount = animes.size,
                        onOpenAnime = onOpenAnime,
                        entryFocusRequester = entryFocusRequester,
                        focusGridState = focusGridState,
                        focusIndexOffset = focusIndexOffset,
                        focusBlockKey = focusBlockKey,
                    )
                }
            }
        }
    }
}

@Composable
private fun RegisterVirtualFocusRowEntry(
    rowState: LazyListState,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
) {
    val state = focusGridState ?: return
    val blockKey = focusBlockKey ?: return
    DisposableEffect(state, blockKey, focusIndexOffset, rowState) {
        val registrationId = state.registerVirtualBlockEntry(blockKey, focusIndexOffset) {
            rowState.requestScrollToItem(0)
        }
        onDispose {
            state.unregisterVirtualBlockEntry(blockKey, focusIndexOffset, registrationId)
        }
    }
    LaunchedEffect(
        state,
        blockKey,
        focusIndexOffset,
        rowState.layoutInfo.visibleItemsInfo.firstOrNull()?.index,
    ) {
        if (rowState.layoutInfo.visibleItemsInfo.any { item -> item.index == 0 }) {
            withFrameNanos { }
            state.completePendingMaterializedFocus()
        }
    }
}

@Composable
private fun SyncDetailsAnimeRowFocus(
    rowState: LazyListState,
    itemCount: Int,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
) {
    var wasFocusedInside by remember(focusGridState, focusBlockKey, focusIndexOffset) { mutableStateOf(false) }
    val focusedIndex = focusGridState?.focusedIndex
    val focusedInside = focusedIndex != null && focusedIndex in focusIndexOffset until (focusIndexOffset + itemCount)
    UiControlEffect(
        focusedIndex,
        itemCount,
        focusIndexOffset,
        focusGridState,
        enabled = focusedInside && !wasFocusedInside && focusedIndex == focusIndexOffset,
    ) {
        val state = focusGridState ?: return@UiControlEffect
        rowState.scrollToItem(0)
        withFrameNanos { }
        state.requester(focusIndexOffset)?.requestFocusSafely()
        wasFocusedInside = true
    }
    LaunchedEffect(focusedInside) {
        if (!focusedInside) wasFocusedInside = false
    }
}

@Composable
private fun DetailsAnimeRowItem(
    anime: Anime,
    index: Int,
    itemCount: Int,
    onOpenAnime: (Long, Any?) -> Unit,
    entryFocusRequester: FocusRequester?,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
) {
    val itemFocusKey = detailsAnimeRowFocusKey(focusBlockKey, anime.id)
    AnimeCard(
        anime = anime,
        onClick = { onOpenAnime(anime.id, itemFocusKey) },
        modifier = Modifier
            .width(172.dp)
            .then(
                detailsAnimeRowItemFocusModifier(
                    index = index,
                    entryFocusRequester = entryFocusRequester,
                    focusGridState = focusGridState,
                    focusIndexOffset = focusIndexOffset,
                    focusBlockKey = focusBlockKey,
                    itemFocusKey = itemFocusKey,
                ),
            )
            .horizontalEdgeFocusHints(index, itemCount),
    )
}

private fun detailsAnimeRowItemFocusModifier(
    index: Int,
    entryFocusRequester: FocusRequester?,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
    itemFocusKey: Any?,
): Modifier = when {
    focusGridState != null -> Modifier.visualFocusGridItem(
        state = focusGridState,
        index = focusIndexOffset + index,
        horizontal = true,
        vertical = true,
        blockKey = focusBlockKey,
        blockEntryIndex = focusIndexOffset,
        focusKey = itemFocusKey,
    )
    index == 0 && entryFocusRequester != null -> Modifier.focusRequester(entryFocusRequester)
    else -> Modifier
}

internal fun detailsAnimeRowFocusKey(blockKey: Any?, animeId: Long): Any? {
    return blockKey?.let { "$it:anime:$animeId" }
}

// DetailsContentModels
internal data class DetailsContentModel(
    val details: AnimeDetails,
    val screenUiState: DetailsScreenUiState,
    val interactive: Boolean,
    val activeFocusRequestNonce: Long,
    val retainedFocusRequestNonce: Long,
    val settings: AppSettings,
    val videos: LoadState<List<VideoVariant>>,
    val selectedGroup: String?,
    val auth: AuthUiState,
    val animeMark: LoadState<UserAnimeMark?>,
    val detailsExtras: LoadState<AnimeDetailsExtras>,
    val forcedOfflineMode: Boolean,
    val playbackProgress: PlaybackProgress?,
    val playbackHistory: List<PlaybackProgress>,
)

internal data class DetailsContentActions(
    val onOpenAnime: (Long) -> Unit,
    val onOpenLogin: () -> Unit,
    val onGenreFilterSelected: (Long, FilterOption) -> Unit,
    val onYearFilterSelected: (Long, Int) -> Unit,
    val onStudioFilterSelected: (Long, FilterOption) -> Unit,
    val onCreatorFilterSelected: (Long, FilterOption) -> Unit,
    val onSelectVideoGroup: (String) -> Unit,
    val onPlayVideo: (VideoVariant) -> Unit,
    val onPlayVideoWithResumeChoice: (VideoVariant, Long) -> Unit,
    val onPlayVideoAt: (VideoVariant, Long) -> Unit,
    val onSelectAnimeListMark: (UserAnimeListMark) -> Unit,
    val onToggleFavorite: () -> Unit,
    val onSetAnimeRating: (Int?) -> Unit,
    val onAddAnimeComment: (String) -> Unit,
    val onLoadMoreAnimeComments: () -> Unit,
    val onToggleVideoSubscription: (VideoVariant) -> Unit,
    val onResolveSampledDownloadQualities: suspend (
        Set<String>,
        List<VideoVariant>,
    ) -> Map<String, List<PreferredQuality>>,
    val onDownloadAllVideos: (DownloadPlan) -> Unit,
    val onResetAnimeWatchProgress: (Long) -> Unit,
    val onRegisterModalInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    val onRegisterDpadFocusRecoveryHandler: ((() -> Boolean)?) -> Unit,
    val onRetry: () -> Unit,
)

// DetailsContentPresentation
internal data class DetailsContentPresentation(
    val isWide: Boolean,
    val readyVideos: List<VideoVariant>,
    val playableVideos: List<VideoVariant>,
    val downloadedSummary: String?,
    val episodeSummary: String,
    val apiEpisodeCount: Int,
    val watchVideo: VideoVariant?,
    val resumeTarget: HeroResumeTarget?,
    val hasWatchProgress: Boolean,
    val focusLayout: DetailsFocusLayout,
)

@Composable
internal fun rememberDetailsContentPresentation(model: DetailsContentModel): DetailsContentPresentation {
    val windowSize = currentResponsiveWindowSizeDp()
    val isLandscape = windowSize.width > windowSize.height
    val readyVideos = model.videos.readyListOrEmpty()
    val playableVideos = remember(readyVideos, model.forcedOfflineMode) {
        if (model.forcedOfflineMode) readyVideos.filter { it.isOfflineAvailable } else readyVideos
    }
    val watchVideo = remember(playableVideos, model.selectedGroup) {
        playableVideos.heroStartVideo(model.selectedGroup)
    }
    val resumeTarget = remember(playableVideos, model.playbackProgress) {
        model.playbackProgress.resolveResumeTarget(playableVideos)
    }
    val focusLayout = rememberDetailsFocusLayout(model, readyVideos)
    return DetailsContentPresentation(
        isWide = windowSize.width >= 900.dp || (isLandscape && windowSize.width >= 600.dp),
        readyVideos = readyVideos,
        playableVideos = playableVideos,
        downloadedSummary = readyVideos.downloadedEpisodeSummary(),
        episodeSummary = model.details.effectiveEpisodeSummary(),
        apiEpisodeCount = model.details.episodeCount,
        watchVideo = watchVideo,
        resumeTarget = resumeTarget,
        hasWatchProgress = model.playbackProgress != null || model.playbackHistory.isNotEmpty(),
        focusLayout = focusLayout,
    )
}

@Composable
private fun rememberDetailsFocusLayout(
    model: DetailsContentModel,
    readyVideos: List<VideoVariant>,
): DetailsFocusLayout {
    val details = model.details
    val screenUiState = model.screenUiState
    return remember(
        details.id,
        details.screenshots.size,
        details.relatedAnime.size,
        screenUiState.relatedExpanded,
        readyVideos,
        model.videos,
        model.detailsExtras,
        model.auth.profile,
        model.forcedOfflineMode,
        screenUiState.subscriptionsExpanded,
        screenUiState.commentsExpanded,
    ) {
        resolveDetailsFocusLayout(
            details = details,
            videos = model.videos,
            readyVideos = readyVideos,
            auth = model.auth,
            detailsExtras = model.detailsExtras,
            forcedOfflineMode = model.forcedOfflineMode,
            relatedExpanded = screenUiState.relatedExpanded,
            subscriptionsExpanded = screenUiState.subscriptionsExpanded,
            commentsExpanded = screenUiState.commentsExpanded,
        )
    }
}

// DetailsContentSections
@Composable
internal fun DetailsContentSections(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    DetailsOverviewSections(model, actions, presentation, focusGridState)
    DetailsVideoSection(model, actions, presentation, focusGridState)
    if (!model.forcedOfflineMode) {
        DetailsOnlineSections(model, actions, presentation, focusGridState)
    }
}

@Composable
private fun DetailsOverviewSections(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    val details = model.details
    DetailsHeroModern(
        model = model.toDetailsHeroModel(presentation),
        actions = actions.toDetailsHeroActions(details.id),
        focusGridState = focusGridState,
        modifier = Modifier.fillMaxWidth(),
    )
    DetailsDescriptionSection(description = details.description)
    DetailsScreenshotsSection(
        screenshots = details.screenshots,
        interactive = model.interactive,
        onRegisterInputActionHandler = actions.onRegisterModalInputActionHandler,
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Screenshots),
        focusBlockKey = DetailsFocusBlockKey.Screenshots,
    )
    DetailsRelatedAnimeSection(
        relatedAnime = details.relatedAnime,
        expanded = model.screenUiState.relatedExpanded,
        onExpandedChange = { model.screenUiState.relatedExpanded = it },
        onOpenAnime = { animeId, focusKey -> model.openAnime(actions, animeId, focusKey) },
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.RelatedAnime),
        focusBlockKey = DetailsFocusBlockKey.RelatedAnime,
    )
}

private fun DetailsContentModel.toDetailsHeroModel(
    presentation: DetailsContentPresentation,
): DetailsHeroModel = DetailsHeroModel(
    details = details,
    interactive = interactive,
    activeFocusRequestNonce = activeFocusRequestNonce,
    isWide = presentation.isWide,
    watchVideo = presentation.watchVideo,
    resumeTarget = presentation.resumeTarget,
    downloadVideos = presentation.playableVideos,
    downloadedSummary = presentation.downloadedSummary,
    episodeSummary = presentation.episodeSummary,
    apiEpisodeCount = presentation.apiEpisodeCount,
    auth = auth,
    animeMark = animeMark,
    detailsExtras = detailsExtras,
    showMarkPanel = !forcedOfflineMode && auth.profile != null,
    showHeroRating = !forcedOfflineMode,
    defaultDownloadQuality = settings.defaultQuality,
    canDownload = !forcedOfflineMode,
    hasWatchProgress = presentation.hasWatchProgress,
)

private fun DetailsContentActions.toDetailsHeroActions(animeId: Long): DetailsHeroActions =
    DetailsHeroActions(
        onOpenLogin = onOpenLogin,
        onGenreFilterSelected = onGenreFilterSelected,
        onYearFilterSelected = onYearFilterSelected,
        onStudioFilterSelected = onStudioFilterSelected,
        onCreatorFilterSelected = onCreatorFilterSelected,
        onSelectListMark = onSelectAnimeListMark,
        onToggleFavorite = onToggleFavorite,
        onRetry = onRetry,
        onSetAnimeRating = onSetAnimeRating,
        onPlayVideo = onPlayVideo,
        onPlayVideoAt = onPlayVideoAt,
        onResolveSampledDownloadQualities = onResolveSampledDownloadQualities,
        onDownloadAllVideos = onDownloadAllVideos,
        onRegisterModalInputActionHandler = onRegisterModalInputActionHandler,
        onResetWatchProgress = { onResetAnimeWatchProgress(animeId) },
    )

@Composable
private fun DetailsVideoSection(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    when (val videos = model.videos) {
        LoadState.Loading -> LoadingPane(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
        )
        is LoadState.Error -> ErrorPane(
            message = videos.message,
            onRetry = actions.onRetry,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp),
        )
        is LoadState.Ready -> VideoPickerModern(
            videos = videos.data,
            selectedGroup = model.selectedGroup,
            playbackHistory = model.playbackHistory,
            onSelectGroup = actions.onSelectVideoGroup,
            onPlayVideo = actions.onPlayVideo,
            onPlayVideoWithResumeChoice = actions.onPlayVideoWithResumeChoice,
            forcedOfflineMode = model.forcedOfflineMode,
            modifier = Modifier.fillMaxWidth(),
            focusGridState = focusGridState,
            focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Episodes),
            focusBlockKey = DetailsFocusBlockKey.Episodes,
        )
    }
}

@Composable
private fun DetailsOnlineSections(
    model: DetailsContentModel,
    actions: DetailsContentActions,
    presentation: DetailsContentPresentation,
    focusGridState: VisualFocusGridState,
) {
    val screenUiState = model.screenUiState
    val extras = when (val extrasState = model.detailsExtras) {
        is LoadState.Ready -> extrasState.data
        LoadState.Loading -> return
        is LoadState.Error -> {
            ErrorPane(
                message = extrasState.message,
                onRetry = actions.onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 180.dp),
            )
            return
        }
    }
    if (model.details.canShowVideoSubscriptions()) {
        DetailsSubscriptionsSection(
            auth = model.auth,
            videos = presentation.readyVideos,
            subscriptions = extras.subscriptions,
            expanded = screenUiState.subscriptionsExpanded,
            onExpandedChange = { screenUiState.subscriptionsExpanded = it },
            onToggleVideoSubscription = actions.onToggleVideoSubscription,
            focusGridState = focusGridState,
            focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Subscriptions),
            focusBlockKey = DetailsFocusBlockKey.Subscriptions,
        )
    }
    DetailsAnimeRowSection(
        title = uiText(UiStringKey.Similar),
        animes = extras.recommendations,
        onOpenAnime = { animeId, focusKey -> model.openAnime(actions, animeId, focusKey) },
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Recommendations),
        focusBlockKey = DetailsFocusBlockKey.Recommendations,
    )
    DetailsCommentsSection(
        comments = extras.comments,
        totalComments = model.details.commentsCount,
        commentsPaging = extras.commentsPaging,
        isAuthorized = model.auth.profile != null,
        scrollState = screenUiState.scrollState,
        expanded = screenUiState.commentsExpanded,
        onExpandedChange = { screenUiState.commentsExpanded = it },
        onAddAnimeComment = actions.onAddAnimeComment,
        onLoadMoreAnimeComments = actions.onLoadMoreAnimeComments,
        focusGridState = focusGridState,
        focusIndexOffset = presentation.focusLayout.offset(DetailsFocusBlock.Comments),
        focusBlockKey = DetailsFocusBlockKey.Comments,
    )
}

private fun DetailsContentModel.openAnime(
    actions: DetailsContentActions,
    animeId: Long,
    focusKey: Any?,
) {
    if (focusKey != null) screenUiState.retainedFocusKey = focusKey
    actions.onOpenAnime(animeId)
}

// DetailsDescriptionSection
@Composable
internal fun DetailsDescriptionSection(description: String) {
    val normalizedDescription = description.trim()
    if (normalizedDescription.isBlank()) return
    Text(
        text = normalizedDescription,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
    )
}

// DetailsFactValue
private val MissingFactValues = setOf(
    "-",
    "\u2014",
    "\u0432\u0402\u201d",
)

internal fun String.isPresentFactValue(): Boolean {
    val normalized = trim()
    return normalized.isNotBlank() &&
        !normalized.equals("unknown", ignoreCase = true) &&
        !normalized.equals("null", ignoreCase = true) &&
        normalized !in MissingFactValues
}

// DetailsPoster
@Composable
internal fun DetailsPoster(
    posterUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    val interactiveModifier = if (onClick != null) {
        Modifier.dpadClickable(shape, onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(interactiveModifier),
    ) {
        PosterImage(
            url = posterUrl,
            contentDescription = title,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// DetailsRatingScale
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RatingScale(
    selected: Int?,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    leftExitRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
) {
    val shape = RoundedCornerShape(8.dp)
    var focusedRating by remember { mutableStateOf<Int?>(null) }
    val internalFocusGridState = rememberVisualFocusGridState(size = 10)
    val effectiveFocusGridState = focusGridState ?: internalFocusGridState
    val effectiveFocusIndexOffset = if (focusGridState == null) 0 else focusIndexOffset
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
            RatingScaleItems(
                selected = selected,
                focusedRating = focusedRating,
                focusGridState = effectiveFocusGridState,
                focusIndexOffset = effectiveFocusIndexOffset,
                verticalFocusEnabled = focusGridState != null,
                leftExitRequester = leftExitRequester,
                onFocusChanged = { value, focused ->
                    focusedRating = when {
                        focused -> value
                        focusedRating == value -> null
                        else -> focusedRating
                    }
                },
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun RowScope.RatingScaleItems(
    selected: Int?,
    focusedRating: Int?,
    focusGridState: VisualFocusGridState,
    focusIndexOffset: Int,
    verticalFocusEnabled: Boolean,
    leftExitRequester: FocusRequester?,
    onFocusChanged: (Int, Boolean) -> Unit,
    onSelected: (Int) -> Unit,
) {
    val filledRating = focusedRating ?: selected
    val fillAlpha = if (focusedRating != null) 0.24f else 0.16f
    (1..10).forEach { value ->
        RatingScaleItem(
            value = value,
            active = filledRating != null && value <= filledRating,
            fillAlpha = fillAlpha,
            focusGridState = focusGridState,
            focusIndex = focusIndexOffset + value - 1,
            verticalFocusEnabled = verticalFocusEnabled,
            leftExitRequester = leftExitRequester,
            onFocusChanged = onFocusChanged,
            onSelected = onSelected,
        )
        if (value < 10) RatingScaleDivider()
    }
}

@Composable
private fun RowScope.RatingScaleItem(
    value: Int,
    active: Boolean,
    fillAlpha: Float,
    focusGridState: VisualFocusGridState,
    focusIndex: Int,
    verticalFocusEnabled: Boolean,
    leftExitRequester: FocusRequester?,
    onFocusChanged: (Int, Boolean) -> Unit,
    onSelected: (Int) -> Unit,
) {
    val siteScaleColor = ratingScaleColorForValue(value)
    val itemShape = ratingScaleItemShape(value)
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .visualFocusGridItem(
                state = focusGridState,
                index = focusIndex,
                vertical = verticalFocusEnabled,
                leftExit = leftExitRequester,
            )
            .background(
                color = if (active) siteScaleColor.copy(alpha = fillAlpha) else Color.Transparent,
                shape = itemShape,
            )
            .onFocusChanged { onFocusChanged(value, it.isFocused) }
            .dpadClickable(itemShape) { onSelected(value) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = "${uiText(UiStringKey.Rating)} $value",
            modifier = Modifier.size(19.dp),
            tint = if (active) siteScaleColor else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun RatingScaleDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.34f)),
    )
}

private fun ratingScaleItemShape(value: Int) = when (value) {
    1 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
    10 -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
    else -> RoundedCornerShape(0.dp)
}

internal fun ratingScaleColorForValue(value: Int): Color {
    return ratingColorForSiteScale(value.coerceIn(1, 10).toDouble())
}

// DetailsRelatedAnimeSection
@Composable
internal fun DetailsRelatedAnimeSection(
    relatedAnime: List<RelatedAnime>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenAnime: (Long, Any?) -> Unit,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (relatedAnime.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YummySpacing.xl, vertical = YummySpacing.sm),
        verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        AccordionHeader(
            title = uiText(UiStringKey.AnimeReleaseOrder),
            expanded = expanded,
            active = false,
            onClick = { onExpandedChange(!expanded) },
            centerTitle = true,
            modifier = relatedAnimeHeaderFocusModifier(
                focusGridState,
                focusIndexOffset,
                focusBlockKey,
            ),
        )
        if (expanded) {
            RelatedAnimeOrderList(
                relatedAnime = relatedAnime,
                onOpenAnime = onOpenAnime,
                focusGridState = focusGridState,
                focusIndexOffset = focusIndexOffset,
                focusBlockKey = focusBlockKey,
            )
        }
    }
}

private fun relatedAnimeHeaderFocusModifier(
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
): Modifier {
    if (focusGridState == null) return Modifier
    return Modifier.visualFocusGridItem(
        state = focusGridState,
        index = focusIndexOffset,
        horizontal = true,
        vertical = true,
        blockKey = focusBlockKey,
        blockEntryIndex = focusIndexOffset,
        focusKey = focusBlockKey?.let { "$it:header" },
    )
}

@Composable
private fun RelatedAnimeOrderList(
    relatedAnime: List<RelatedAnime>,
    onOpenAnime: (Long, Any?) -> Unit,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = yummySurfaceColor(YummySurfaceRole.Panel),
        contentColor = yummySurfaceContentColor(YummySurfaceRole.Panel),
        border = yummySurfaceBorder(YummySurfaceRole.Panel),
        shape = YummyRadii.smallShape,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = YummySpacing.lg, vertical = YummySpacing.md),
            verticalArrangement = Arrangement.spacedBy(YummySpacing.sm),
        ) {
            relatedAnime.forEachIndexed { index, related ->
                val itemFocusKey = detailsRelatedAnimeFocusKey(focusBlockKey, related.id)
                RelatedAnimeOrderRow(
                    index = index + 1,
                    relatedAnime = related,
                    onClick = { onOpenAnime(related.id, itemFocusKey) },
                    modifier = relatedAnimeItemFocusModifier(
                        focusGridState = focusGridState,
                        focusIndex = focusIndexOffset + index + 1,
                        focusBlockKey = focusBlockKey,
                        itemFocusKey = itemFocusKey,
                    ),
                )
            }
        }
    }
}

private fun relatedAnimeItemFocusModifier(
    focusGridState: VisualFocusGridState?,
    focusIndex: Int,
    focusBlockKey: Any?,
    itemFocusKey: Any?,
): Modifier {
    if (focusGridState == null) return Modifier
    return Modifier.visualFocusGridItem(
        state = focusGridState,
        index = focusIndex,
        horizontal = false,
        vertical = true,
        blockKey = focusBlockKey,
        blockEntryIndex = focusIndex,
        consumeDisabledAxis = true,
        focusKey = itemFocusKey,
    )
}

@Composable
internal fun RelatedAnimeOrderRow(
    index: Int,
    relatedAnime: RelatedAnime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCompact = currentResponsiveWindowSizeDp().width < 680.dp
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
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(YummyRadii.smallShape, onClick),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = YummyRadii.smallShape,
    ) {
        RelatedAnimeOrderRowContent(
            index = index,
            relatedAnime = relatedAnime,
            titleColor = titleColor,
            meta = meta,
            compact = isCompact,
        )
    }
}

@Composable
private fun RelatedAnimeOrderRowContent(
    index: Int,
    relatedAnime: RelatedAnime,
    titleColor: Color,
    meta: String,
    compact: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 58.dp else 42.dp)
            .padding(horizontal = YummySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(YummySpacing.sm),
    ) {
        Text(
            text = "$index.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(34.dp),
        )
        if (compact) {
            RelatedAnimeCompactText(relatedAnime.title, meta, titleColor, Modifier.weight(1f))
        } else {
            RelatedAnimeWideText(
                title = relatedAnime.title,
                meta = meta,
                titleColor = titleColor,
                titleModifier = Modifier.weight(1.3f),
                metaModifier = Modifier.weight(1f),
            )
        }
        RelatedAnimeRating(relatedAnime.rating)
    }
}

@Composable
private fun RelatedAnimeCompactText(
    title: String,
    meta: String,
    titleColor: Color,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
    ) {
        RelatedAnimeTitle(title, titleColor, Modifier)
        if (meta.isNotBlank()) {
            RelatedAnimeMeta(meta, Modifier)
        }
    }
}

@Composable
private fun RelatedAnimeWideText(
    title: String,
    meta: String,
    titleColor: Color,
    titleModifier: Modifier,
    metaModifier: Modifier,
) {
    RelatedAnimeTitle(title, titleColor, titleModifier)
    RelatedAnimeMeta(meta, metaModifier)
}

@Composable
private fun RelatedAnimeTitle(title: String, color: Color, modifier: Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun RelatedAnimeMeta(meta: String, modifier: Modifier) {
    Text(
        text = meta,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun RelatedAnimeRating(rating: Double?) {
    Box(
        modifier = Modifier.width(60.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        rating?.let {
            Surface(
                color = YummyColors.rating,
                contentColor = Color(0xFF211200),
                shape = YummyRadii.pillShape,
            ) {
                Text(
                    text = formatRating(it),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        horizontal = YummySpacing.sm,
                        vertical = YummySpacing.xs,
                    ),
                )
            }
        }
    }
}

internal fun detailsRelatedAnimeFocusKey(blockKey: Any?, animeId: Long): Any? {
    return blockKey?.let { "$it:related:$animeId" }
}

// DetailsScreenshotsSection
@Composable
internal fun DetailsScreenshotsSection(
    screenshots: List<String>,
    interactive: Boolean,
    onRegisterInputActionHandler: (((InputAction) -> Boolean)?) -> Unit,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (screenshots.isEmpty()) return
    val visibleScreenshots = remember(screenshots) { screenshots.take(24) }
    val rowState = remember(visibleScreenshots) { LazyListState() }
    var selectedIndex by remember(visibleScreenshots) { mutableStateOf<Int?>(null) }
    RegisterVirtualFocusRowEntry(rowState, focusGridState, focusIndexOffset, focusBlockKey)
    LaunchedEffect(interactive) {
        if (!interactive) selectedIndex = null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        HorizontalScrollEdgeFrame(
            state = rowState,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LazyRow(
                state = rowState,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = HorizontalScrollEdgeContentPadding),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                lazyItemsIndexed(
                    visibleScreenshots,
                    key = { index, screenshot -> "screenshot:$index:$screenshot" },
                ) { index, screenshot ->
                    ScreenshotThumbnail(
                        screenshot = screenshot,
                        index = index,
                        screenshotCount = visibleScreenshots.size,
                        focusGridState = focusGridState,
                        focusIndexOffset = focusIndexOffset,
                        focusBlockKey = focusBlockKey,
                        onClick = { selectedIndex = index },
                    )
                }
            }
        }
    }

    selectedIndex?.takeIf { interactive }?.let { index ->
        ScreenshotViewerDialog(
            screenshots = visibleScreenshots,
            initialIndex = index,
            onDismiss = { selectedIndex = null },
            onRegisterInputActionHandler = onRegisterInputActionHandler,
        )
    }
}

@Composable
private fun ScreenshotThumbnail(
    screenshot: String,
    index: Int,
    screenshotCount: Int,
    focusGridState: VisualFocusGridState?,
    focusIndexOffset: Int,
    focusBlockKey: Any?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .width(320.dp)
            .aspectRatio(16f / 9f)
            .visualFocusGridItemIfPresent(
                state = focusGridState,
                index = focusIndexOffset + index,
                blockKey = focusBlockKey,
                blockEntryIndex = focusIndexOffset,
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalEdgeFocusHints(index, screenshotCount)
            .dpadClickable(shape, onClick = onClick)
            .onPreviewKeyEvent { event ->
                val state = focusGridState ?: return@onPreviewKeyEvent false
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val direction = event.key.toVisualGridDirectionOrNull()
                    ?.takeIf { it == VisualGridDirection.Up || it == VisualGridDirection.Down }
                    ?: return@onPreviewKeyEvent false
                state.requestFocusTarget(
                    index = focusIndexOffset + index,
                    direction = direction,
                    exit = null,
                )
            },
    ) {
        PosterImage(
            url = screenshot,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

// DetailsSubscriptionsSection
private data class DetailsSubscriptionFocus(
    val state: VisualFocusGridState,
    val indexOffset: Int,
    val blockKey: Any?,
) {
    val contentEntryIndex: Int = indexOffset + 1
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
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (auth.profile == null || videos.isEmpty()) return
    val groups = videos.detailsSubscriptionVoiceGroups()
    if (groups.isEmpty()) return
    val activeCount = groups.count { subscriptions.isVideoVoiceSubscribed(it) }
    val localFocusGridState = rememberVisualFocusGridState(
        size = groups.size + 1,
        key = groups.map { it.id to it.matchingVoiceKey },
    )
    val focus = DetailsSubscriptionFocus(
        state = focusGridState ?: localFocusGridState,
        indexOffset = if (focusGridState == null) 0 else focusIndexOffset,
        blockKey = if (focusGridState == null) null else focusBlockKey,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DetailsSubscriptionsHeader(
            activeCount = activeCount,
            expanded = expanded,
            onClick = { onExpandedChange(!expanded) },
            focus = focus,
            verticalFocusEnabled = focusGridState != null,
        )
        if (expanded) {
            DetailsSubscriptionOptions(
                groups = groups,
                subscriptions = subscriptions,
                focus = focus,
                verticalFocusEnabled = focusGridState != null,
                onToggleVideoSubscription = onToggleVideoSubscription,
            )
        }
    }
}

@Composable
private fun DetailsSubscriptionsHeader(
    activeCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    focus: DetailsSubscriptionFocus,
    verticalFocusEnabled: Boolean,
) {
    AccordionHeader(
        title = uiText(UiStringKey.Subscriptions),
        summary = activeCount.takeIf { it > 0 }?.let { uiText(UiStringKey.ActiveCount, it) }.orEmpty(),
        expanded = expanded,
        active = activeCount > 0,
        onClick = onClick,
        centerTitle = true,
        modifier = Modifier.visualFocusGridItem(
            state = focus.state,
            index = focus.indexOffset,
            horizontal = true,
            vertical = verticalFocusEnabled,
            blockKey = focus.blockKey,
            blockEntryIndex = focus.indexOffset,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSubscriptionOptions(
    groups: List<VideoVariant>,
    subscriptions: List<VideoSubscription>,
    focus: DetailsSubscriptionFocus,
    verticalFocusEnabled: Boolean,
    onToggleVideoSubscription: (VideoVariant) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        groups.forEachIndexed { index, video ->
            DetailsSubscriptionOption(
                video = video,
                subscribed = subscriptions.isVideoVoiceSubscribed(video),
                focus = focus,
                focusIndex = focus.indexOffset + index + 1,
                verticalFocusEnabled = verticalFocusEnabled,
                onClick = { onToggleVideoSubscription(video) },
            )
        }
    }
}

@Composable
private fun DetailsSubscriptionOption(
    video: VideoVariant,
    subscribed: Boolean,
    focus: DetailsSubscriptionFocus,
    focusIndex: Int,
    verticalFocusEnabled: Boolean,
    onClick: () -> Unit,
) {
    val itemShape = RoundedCornerShape(8.dp)
    var itemFocused by remember(video.id, video.matchingVoiceKey) { mutableStateOf(false) }
    Surface(
        modifier = Modifier
            .visualFocusGridItem(
                state = focus.state,
                index = focusIndex,
                horizontal = true,
                vertical = verticalFocusEnabled,
                blockKey = focus.blockKey,
                blockEntryIndex = focus.contentEntryIndex,
            )
            .onFocusChanged { itemFocused = it.isFocused }
            .dpadClickable(itemShape, onClick = onClick),
        color = yummyActionSurfaceColor(selected = subscribed, focused = itemFocused),
        contentColor = yummyActionContentColor(selected = subscribed, focused = itemFocused),
        border = yummyActionBorder(selected = subscribed, focused = itemFocused),
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

internal fun List<VideoVariant>.detailsSubscriptionVoiceGroups(): List<VideoVariant> {
    val siteVoiceOrder = siteVoiceOrderIndex()
    return filter { it.matchingVoiceKey.isNotBlank() }
        .groupBy { it.matchingVoiceKey }
        .values
        .mapNotNull { group -> group.minByOrNull { it.player } }
        .sortedWith(
            compareBy<VideoVariant> { siteVoiceOrder[it.matchingVoiceKey] ?: Int.MAX_VALUE }
                .thenBy { it.matchingDubbingTitle },
        )
        .take(18)
}
