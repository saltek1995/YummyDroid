package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import me.yummydroid.app.data.Anime
import me.yummydroid.app.ui.theme.YummyColors
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.YummySpacing

@Composable
internal fun AnimeCard(
    anime: Anime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    var localFocused by remember { mutableStateOf(false) }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused = focused ?: localFocused
    val expanded = isFocused || isPressed

    Box(
        modifier = modifier
            .zIndex(if (expanded) 8f else 0f)
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (focused == null) {
                    localFocused = state.isFocused || state.hasFocus
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        AnimeCardSurface(
            anime = anime,
            expanded = false,
            focused = isFocused,
            modifier = Modifier.fillMaxWidth(),
        )

        if (expanded) {
            AnimeCardSurface(
                anime = anime,
                expanded = true,
                focused = isFocused,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints.copy(minHeight = 0))
                        layout(placeable.width, 0) {
                            placeable.place(0, 0)
                        }
                    },
            )
        }
    }
}

@Composable
internal fun AnimeCardSurface(
    anime: Anime,
    expanded: Boolean,
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = YummyRadii.smallShape
    ElevatedCard(
        modifier = modifier.then(
            if (focused) {
                Modifier.border(BorderStroke(3.dp, YummyColors.focus), shape)
            } else {
                Modifier
            },
        ),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = shape,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(topStart = YummyRadii.small, topEnd = YummyRadii.small))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            PosterImage(
                url = anime.posterUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
            )

            if (anime.rating != null || anime.views > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(YummySpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(YummySpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    anime.rating?.let { rating ->
                        RatingBadge(rating = rating, modifier = Modifier.widthIn(min = 62.dp))
                    }

                    if (anime.views > 0) {
                        ViewsBadge(
                            views = anime.views,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(max = 128.dp),
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expanded) Modifier.heightIn(min = 92.dp) else Modifier.height(92.dp))
                .padding(horizontal = YummySpacing.md, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = if (expanded) 8 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            )

            Text(
                text = anime.meta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp),
            )
        }
    }
}
