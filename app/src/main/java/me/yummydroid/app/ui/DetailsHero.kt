package me.yummydroid.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// DetailsHeroRuntime
@Composable
internal fun DetailsHeroModern(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    modifier: Modifier = Modifier,
    focusGridState: VisualFocusGridState? = null,
) {
    val localHeroFocusGridState = rememberVisualFocusGridState(
        size = DETAILS_HERO_FOCUS_GRAPH_SIZE,
        key = model.details.id,
        allowLoosePerpendicularMatch = true,
    )
    Box(modifier = modifier) {
        model.details.backdropUrl?.let { backdrop ->
            PosterImage(
                url = backdrop,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(heroBackdropScrim()),
            )
        }
        DetailsHeroSiteLayout(
            model = model,
            actions = actions,
            heroFocusGridState = focusGridState ?: localHeroFocusGridState,
            modifier = Modifier
                .align(if (model.isWide) Alignment.BottomStart else Alignment.TopStart)
                .fillMaxWidth()
                .then(if (!model.isWide) Modifier.statusBarsPadding() else Modifier),
        )
    }
}

@Composable
private fun heroBackdropScrim(): Brush {
    val background = MaterialTheme.colorScheme.background
    return remember(background) {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.18f),
                background.copy(alpha = 0.48f),
                background.copy(alpha = 0.86f),
            ),
        )
    }
}
// DetailsHeroSiteLayout
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

// DetailsHeroInfo
@Composable
internal fun DetailsHeroSiteInfo(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    compact: Boolean,
    isWide: Boolean,
    heroFocusGridState: VisualFocusGridState?,
    modifier: Modifier = Modifier,
) {
    val details = model.details
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(if (compact) 7.dp else 10.dp),
    ) {
        DetailsHeroHeading(
            details = details,
            compact = compact,
            isWide = isWide,
            detailsExtras = model.detailsExtras,
            auth = model.auth,
            showHeroRating = model.showHeroRating,
            onSetAnimeRating = actions.onSetAnimeRating,
            onRegisterModalInputActionHandler = actions.onRegisterModalInputActionHandler,
            interactive = model.interactive,
            heroFocusGridState = heroFocusGridState,
        )
        DetailsHeroActionPanel(
            model = model,
            actions = actions,
            externalPrimaryFocusRequester = heroFocusGridState?.requester(DetailsHeroFocusIndex.PrimaryAction),
            heroFocusGridState = heroFocusGridState,
        )
        DetailsHeroProgressSummary(model.episodeSummary, model.downloadedSummary)
        DetailsHeroFactRows(
            details = details,
            apiEpisodeCount = model.apiEpisodeCount,
            narrow = !isWide,
            compact = compact,
            onGenreFilterSelected = { genre -> actions.onGenreFilterSelected(details.id, genre) },
            onYearFilterSelected = { year -> actions.onYearFilterSelected(details.id, year) },
            onStudioFilterSelected = { studio -> actions.onStudioFilterSelected(details.id, studio) },
            onCreatorFilterSelected = { creator -> actions.onCreatorFilterSelected(details.id, creator) },
            heroFocusGridState = heroFocusGridState,
        )
    }
}

// DetailsHeroMediaCard
@Composable
internal fun DetailsHeroMediaCard(
    model: DetailsHeroModel,
    actions: DetailsHeroActions,
    focusGridState: VisualFocusGridState,
    markMaxWidth: Dp,
    modifier: Modifier,
) {
    var posterViewerOpen by remember(model.details.posterUrl) { mutableStateOf(false) }
    LaunchedEffect(model.interactive) {
        if (!model.interactive) posterViewerOpen = false
    }
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
                    blockedDirections = setOf(VisualGridDirection.Right),
                ),
        )
        if (model.showMarkPanel) {
            AnimeMarkPanelModern(
                auth = model.auth,
                animeMark = model.animeMark,
                onOpenLogin = actions.onOpenLogin,
                onSelectListMark = actions.onSelectListMark,
                onToggleFavorite = actions.onToggleFavorite,
                onRetry = actions.onRetry,
                focusGridState = focusGridState,
                focusIndexOffset = DetailsHeroFocusIndex.MarkStart,
                focusBlockKey = DetailsFocusBlockKey.HeroMarks,
                maxWidth = markMaxWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    if (model.interactive && posterViewerOpen) {
        ScreenshotViewerDialog(
            screenshots = listOf(model.details.posterUrl),
            initialIndex = 0,
            onDismiss = { posterViewerOpen = false },
            onRegisterInputActionHandler = actions.onRegisterModalInputActionHandler,
        )
    }
}
