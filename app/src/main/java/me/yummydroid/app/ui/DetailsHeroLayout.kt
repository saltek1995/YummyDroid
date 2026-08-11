package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class DetailsHeroLayoutGeometry(
    val expanded: Boolean,
    val compact: Boolean,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val gap: Dp,
    val posterWidth: Dp,
    val markMaxWidth: Dp,
)

internal fun resolveDetailsHeroLayoutGeometry(
    maxWidth: Dp,
    windowHeight: Dp,
    responsiveWidth: Dp = maxWidth,
    responsiveHeight: Dp = windowHeight,
): DetailsHeroLayoutGeometry {
    val expanded = responsiveWidth > 700.dp
    val compact = responsiveWidth <= 500.dp || responsiveHeight <= 500.dp
    val horizontalPadding = if (expanded) 24.dp else 18.dp
    val verticalPadding = when {
        !expanded -> 14.dp
        compact -> 14.dp
        else -> 22.dp
    }
    val posterWidth = if (expanded) 264.dp.coerceAtMost(maxWidth * 0.34f) else maxWidth
    return DetailsHeroLayoutGeometry(
        expanded = expanded,
        compact = compact,
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        gap = 10.dp,
        posterWidth = posterWidth,
        markMaxWidth = if (expanded) posterWidth else maxWidth,
    )
}

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

@Composable
private fun DetailsHeroMediaCard(
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
