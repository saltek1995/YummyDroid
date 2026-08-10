package me.yummydroid.app

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import me.yummydroid.app.data.InterfaceScale

private const val ReferenceTelevisionWidthDp = 960
private const val ReferenceTelevisionHeightDp = 540
private const val DensityDefaultDpi = 160
internal const val AppFontScale = 1f

internal data class AppUiConfiguration(
    val densityDpi: Int,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val fontScale: Float,
)

internal fun resolveBaseUiDensityDpi(
    isTelevision: Boolean,
    widthPixels: Int,
    heightPixels: Int,
    stableDensityDpi: Int,
): Int? {
    if (widthPixels <= 0 || heightPixels <= 0 || stableDensityDpi <= 0) return null
    if (!isTelevision) return stableDensityDpi

    val referenceScale = min(
        widthPixels.toFloat() / ReferenceTelevisionWidthDp,
        heightPixels.toFloat() / ReferenceTelevisionHeightDp,
    )
    return max(stableDensityDpi, (DensityDefaultDpi * referenceScale).roundToInt())
}

internal fun resolveAppUiConfiguration(
    isTelevision: Boolean,
    widthPixels: Int,
    heightPixels: Int,
    currentDensityDpi: Int,
    stableDensityDpi: Int,
    currentFontScale: Float,
    interfaceScale: InterfaceScale = InterfaceScale.Default,
): AppUiConfiguration? {
    if (
        widthPixels <= 0 ||
        heightPixels <= 0 ||
        currentDensityDpi <= 0 ||
        stableDensityDpi <= 0
    ) {
        return null
    }

    val standardDensityDpi = resolveBaseUiDensityDpi(
        isTelevision = isTelevision,
        widthPixels = widthPixels,
        heightPixels = heightPixels,
        stableDensityDpi = stableDensityDpi,
    ) ?: return null
    val normalizedDensityDpi = (standardDensityDpi * interfaceScale.multiplier).roundToInt()
    if (normalizedDensityDpi == currentDensityDpi && currentFontScale == AppFontScale) return null

    return AppUiConfiguration(
        densityDpi = normalizedDensityDpi,
        screenWidthDp = (widthPixels * DensityDefaultDpi.toFloat() / normalizedDensityDpi).roundToInt(),
        screenHeightDp = (heightPixels * DensityDefaultDpi.toFloat() / normalizedDensityDpi).roundToInt(),
        fontScale = AppFontScale,
    )
}

internal fun Context.isTelevisionDevice(): Boolean {
    val configuration = resources.configuration
    return (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION
}

internal fun Context.baseUiDensityDpi(): Int {
    val metrics = resources.displayMetrics
    return resolveBaseUiDensityDpi(
        isTelevision = isTelevisionDevice(),
        widthPixels = metrics.widthPixels,
        heightPixels = metrics.heightPixels,
        stableDensityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE,
    ) ?: resources.configuration.densityDpi.coerceAtLeast(DensityDefaultDpi)
}

internal fun Context.withAppUiConfiguration(
    interfaceScale: InterfaceScale = InterfaceScale.Default,
): Context {
    val configuration = resources.configuration
    val metrics = resources.displayMetrics
    val normalized = resolveAppUiConfiguration(
        isTelevision = isTelevisionDevice(),
        widthPixels = metrics.widthPixels,
        heightPixels = metrics.heightPixels,
        currentDensityDpi = configuration.densityDpi,
        stableDensityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE,
        currentFontScale = configuration.fontScale,
        interfaceScale = interfaceScale,
    ) ?: return this

    val overrideConfiguration = Configuration(configuration).apply {
        densityDpi = normalized.densityDpi
        screenWidthDp = normalized.screenWidthDp
        screenHeightDp = normalized.screenHeightDp
        smallestScreenWidthDp = min(normalized.screenWidthDp, normalized.screenHeightDp)
        fontScale = normalized.fontScale
    }
    return createConfigurationContext(overrideConfiguration)
}
