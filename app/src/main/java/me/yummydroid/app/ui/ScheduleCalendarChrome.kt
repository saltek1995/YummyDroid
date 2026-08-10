package me.yummydroid.app.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.yummydroid.app.ui.theme.YummyRadii
import me.yummydroid.app.ui.theme.yummyActionContentColor
import me.yummydroid.app.ui.theme.yummyActionSurfaceColor

internal fun Modifier.scheduleCalendarStickyMonthMask(maskStartPx: Float): Modifier {
    if (maskStartPx <= 0.5f) return this
    return drawWithContent {
        val left = maskStartPx.coerceIn(0f, size.width)
        if (left >= size.width) return@drawWithContent
        clipRect(left = left) {
            this@drawWithContent.drawContent()
        }
    }
}

@Composable
internal fun ScheduleCalendarMonthStrip(
    monthOverlay: ScheduleCalendarMonthOverlay?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val resolvedMonthOverlay = monthOverlay ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ScheduleDayTileHeight)
            .clipToBounds(),
    ) {
        resolvedMonthOverlay.chips.forEach { chip ->
            ScheduleMonthInlineChip(
                title = chip.title,
                modifier = Modifier.offset(
                    x = with(density) { chip.offsetPx.toDp() },
                ),
            )
        }
    }
}

@Composable
internal fun ScheduleMonthInlineChip(
    title: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(ScheduleMonthInlineLabelWidth)
            .height(ScheduleDayTileHeight),
        color = yummyActionSurfaceColor(),
        contentColor = yummyActionContentColor(),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth(0.72f)
                    .height(ScheduleMonthInlineLabelAccentHeight)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = YummyRadii.pillShape,
                    ),
            )
        }
    }
}

internal val ScheduleDayTileWidth = 96.dp
internal val ScheduleDayTileHeight = 78.dp
internal val ScheduleDayTilePhoneGap = BrowseChromeItemGap
internal val ScheduleDayTileWideGap = BrowseChromeItemGap
internal val ScheduleCalendarOuterHorizontalPadding = 0.dp
internal val ScheduleCalendarHorizontalPadding = 0.dp
internal val ScheduleMonthInlineLabelWidth = ScheduleDayTileWidth
private val ScheduleMonthInlineLabelAccentHeight = 2.dp
internal val ScheduleCalendarPhoneBottomPadding = 0.dp
internal val ScheduleCalendarWideBottomPadding = 0.dp
internal val ScheduleCalendarTopGap = 0.dp

@OptIn(ExperimentalFoundationApi::class)
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal val ScheduleCalendarBringIntoViewSpec = object : BringIntoViewSpec {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(
        durationMillis = 420,
        easing = FastOutSlowInEasing,
    )

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
}
