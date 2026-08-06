package me.yummydroid.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal val BrowseTvScheduleBlockGap = 10.dp
internal val BrowseGridTopContentPadding = 12.dp
internal val BrowseFocusedCardBottomGap = 20.dp
private const val BrowseTouchBounceOverscrollResistance = 0.48f

internal val BrowseGridHorizontalGap = 18.dp
internal val BrowseGridVerticalGap = 22.dp

internal fun browseGridHorizontalContentPadding(maxWidth: Dp): Dp {
    return if (maxWidth >= 720.dp) {
        BrowseChromeWideHorizontalPadding
    } else {
        BrowseChromePhoneHorizontalPadding
    }
}

internal fun browseGridItemHeight(
    maxWidth: Dp,
    columns: Int,
    horizontalPadding: Dp,
): Dp {
    if (columns <= 0 || maxWidth <= 0.dp) return 0.dp
    val horizontalGaps = BrowseGridHorizontalGap * (columns - 1).coerceAtLeast(0).toFloat()
    val itemWidth = ((maxWidth - horizontalPadding * 2f - horizontalGaps) / columns.toFloat())
        .coerceAtLeast(0.dp)
    return itemWidth / AnimeCardPosterAspectRatio
}

internal fun browseGridFocusedCardTopInset(
    contentTopPadding: Dp,
    maxWidth: Dp,
): Dp {
    if (contentTopPadding <= 0.dp) return 0.dp
    return if (maxWidth >= 720.dp) {
        BrowseTvSectionIndicatorHeight + BrowseFocusedCardBottomGap
    } else {
        contentTopPadding
    }
}

internal fun browseGridFocusedCardBottomPadding(
    maxWidth: Dp,
    maxHeight: Dp,
    columns: Int,
    horizontalPadding: Dp,
    topInset: Dp,
    bottomInset: Dp,
    basePadding: Dp,
): Dp {
    if (columns <= 0 || maxWidth <= 0.dp || maxHeight <= 0.dp) return basePadding
    val itemHeight = browseGridItemHeight(
        maxWidth = maxWidth,
        columns = columns,
        horizontalPadding = horizontalPadding,
    )
    val safeHeight = (maxHeight - topInset - bottomInset).coerceAtLeast(0.dp)
    if (itemHeight <= 0.dp || safeHeight <= 0.dp) return basePadding

    val targetCenter = topInset + safeHeight / 2f
    val requiredPadding = maxHeight - targetCenter - itemHeight / 2f
    return maxOf(basePadding, requiredPadding.coerceAtLeast(0.dp))
}

@Composable
internal fun Modifier.browseTouchBounceOverscroll(
    enabled: Boolean,
    gridState: LazyGridState,
): Modifier {
    if (!enabled) return this

    val scope = rememberCoroutineScope()
    val offsetPx = remember { mutableFloatStateOf(0f) }
    val reboundJobRef = remember { arrayOfNulls<Job>(1) }
    val reboundSpec = remember {
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }

    fun cancelRebound() {
        reboundJobRef[0]?.cancel()
        reboundJobRef[0] = null
    }

    fun startRebound() {
        val start = offsetPx.floatValue
        if (abs(start) <= 0.5f) {
            offsetPx.floatValue = 0f
            return
        }
        cancelRebound()
        reboundJobRef[0] = scope.launch {
            val animatable = Animatable(start)
            animatable.animateTo(0f, reboundSpec) {
                offsetPx.floatValue = value
            }
            offsetPx.floatValue = 0f
        }
    }

    fun consumePull(deltaY: Float): Float {
        if (deltaY == 0f) return 0f
        val current = offsetPx.floatValue
        val pullingPastTop = deltaY > 0f && !gridState.canScrollBackward
        val pullingPastBottom = deltaY < 0f && !gridState.canScrollForward
        if (!pullingPastTop && !pullingPastBottom) return 0f

        cancelRebound()
        offsetPx.floatValue = current + deltaY * BrowseTouchBounceOverscrollResistance
        return deltaY
    }

    fun consumeReturn(deltaY: Float): Float {
        val current = offsetPx.floatValue
        if (current == 0f || deltaY == 0f) return 0f
        val returnsFromTop = current > 0f && deltaY < 0f
        val returnsFromBottom = current < 0f && deltaY > 0f
        if (!returnsFromTop && !returnsFromBottom) return 0f

        cancelRebound()
        val proposed = current + deltaY
        val consumed = when {
            current > 0f && proposed < 0f -> -current
            current < 0f && proposed > 0f -> -current
            else -> deltaY
        }
        offsetPx.floatValue = current + consumed
        return consumed
    }

    val connection = remember(gridState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val consumedY = consumeReturn(available.y)
                return if (consumedY != 0f) Offset(x = 0f, y = consumedY) else Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val consumedY = consumePull(available.y)
                return if (consumedY != 0f) Offset(x = 0f, y = consumedY) else Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetPx.floatValue == 0f) return Velocity.Zero
                startRebound()
                return Velocity(x = 0f, y = available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (offsetPx.floatValue == 0f) return Velocity.Zero
                startRebound()
                return Velocity(x = 0f, y = available.y)
            }
        }
    }

    return this
        .nestedScroll(connection)
        .graphicsLayer {
            translationY = offsetPx.floatValue
        }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun BrowseGridScrollLocalProvider(
    touchOverscrollEnabled: Boolean,
    content: @Composable () -> Unit,
) {
    if (touchOverscrollEnabled) {
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides BrowseGridNoopBringIntoViewSpec,
            content = content,
        )
    } else {
        CompositionLocalProvider(
            LocalBringIntoViewSpec provides BrowseGridNoopBringIntoViewSpec,
            LocalOverscrollFactory provides null,
            content = content,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
private val BrowseGridNoopBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(durationMillis = 0)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
