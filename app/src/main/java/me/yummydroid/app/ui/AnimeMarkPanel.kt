package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.UserAnimeMark
import me.yummydroid.app.LoadState
import me.yummydroid.app.readyDataOrNull
import me.yummydroid.app.ui.components.dpadClickable
import me.yummydroid.app.ui.theme.yummyActionBorder
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

@Composable
internal fun AnimeMarkPanelModern(
    auth: AuthUiState,
    animeMark: LoadState<UserAnimeMark?>,
    onOpenLogin: () -> Unit,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
    maxWidth: Dp = 392.dp,
    modifier: Modifier = Modifier,
) {
    val mark = animeMark.readyDataOrNull() ?: UserAnimeMark()
    val selectListMark: (UserAnimeListMark) -> Unit = if (auth.profile == null) {
        { if (!auth.loading) onOpenLogin() }
    } else {
        onSelectListMark
    }
    val toggleFavorite: () -> Unit = if (auth.profile == null) {
        { if (!auth.loading) onOpenLogin() }
    } else {
        onToggleFavorite
    }

    AnimeMarkSegmentedControl(
        mark = mark,
        onSelectListMark = selectListMark,
        onToggleFavorite = toggleFavorite,
        focusGridState = focusGridState,
        focusIndexOffset = focusIndexOffset,
        focusBlockKey = focusBlockKey,
        maxWidth = maxWidth,
        modifier = modifier,
    )
}

@Composable
internal fun AnimeMarkSegmentedControl(
    mark: UserAnimeMark,
    onSelectListMark: (UserAnimeListMark) -> Unit,
    onToggleFavorite: () -> Unit,
    focusGridState: VisualFocusGridState? = null,
    focusIndexOffset: Int = 0,
    focusBlockKey: Any? = null,
    maxWidth: Dp = 392.dp,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    val listMarks = UserAnimeListMark.displayOrder
    val totalMarks = listMarks.size + 1
    val internalFocusGridState = rememberVisualFocusGridState(size = totalMarks)
    val effectiveFocusGridState = focusGridState ?: internalFocusGridState
    val effectiveFocusIndexOffset = if (focusGridState == null) 0 else focusIndexOffset
    Surface(
        modifier = modifier.widthIn(max = maxWidth),
        color = yummyActionSurfaceColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = yummyActionBorder(),
        shape = shape,
    ) {
        Row(
            modifier = Modifier
                .height(48.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listMarks.forEachIndexed { index, listMark ->
                AnimeMarkSegment(
                    icon = listMark.icon(),
                    title = listMark.localizedTitle(),
                    color = listMark.siteColor(),
                    selected = mark.list == listMark,
                    onClick = { onSelectListMark(listMark) },
                    index = index,
                    total = totalMarks,
                    focusIndex = effectiveFocusIndexOffset + index,
                    focusGridState = effectiveFocusGridState,
                    focusBlockKey = focusBlockKey,
                    focusBlockEntryIndex = effectiveFocusIndexOffset,
                    modifier = Modifier.weight(1f),
                )
                MarkDivider()
            }
            AnimeMarkSegment(
                icon = Icons.Default.Favorite,
                title = uiText(UiStringKey.Favorites),
                color = favoriteMarkColor,
                selected = mark.isFavorite,
                onClick = onToggleFavorite,
                index = totalMarks - 1,
                total = totalMarks,
                focusIndex = effectiveFocusIndexOffset + totalMarks - 1,
                focusGridState = effectiveFocusGridState,
                focusBlockKey = focusBlockKey,
                focusBlockEntryIndex = effectiveFocusIndexOffset,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun AnimeMarkSegment(
    icon: ImageVector,
    title: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    index: Int = -1,
    total: Int = 0,
    focusIndex: Int = index,
    focusGridState: VisualFocusGridState? = null,
    focusBlockKey: Any? = null,
    focusBlockEntryIndex: Int = focusIndex,
) {
    val shape = RoundedCornerShape(6.dp)
    val focusModifier = if (focusGridState != null && index in 0 until total && focusIndex >= 0) {
        Modifier.visualFocusGridItem(
            state = focusGridState,
            index = focusIndex,
            horizontal = true,
            vertical = focusBlockKey != null,
            blockKey = focusBlockKey,
            blockEntryIndex = focusBlockEntryIndex,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .fillMaxHeight()
            .then(focusModifier)
            .background(if (selected) color else Color.Transparent)
            .dpadClickable(shape, onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = if (selected) Color.White else color,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
internal fun MarkDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
    )
}
