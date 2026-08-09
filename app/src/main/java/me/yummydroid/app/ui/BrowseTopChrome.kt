package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import me.yummydroid.app.AuthUiState
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.R

internal val BrowseChromePhoneHorizontalPadding = 16.dp
internal val BrowseChromeWideHorizontalPadding = 24.dp

@Composable
internal fun BrowseTopBarModern(
    onOpenSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDownloads: () -> Unit,
    auth: AuthUiState,
    activeFilters: Int,
    activeSearch: Boolean,
    activeFiltersPanel: Boolean,
    activeSettings: Boolean,
    activeDownloads: Boolean,
    activeProfile: Boolean,
    activeDownloadCount: Int,
    forcedOfflineMode: Boolean,
    modifier: Modifier = Modifier,
    searchEnabled: Boolean = true,
    filtersEnabled: Boolean = true,
    onOpenLogin: () -> Unit,
    onOpenProfile: () -> Unit,
    isWide: Boolean,
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    activeSectionPosition: Float? = null,
    onSectionSelected: (BrowseSection) -> Unit,
    onExitDown: (() -> Unit)? = null,
    actionsFocusRequester: FocusRequester? = null,
    sectionTabsFocusRequester: FocusRequester? = null,
    sectionTabFocusRequesters: Map<BrowseSection, FocusRequester> = emptyMap(),
    sectionTabsFocusEnabled: Boolean = true,
    showCompactControls: Boolean = true,
    collapseWhenHidden: Boolean = true,
    visible: Boolean = true,
    visibilityProgress: Float? = null,
    visibilityProgressProvider: (() -> Float)? = null,
) {
    val horizontalPadding = if (isWide) {
        BrowseChromeWideHorizontalPadding
    } else {
        BrowseChromePhoneHorizontalPadding
    }
    val stackActions = !isWide && currentWindowSizeDp().width < 360.dp

    if (isWide) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .browseTopBarVisibility(visible, collapseWhenHidden, visibilityProgress, visibilityProgressProvider)
                .browseTopBarExitDown(onExitDown)
                .statusBarsPadding()
                .padding(horizontal = horizontalPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AppWordmark(
                    modifier = Modifier.weight(1f),
                    height = 52.dp,
                )

                if (forcedOfflineMode) {
                    OfflineModeChip()
                }

                BrowseTopBarActions(
                    onOpenSearch = onOpenSearch,
                    onOpenFilters = onOpenFilters,
                    onOpenSettings = onOpenSettings,
                    onOpenDownloads = onOpenDownloads,
                    auth = auth,
                    activeFilters = activeFilters,
                    activeSearch = activeSearch,
                    activeFiltersPanel = activeFiltersPanel,
                    activeSettings = activeSettings,
                    activeDownloads = activeDownloads,
                    activeProfile = activeProfile,
                    activeDownloadCount = activeDownloadCount,
                    searchEnabled = searchEnabled,
                    filtersEnabled = filtersEnabled,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    entryFocusRequester = actionsFocusRequester,
                    downFocusRequester = sectionTabsFocusRequester,
                    consumeUpWhenNoRequester = true,
                    consumeHorizontalEdgesWhenNoRequester = true,
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .browseTopBarVisibility(visible, collapseWhenHidden, visibilityProgress, visibilityProgressProvider)
                .browseTopBarExitDown(onExitDown)
                .padding(horizontal = horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
            )

            if (forcedOfflineMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    OfflineModeChip()
                }
            }

            if (showCompactControls) {
                BrowseSectionTabs(
                    activeSection = activeSection,
                    visibleSections = visibleSections,
                    activeSectionPosition = activeSectionPosition,
                    onSectionSelected = onSectionSelected,
                    sectionFocusRequesters = sectionTabFocusRequesters,
                    focusEnabled = sectionTabsFocusEnabled,
                    modifier = Modifier.fillMaxWidth(),
                )

                BrowseTopBarActions(
                    onOpenSearch = onOpenSearch,
                    onOpenFilters = onOpenFilters,
                    onOpenSettings = onOpenSettings,
                    onOpenDownloads = onOpenDownloads,
                    auth = auth,
                    activeFilters = activeFilters,
                    activeSearch = activeSearch,
                    activeFiltersPanel = activeFiltersPanel,
                    activeSettings = activeSettings,
                    activeDownloads = activeDownloads,
                    activeProfile = activeProfile,
                    activeDownloadCount = activeDownloadCount,
                    searchEnabled = searchEnabled,
                    filtersEnabled = filtersEnabled,
                    onOpenLogin = onOpenLogin,
                    onOpenProfile = onOpenProfile,
                    entryFocusRequester = actionsFocusRequester,
                    modifier = Modifier.fillMaxWidth(),
                    spreadActions = !stackActions,
                    stackActions = stackActions,
                )
            }
        }
    }
}

@Composable
private fun Modifier.browseTopBarVisibility(
    visible: Boolean,
    collapseWhenHidden: Boolean,
    visibilityProgress: Float? = null,
    visibilityProgressProvider: (() -> Float)? = null,
): Modifier {
    val animatedProgress = if (visibilityProgress == null && visibilityProgressProvider == null) {
        val animatedProgress by animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseTopBarVisibility",
        )
        animatedProgress
    } else {
        null
    }
    fun progress(): Float {
        return (visibilityProgressProvider?.invoke() ?: visibilityProgress ?: animatedProgress ?: 0f)
            .coerceIn(0f, 1f)
    }
    return this
        .then(if (visible) Modifier else Modifier.focusProperties { canFocus = false })
        .layout { measurable, constraints ->
            val progress = progress()
            val placeable = measurable.measure(constraints)
            val height = if (collapseWhenHidden) {
                (placeable.height * progress).roundToInt()
            } else {
                placeable.height
            }
            val offsetY = if (collapseWhenHidden) {
                height - placeable.height
            } else {
                ((progress - 1f) * placeable.height).roundToInt()
            }
            layout(width = placeable.width, height = height) {
                placeable.placeRelative(x = 0, y = offsetY)
            }
        }
        .clipToBounds()
        .graphicsLayer { alpha = if (collapseWhenHidden) progress() else 1f }
}

private fun Modifier.browseTopBarExitDown(onExitDown: (() -> Unit)?): Modifier {
    if (onExitDown == null) return this
    return onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
            onExitDown()
            true
        } else {
            false
        }
    }
}

@Composable
internal fun AppWordmark(
    modifier: Modifier = Modifier,
    height: Dp,
) {
    Box(
        modifier = modifier.height(height),
        contentAlignment = Alignment.CenterStart,
    ) {
        Image(
            painter = painterResource(R.drawable.app_wordmark),
            contentDescription = "YummyDroid",
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            modifier = Modifier
                .fillMaxHeight()
                .width(height * 5.45f),
        )
    }
}
