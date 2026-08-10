package me.yummydroid.app.ui

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AnimeDetailsExtras
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.data.RelatedAnime
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.formatRating
import me.yummydroid.app.LoadState
import me.yummydroid.app.ui.components.dpadClickable
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
                    focusKey = focusBlockKey?.let { "$it:header" },
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
                        val itemFocusKey = detailsRelatedAnimeFocusKey(focusBlockKey, related.id)
                        RelatedAnimeOrderRow(
                            index = index + 1,
                            relatedAnime = related,
                            onClick = { onOpenAnime(related.id, itemFocusKey) },
                            modifier = if (focusGridState != null) {
                                Modifier.visualFocusGridItem(
                                    state = focusGridState,
                                    index = focusIndexOffset + index + 1,
                                    horizontal = false,
                                    vertical = true,
                                    blockKey = focusBlockKey,
                                    blockEntryIndex = focusIndexOffset + index + 1,
                                    consumeDisabledAxis = true,
                                    focusKey = itemFocusKey,
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
        is LoadState.Ready -> DetailsSubscriptionsSection(
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

@Composable
internal fun DetailsRecommendationsSection(
    extrasState: LoadState<AnimeDetailsExtras>,
    onOpenAnime: (Long, Any?) -> Unit,
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
        is LoadState.Ready -> DetailsCommentsSection(
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

internal fun detailsRelatedAnimeFocusKey(blockKey: Any?, animeId: Long): Any? {
    return blockKey?.let { "$it:related:$animeId" }
}
