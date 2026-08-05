package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed as lazyItemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.matchingVoiceKey
import me.yummydroid.app.data.RelatedAnime
import me.yummydroid.app.data.siteVoiceOrderIndex
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatCommentTimestamp
import me.yummydroid.app.formatRating
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor
import me.yummydroid.app.ui.theme.YummySurfaceRole

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DetailsRelatedAnimeSection(
    relatedAnime: List<RelatedAnime>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onOpenAnime: (Long) -> Unit,
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
        val shape = YummyRadii.smallShape
        AccordionHeader(
            title = uiText(UiStringKey.AnimeReleaseOrder),
            expanded = expanded,
            active = false,
            onClick = { onExpandedChange(!expanded) },
            centerTitle = true,
            modifier = if (focusGridState != null) {
                Modifier.visualFocusGridItem(
                    state = focusGridState,
                    index = focusIndexOffset,
                    horizontal = true,
                    vertical = true,
                    blockKey = focusBlockKey,
                    blockEntryIndex = focusIndexOffset,
                )
            } else {
                Modifier
            },
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
                            modifier = if (focusGridState != null) {
                                Modifier.visualFocusGridItem(
                                    state = focusGridState,
                                    index = focusIndexOffset + index + 1,
                                    horizontal = false,
                                    vertical = true,
                                    blockKey = focusBlockKey,
                                    blockEntryIndex = focusIndexOffset + index + 1,
                                    consumeDisabledAxis = true,
                                )
                            } else {
                                Modifier
                            },
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
    modifier: Modifier = Modifier,
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
    val rowHeight = if (isCompact) 58.dp else 42.dp

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .dpadClickable(YummyRadii.smallShape, onClick),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = YummyRadii.smallShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
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
            if (isCompact) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(YummySpacing.xs),
                ) {
                    Text(
                        text = relatedAnime.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (meta.isNotBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text(
                    text = relatedAnime.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1.3f),
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Box(
                modifier = Modifier.width(60.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                relatedAnime.rating?.let { rating ->
                    Surface(
                        color = YummyColors.rating,
                        contentColor = Color(0xFF211200),
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
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
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
                focusGridState = focusGridState,
                focusIndexOffset = focusIndexOffset,
                focusBlockKey = focusBlockKey,
            )
        }
    }
}

@Composable
internal fun DetailsRecommendationsSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    onOpenAnime: (Long) -> Unit,
    entryFocusRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (extrasState !is LoadState.Ready) return
    DetailsAnimeRowSection(
        title = uiText(UiStringKey.Similar),
        animes = extrasState.data.recommendations,
        onOpenAnime = onOpenAnime,
        entryFocusRequester = entryFocusRequester,
        focusGridState = focusGridState,
        focusIndexOffset = focusIndexOffset,
        focusBlockKey = focusBlockKey,
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
    entryFocusRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
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
                entryFocusRequester = entryFocusRequester,
                focusGridState = focusGridState,
                focusIndexOffset = focusIndexOffset,
                focusBlockKey = focusBlockKey,
            )
        }
    }
}

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RatingScale(
    selected: Int?,
    onSelected: (Int) -> Unit,
    leftExitRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    var focusedRating by remember { mutableStateOf<Int?>(null) }
    val previewRating = focusedRating
    val filledRating = previewRating ?: selected
    val fillColor = if (previewRating != null) {
        Color(0xFFFF5E66).copy(alpha = 0.92f)
    } else {
        YummyColors.rating.copy(alpha = 0.94f)
    }
    val filledIconColor = if (previewRating != null) {
        Color.White
    } else {
        Color(0xFF211200)
    }
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
            (1..10).forEach { value ->
                val active = filledRating != null && value <= filledRating
                val itemShape = when (value) {
                    1 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
                    10 -> RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                    else -> RoundedCornerShape(0.dp)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .visualFocusGridItem(
                            state = effectiveFocusGridState,
                            index = effectiveFocusIndexOffset + value - 1,
                            vertical = focusGridState != null,
                            leftExit = leftExitRequester,
                        )
                        .background(
                            color = if (active) fillColor else Color.Transparent,
                            shape = itemShape,
                        )
                        .onFocusChanged { focusState ->
                            if (focusState.isFocused) {
                                focusedRating = value
                            } else if (focusedRating == value) {
                                focusedRating = null
                            }
                        }
                        .dpadClickable(itemShape) { onSelected(value) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "${uiText(UiStringKey.Rating)} $value",
                        modifier = Modifier.size(19.dp),
                        tint = if (active) filledIconColor else MaterialTheme.colorScheme.onSurface,
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
    val effectiveFocusGridState = focusGridState ?: localFocusGridState
    val effectiveFocusIndexOffset = if (focusGridState == null) 0 else focusIndexOffset
    val effectiveFocusBlockKey = if (focusGridState == null) null else focusBlockKey
    val contentEntryIndex = effectiveFocusIndexOffset + 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AccordionHeader(
            title = uiText(UiStringKey.Subscriptions),
            summary = activeCount.takeIf { it > 0 }?.let { uiText(UiStringKey.ActiveCount, it) }.orEmpty(),
            expanded = expanded,
            active = activeCount > 0,
            onClick = { onExpandedChange(!expanded) },
            centerTitle = true,
            modifier = Modifier.visualFocusGridItem(
                state = effectiveFocusGridState,
                index = effectiveFocusIndexOffset,
                horizontal = true,
                vertical = focusGridState != null,
                blockKey = effectiveFocusBlockKey,
                blockEntryIndex = effectiveFocusIndexOffset,
            ),
        )

        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                groups.forEachIndexed { index, video ->
                    val subscribed = subscriptions.isVideoVoiceSubscribed(video)
                    val itemShape = RoundedCornerShape(8.dp)
                    var itemFocused by remember(video.id, video.matchingVoiceKey) { mutableStateOf(false) }
                    Surface(
                        modifier = Modifier
                            .visualFocusGridItem(
                                state = effectiveFocusGridState,
                                index = effectiveFocusIndexOffset + index + 1,
                                horizontal = true,
                                vertical = focusGridState != null,
                                blockKey = effectiveFocusBlockKey,
                                blockEntryIndex = contentEntryIndex,
                            )
                            .onFocusChanged { focusState -> itemFocused = focusState.isFocused }
                            .dpadClickable(itemShape) { onToggleVideoSubscription(video) },
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
            }
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

@Composable
internal fun DetailsAnimeRowSection(
    title: String,
    animes: List<Anime>,
    onOpenAnime: (Long) -> Unit,
    entryFocusRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (animes.isEmpty()) return
    val rowState = remember(title, animes.size, animes.firstOrNull()?.id) { LazyListState() }
    var wasFocusedInside by remember(focusGridState, focusBlockKey, focusIndexOffset) { mutableStateOf(false) }
    val focusedIndex = focusGridState?.focusedIndex

    LaunchedEffect(focusedIndex, animes.size, focusIndexOffset, focusGridState) {
        val state = focusGridState ?: return@LaunchedEffect
        val inside = focusedIndex != null && focusedIndex in focusIndexOffset until (focusIndexOffset + animes.size)
        if (inside && !wasFocusedInside) {
            if (focusedIndex == focusIndexOffset) {
                rowState.scrollToItem(0)
                withFrameNanos { }
                state.requester(focusIndexOffset)?.requestFocusSafely()
            }
        }
        wasFocusedInside = inside
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .focusEntryGroup(entryFocusRequester)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            lazyItemsIndexed(
                animes,
                key = { index, anime -> "details-anime-row:$title:$index:${anime.id}:${anime.title}" },
            ) { index, anime ->
                AnimeCard(
                    anime = anime,
                    onClick = { onOpenAnime(anime.id) },
                    modifier = Modifier
                        .width(172.dp)
                        .then(
                            when {
                                focusGridState != null -> Modifier.visualFocusGridItem(
                                    state = focusGridState,
                                    index = focusIndexOffset + index,
                                    horizontal = true,
                                    vertical = true,
                                    blockKey = focusBlockKey,
                                    blockEntryIndex = focusIndexOffset,
                                )
                                index == 0 && entryFocusRequester != null -> {
                                    Modifier.focusRequester(entryFocusRequester)
                                }
                                else -> Modifier
                            },
                        )
                        .horizontalEdgeFocusHints(index, animes.size),
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
    entryFocusRequester: FocusRequester? = null,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
) {
    if (comments.isEmpty() && !isAuthorized) return
    var draft by remember { mutableStateOf("") }
    val commentInputFocusIndex = focusIndexOffset + 1
    val commentSendFocusIndex = focusIndexOffset + 2
    val commentsStartFocusIndex = focusIndexOffset + if (isAuthorized) 3 else 1
    var wasExpanded by remember { mutableStateOf(expanded) }

    LaunchedEffect(expanded, isAuthorized, focusGridState) {
        val opened = !wasExpanded && expanded
        wasExpanded = expanded
        val state = focusGridState ?: return@LaunchedEffect
        if (!opened || !isAuthorized) return@LaunchedEffect
        withFrameNanos { }
        state.requester(commentInputFocusIndex)?.requestFocusSafely()
    }

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
            .focusEntryGroup(entryFocusRequester)
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val commentsProgressText = if (comments.isNotEmpty()) {
            if (totalComments > 0L) {
                "${comments.size} ${uiText(UiStringKey.Of)} ${localizedViews(totalComments)} ${uiText(UiStringKey.Loaded)}"
            } else {
                "${comments.size} ${uiText(UiStringKey.Loaded)}"
            }
        } else {
            null
        }
        val headerFocusModifier = when {
            focusGridState != null -> Modifier.visualFocusGridItem(
                state = focusGridState,
                index = focusIndexOffset,
                horizontal = true,
                vertical = true,
                blockKey = focusBlockKey,
                blockEntryIndex = focusIndexOffset,
            )
            entryFocusRequester != null -> Modifier.focusRequester(entryFocusRequester)
            else -> Modifier
        }
        AccordionHeader(
            title = uiText(UiStringKey.Comments),
            summary = commentsProgressText.orEmpty(),
            expanded = expanded,
            active = false,
            onClick = { onExpandedChange(!expanded) },
            centerTitle = true,
            modifier = Modifier
                .then(headerFocusModifier),
        )

        if (expanded) {
            if (isAuthorized) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(uiText(UiStringKey.Comment)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (focusGridState != null) {
                                Modifier.visualFocusGridItem(
                                    state = focusGridState,
                                    index = commentInputFocusIndex,
                                    horizontal = true,
                                    vertical = true,
                                    blockKey = focusBlockKey,
                                    blockEntryIndex = commentInputFocusIndex,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .padding(1.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    DialogActionButton(
                        text = uiText(UiStringKey.Send),
                        primary = true,
                        onClick = {
                            val text = draft.trim()
                            if (text.isNotBlank()) {
                                onAddAnimeComment(text)
                                draft = ""
                            }
                        },
                        modifier = Modifier.then(
                            if (focusGridState != null) {
                                Modifier.visualFocusGridItem(
                                    state = focusGridState,
                                    index = commentSendFocusIndex,
                                    horizontal = true,
                                    vertical = true,
                                    blockKey = focusBlockKey,
                                    blockEntryIndex = commentInputFocusIndex,
                                )
                            } else {
                                Modifier
                            },
                        ),
                    )
                }
            }

            comments.forEachIndexed { index, comment ->
                val commentShape = RoundedCornerShape(8.dp)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (focusGridState != null) {
                                Modifier.visualFocusGridItem(
                                    state = focusGridState,
                                    index = commentsStartFocusIndex + index,
                                    horizontal = true,
                                    vertical = true,
                                    blockKey = focusBlockKey,
                                    blockEntryIndex = commentsStartFocusIndex,
                                )
                            } else {
                                Modifier
                            },
                        )
                        .focusRing(commentShape)
                        .focusable(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = commentShape,
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
                                text = comment.userName.ifBlank { uiText(UiStringKey.User) },
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
                        text = uiText(UiStringKey.Retry),
                        primary = true,
                        onClick = onLoadMoreAnimeComments,
                        modifier = Modifier,
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

@Composable
internal fun UserAnimeListMark.localizedTitle(): String = uiText(
    when (this) {
        UserAnimeListMark.Watching -> UiStringKey.Watching
        UserAnimeListMark.Planned -> UiStringKey.Planned
        UserAnimeListMark.Watched -> UiStringKey.Watched
        UserAnimeListMark.Postponed -> UiStringKey.Postponed
        UserAnimeListMark.Dropped -> UiStringKey.Dropped
    },
)

internal fun UserAnimeListMark.siteColor() = when (this) {
    UserAnimeListMark.Watching -> Color(0xFFFF5E66)
    UserAnimeListMark.Planned -> Color(0xFFB66DFF)
    UserAnimeListMark.Watched -> Color(0xFF35D47A)
    UserAnimeListMark.Postponed -> Color(0xFFFFB71B)
    UserAnimeListMark.Dropped -> Color(0xFF9EA3AA)
}

internal val favoriteMarkColor = Color(0xFFC94DDB)
