package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun DetailsHeroMediaCard(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    focusGridState: VisualFocusGridState,
    markMaxWidth: Dp,
    modifier: Modifier,
) {
    var posterViewerOpen by remember(model.details.posterUrl) { mutableStateOf(false) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        DetailsPoster(
            posterUrl = model.details.posterUrl,
            title = model.details.title,
            onClick = model.details.posterUrl
                .takeIf { it.isNotBlank() }
                ?.let { { posterViewerOpen = true } },
            modifier = Modifier
                .fillMaxWidth()
                .visualFocusGridItem(
                    state = focusGridState,
                    index = DetailsHeroFocusIndex.Poster,
                    horizontal = true,
                    vertical = true,
                    blockKey = DetailsFocusBlockKey.HeroPoster,
                    blockEntryIndex = DetailsHeroFocusIndex.Poster,
                ),
        )
        if (model.showMarkPanel) {
            AnimeMarkPanelModern(
                auth = model.auth,
                animeMark = model.animeMark,
                onOpenLogin = actions.onOpenLogin,
                onSelectListMark = actions.onSelectListMark,
                onToggleFavorite = actions.onToggleFavorite,
                focusGridState = focusGridState,
                focusIndexOffset = DetailsHeroFocusIndex.MarkStart,
                focusBlockKey = DetailsFocusBlockKey.HeroMarks,
                maxWidth = markMaxWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (posterViewerOpen) {
        ScreenshotViewerDialog(
            screenshots = listOf(model.details.posterUrl),
            initialIndex = 0,
            onDismiss = { posterViewerOpen = false },
            onRegisterInputActionHandler = actions.onRegisterModalInputActionHandler,
        )
    }
}
