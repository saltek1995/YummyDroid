package me.yummydroid.app.ui

import android.util.DisplayMetrics
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.yummydroid.app.baseUiDensityDpi

@Composable
internal fun currentWindowSizeDp(): DpSize {
    val containerSize = LocalWindowInfo.current.containerSize
    return with(LocalDensity.current) {
        DpSize(containerSize.width.toDp(), containerSize.height.toDp())
    }
}

@Composable
internal fun currentResponsiveWindowSizeDp(): DpSize {
    val containerSize = LocalWindowInfo.current.containerSize
    return responsiveWindowSizeDp(
        widthPixels = containerSize.width,
        heightPixels = containerSize.height,
        densityDpi = LocalContext.current.baseUiDensityDpi(),
    )
}

internal fun responsiveWindowSizeDp(
    widthPixels: Int,
    heightPixels: Int,
    densityDpi: Int,
): DpSize {
    if (widthPixels <= 0 || heightPixels <= 0 || densityDpi <= 0) return DpSize.Zero
    val dpPerPixel = DisplayMetrics.DENSITY_DEFAULT.toFloat() / densityDpi
    return DpSize(
        width = (widthPixels * dpPerPixel).dp,
        height = (heightPixels * dpPerPixel).dp,
    )
}
