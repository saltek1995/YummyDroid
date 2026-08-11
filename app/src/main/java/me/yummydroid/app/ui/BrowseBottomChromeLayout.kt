package me.yummydroid.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import me.yummydroid.app.ui.components.liquidGlassBackdrop

private val BrowseBottomBarGlassTopFadeHeight = 32.dp
internal val BrowseBottomChromeInteractiveTopPadding = BrowseBottomBarGlassTopFadeHeight + 10.dp
internal val BrowseChromeItemGap = 8.dp
internal val BrowseBottomBaseControlsFallbackHeight = 96.dp
private val BrowseBottomCalendarToTabsGap = BrowseChromeItemGap

@Composable
internal fun BrowseBottomChromeLayout(
    state: BrowseHomeChromeState,
    actions: BrowseActionCallbacks,
    sectionNavigation: BrowseBottomSectionNavigation,
    showSectionTabs: Boolean,
    hazeState: HazeState?,
    topProtectedContent: (@Composable (Modifier) -> Unit)?,
    topProtectedVisibilityProgress: Float?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val geometry = remember { BrowseBottomChromeGeometryState() }
    val actionFocusRequester = remember { FocusRequester() }
    val protectedContent = rememberBrowseBottomProtectedContentState(
        content = topProtectedContent,
        visibilityProgress = topProtectedVisibilityProgress,
    )
    LaunchedEffect(protectedContent.active, showSectionTabs) {
        geometry.clearPointerBlockStart()
    }
    val pointerBlockHeight = geometry.pointerBlockHeight(
        density = density,
        fallbackStart = BrowseBottomChromeInteractiveTopPadding,
    )
    val baseContentHeight = geometry.baseControlsContentHeight(
        density = density,
        contentTopPadding = BrowseBottomChromeInteractiveTopPadding,
    )

    val trackedModifier = with(geometry) { modifier.fillMaxWidth().trackBar() }
    Box(modifier = trackedModifier) {
        BrowseBottomChromeBackdrop(
            hazeState = hazeState,
            modifier = Modifier.matchParentSize(),
        )
        BrowseBottomPointerBlock(
            height = pointerBlockHeight,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        protectedContent.content?.let { content ->
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = baseContentHeight + BrowseBottomCalendarToTabsGap,
                    )
                    .browseBottomTopProtectedVisibility(protectedContent.progress)
                    .run { geometry.run { pointerBlockStartAnchor() } },
            ) {
                content(Modifier.fillMaxWidth())
            }
        }
        BrowseBottomControls(
            state = state,
            actions = actions,
            sectionNavigation = sectionNavigation,
            showSectionTabs = showSectionTabs,
            protectedSlotActive = protectedContent.active,
            actionFocusRequester = actionFocusRequester,
            geometry = geometry,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun BrowseBottomChromeBackdrop(
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.liquidGlassBackdrop(
                shape = RoundedCornerShape(0.dp),
                intensity = 1.12f,
                hazeState = hazeState,
                topFadeFraction = 0.36f,
            ),
    )
}

@Composable
private fun BrowseBottomPointerBlock(
    height: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    if (height <= 0.dp) return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .consumeUnhandledPointerInput(height),
    )
}
