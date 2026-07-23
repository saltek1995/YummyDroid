package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.Icons
import androidx.compose.material3.AssistChip
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.data.Anime
import me.yummydroid.app.data.AnimeComment
import me.yummydroid.app.data.AnimeRatingSummary
import me.yummydroid.app.data.AnimeTrailer
import me.yummydroid.app.data.matchingDubbingKey
import me.yummydroid.app.data.matchingDubbingTitle
import me.yummydroid.app.data.RelatedAnime
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatCommentTimestamp
import me.yummydroid.app.formatRating
import me.yummydroid.app.formatViews
import me.yummydroid.app.LoadState
import me.yummydroid.app.PagingUiState
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.components.focusRing
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
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
    stopUpEscape: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    var focusedRating by remember { mutableStateOf<Int?>(null) }
    val previewRating = focusedRating
    val filledRating = previewRating ?: selected
    val fillColor = if (previewRating != null) {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.86f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.90f)
    }
    val filledIconColor = if (previewRating != null) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
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
                        .stopHorizontalFocusEscape(
                            index = value - 1,
                            total = 10,
                            leftExit = leftExitRequester,
                            stopUp = stopUpEscape,
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
                        contentDescription = "${uiText("Оценка")} $value",
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
        val footerHasFocusableAction = commentsPaging.error != null
        val headerIsLastFocusable = !expanded || (!isAuthorized && !footerHasFocusableAction)
        AccordionHeader(
            title = uiText("Комментарии"),
            expanded = expanded,
            active = false,
            onClick = { onExpandedChange(!expanded) },
            trailingText = commentsProgressText,
            modifier = if (headerIsLastFocusable) Modifier.stopDownFocusEscape() else Modifier,
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
                        modifier = if (footerHasFocusableAction) Modifier else Modifier.stopDownFocusEscape(),
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
                        modifier = Modifier.stopDownFocusEscape(),
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
