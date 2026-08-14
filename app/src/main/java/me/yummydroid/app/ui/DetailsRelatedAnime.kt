package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.data.RelatedAnime
import me.yummydroid.app.formatRating
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing
import me.yummydroid.app.ui.theme.YummySurfaceRole
import me.yummydroid.app.ui.theme.yummySurfaceBorder
import me.yummydroid.app.ui.theme.yummySurfaceColor
import me.yummydroid.app.ui.theme.yummySurfaceContentColor

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
