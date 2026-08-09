package me.yummydroid.app

import android.content.Context
import android.content.res.Configuration
import kotlin.math.min
import kotlin.math.roundToInt

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
): TelevisionUiDensity? {
    if (!isTelevision || widthPixels <= 0 || heightPixels <= 0 || currentDensityDpi <= 0) {
        return null
    }

    val referenceScale = min(
        widthPixels.toFloat() / ReferenceTelevisionWidthDp,
        heightPixels.toFloat() / ReferenceTelevisionHeightDp,
    )
    val normalizedDensityDpi = (DensityDefaultDpi * referenceScale).roundToInt()
    if (normalizedDensityDpi <= currentDensityDpi) return null

    return TelevisionUiDensity(
        densityDpi = normalizedDensityDpi,
        screenWidthDp = (widthPixels * DensityDefaultDpi.toFloat() / normalizedDensityDpi).roundToInt(),
        screenHeightDp = (heightPixels * DensityDefaultDpi.toFloat() / normalizedDensityDpi).roundToInt(),
    )
}

internal fun Context.withNormalizedTelevisionUiDensity(): Context {
    val configuration = resources.configuration
    val isTelevision = (configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) ==
        Configuration.UI_MODE_TYPE_TELEVISION
    val metrics = resources.displayMetrics
    val normalized = resolveTelevisionUiDensity(
        isTelevision = isTelevision,
        widthPixels = metrics.widthPixels,
        heightPixels = metrics.heightPixels,
        currentDensityDpi = configuration.densityDpi,
    ) ?: return this

    val overrideConfiguration = Configuration(configuration).apply {
        densityDpi = normalized.densityDpi
        screenWidthDp = normalized.screenWidthDp
        screenHeightDp = normalized.screenHeightDp
        smallestScreenWidthDp = min(normalized.screenWidthDp, normalized.screenHeightDp)
    }
    return createConfigurationContext(overrideConfiguration)
}
