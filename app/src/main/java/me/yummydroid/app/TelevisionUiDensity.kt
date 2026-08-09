package me.yummydroid.app

import android.content.Context
import android.content.res.Configuration
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import me.yummydroid.app.data.InterfaceScale

private const val ReferenceTelevisionWidthDp = 960
private const val ReferenceTelevisionHeightDp = 540
private const val DensityDefaultDpi = 160

internal data class TelevisionUiDensity(
    val densityDpi: Int,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
)

internal fun resolveTelevisionUiDensity(
    isTelevision: Boolean,
    widthPixels: Int,
    heightPixels: Int,
    currentDensityDpi: Int,
    interfaceScale: InterfaceScale = InterfaceScale.Default,
): TelevisionUiDensity? {
    if (!isTelevision || widthPixels <= 0 || heightPixels <= 0 || currentDensityDpi <= 0) {
        return null
    }

    val referenceScale = min(
        widthPixels.toFloat() / ReferenceTelevisionWidthDp,
        heightPixels.toFloat() / ReferenceTelevisionHeightDp,
    )
    val referenceDensityDpi = (DensityDefaultDpi * referenceScale).roundToInt()
    val standardDensityDpi = max(currentDensityDpi, referenceDensityDpi)
    val normalizedDensityDpi = (standardDensityDpi * interfaceScale.multiplier).roundToInt()
    if (normalizedDensityDpi == currentDensityDpi) return null

    return TelevisionUiDensity(
        densityDpi = normalizedDensityDpi,
        screenWidthDp = (widthPixels * DensityDefaultDpi.toFloat() / normalizedDensityDpi).roundToInt(),
        screenHeightDp = (heightPixels * DensityDefaultDpi.toFloat() / normalizedDensityDpi).roundToInt(),
    )
}

internal fun Context.isTelevisionDevice(): Boolean {
    val configuration = resources.configuration
    return (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION
}

internal fun Context.withNormalizedTelevisionUiDensity(
    interfaceScale: InterfaceScale = InterfaceScale.Default,
): Context {
    val configuration = resources.configuration
    val metrics = resources.displayMetrics
    val normalized = resolveTelevisionUiDensity(
        isTelevision = isTelevisionDevice(),
        widthPixels = metrics.widthPixels,
        heightPixels = metrics.heightPixels,
        currentDensityDpi = configuration.densityDpi,
        interfaceScale = interfaceScale,
    ) ?: return this

    val overrideConfiguration = Configuration(configuration).apply {
        densityDpi = normalized.densityDpi
        screenWidthDp = normalized.screenWidthDp
        screenHeightDp = normalized.screenHeightDp
        smallestScreenWidthDp = min(normalized.screenWidthDp, normalized.screenHeightDp)
    }
    return createConfigurationContext(overrideConfiguration)
}
