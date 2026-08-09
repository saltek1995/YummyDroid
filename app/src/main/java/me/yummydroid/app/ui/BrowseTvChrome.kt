package me.yummydroid.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.yummydroid.app.BrowseSection
import me.yummydroid.app.ui.components.liquidGlassBackdrop
import me.yummydroid.app.ui.theme.YummySpacing

internal val BrowseTvSectionIndicatorHeight = 56.dp
private val BrowseTvSectionIndicatorGlassExtraHeight = 96.dp
private const val BrowseTvSectionIndicatorGlassIntensity = 1.85f
private val BrowseTvSectionIndicatorHorizontalPadding = 24.dp

@Composable
internal fun BrowseTvSectionIndicatorBar(
    activeSection: BrowseSection,
    visibleSections: List<BrowseSection>,
    modifier: Modifier = Modifier,
    activeSectionPosition: Float? = null,
    onSectionSelected: (BrowseSection) -> Unit,
    sectionFocusRequesters: Map<BrowseSection, FocusRequester> = emptyMap(),
    onExitUp: (() -> Boolean)? = null,
    onExitDown: (() -> Boolean)? = null,
    drawBackdrop: Boolean = true,
    backdropVisible: Boolean = true,
    backdropProgress: Float? = null,
    backdropProgressProvider: (() -> Float)? = null,
    sectionTabsFocusEnabled: Boolean = true,
    squareTopCorners: Boolean = true,
    hazeState: HazeState? = null,
) {
    val animatedBackdropAlpha = if (drawBackdrop && backdropProgress == null && backdropProgressProvider == null) {
        val animatedBackdropAlpha by animateFloatAsState(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
            label = "browseTabsBackdropAlpha",
        )
        animatedBackdropAlpha
    } else {
        null
    }
    fun backdropAlpha(): Float {
        if (!drawBackdrop) return 0f
        return (backdropProgressProvider?.invoke()
            ?: backdropProgress
            ?: animatedBackdropAlpha
            ?: if (backdropVisible) 1f else 0f)
            .coerceIn(0f, 1f)
    }
    val barHeight = if (drawBackdrop) {
        BrowseTvSectionIndicatorHeight + BrowseTvSectionIndicatorGlassExtraHeight
    } else {
        BrowseSectionTabsHeight
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight),
    ) {
        if (drawBackdrop && (backdropVisible || backdropProgress != null || backdropProgressProvider != null)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = backdropAlpha() }
                    .liquidGlassBackdrop(
                        shape = RoundedCornerShape(0.dp),
                        intensity = BrowseTvSectionIndicatorGlassIntensity,
                        hazeState = hazeState,
                        topFadeFraction = 0f,
                        bottomFadeFraction = 0.56f,
                    ),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BrowseSectionTabsHeight),
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.Top,
            ) {
                Spacer(
                    modifier = Modifier
                        .width(BrowseTvSectionIndicatorHorizontalPadding)
                        .fillMaxHeight()
                        .consumeUnhandledPointerInput("tv-tabs-left-edge"),
                )
                visibleSections.forEachIndexed { index, _ ->
                    Spacer(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    if (index < visibleSections.lastIndex) {
                        Spacer(
                            modifier = Modifier
                                .width(YummySpacing.md)
                                .fillMaxHeight()
                                .consumeUnhandledPointerInput("tv-tabs-gap-$index"),
                        )
                    }
                }
                Spacer(
                    modifier = Modifier
                        .width(BrowseTvSectionIndicatorHorizontalPadding)
                        .fillMaxHeight()
                        .consumeUnhandledPointerInput("tv-tabs-right-edge"),
                )
            }
        }
        BrowseSectionTabs(
            activeSection = activeSection,
            visibleSections = visibleSections,
            activeSectionPosition = activeSectionPosition,
            onSectionSelected = onSectionSelected,
            sectionFocusRequesters = sectionFocusRequesters,
            onExitUp = onExitUp,
            onExitDown = onExitDown,
            squareTopCorners = squareTopCorners,
            focusEnabled = sectionTabsFocusEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = BrowseTvSectionIndicatorHorizontalPadding,
                    end = BrowseTvSectionIndicatorHorizontalPadding,
                ),
        )
    }
}
