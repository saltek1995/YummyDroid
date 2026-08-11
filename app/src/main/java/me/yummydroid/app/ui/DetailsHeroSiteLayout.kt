package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

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
