package me.yummydroid.app.ui

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
