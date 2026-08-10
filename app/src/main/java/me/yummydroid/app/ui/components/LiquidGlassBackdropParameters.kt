package me.yummydroid.app.ui.components

internal data class LiquidGlassBackdropParameters(
    val intensity: Float,
    val topFadeFraction: Float,
    val bottomFadeFraction: Float,
    val startsSolid: Boolean,
    val baseAlpha: Float,
    val tintAlpha: Float,
    val blurRadiusDp: Float,
    val topSoftStop: Float,
    val topSolidStop: Float,
    val bottomSolidStop: Float,
    val bottomSoftStop: Float,
    val middleStop: Float,
)

internal fun resolveLiquidGlassBackdropParameters(
    intensity: Float,
    topFadeFraction: Float,
    bottomFadeFraction: Float,
): LiquidGlassBackdropParameters {
    val resolvedIntensity = intensity.coerceIn(0f, 1.85f)
    val resolvedTopFade = topFadeFraction.coerceIn(0f, 0.60f)
    val resolvedBottomFade = bottomFadeFraction.coerceIn(0f, 0.75f)
    val startsSolid = resolvedTopFade <= 0f
    val baseAlpha = if (startsSolid) {
        (0.22f * resolvedIntensity).coerceIn(0.16f, 0.42f)
    } else {
        (0.14f * resolvedIntensity).coerceIn(0.06f, 0.24f)
    }
    val tintAlpha = if (startsSolid) {
        (0.78f * resolvedIntensity).coerceIn(0.58f, 0.96f)
    } else {
        (0.58f * resolvedIntensity).coerceIn(0.36f, 0.72f)
    }
    val topSoftStop = (resolvedTopFade * 0.45f).coerceAtMost(0.20f)
    val topSolidStop = resolvedTopFade.coerceAtLeast(0.01f)
    val bottomSolidStop = (1f - resolvedBottomFade).coerceIn(topSolidStop, 1f)
    val bottomSoftStop = (1f - resolvedBottomFade * 0.45f).coerceIn(bottomSolidStop, 1f)
    val middleStop = (topSolidStop + (bottomSolidStop - topSolidStop) * 0.56f)
        .coerceIn(topSolidStop, bottomSolidStop)
    return LiquidGlassBackdropParameters(
        intensity = resolvedIntensity,
        topFadeFraction = resolvedTopFade,
        bottomFadeFraction = resolvedBottomFade,
        startsSolid = startsSolid,
        baseAlpha = baseAlpha,
        tintAlpha = tintAlpha,
        blurRadiusDp = if (startsSolid) 80f else 34f,
        topSoftStop = topSoftStop,
        topSolidStop = topSolidStop,
        bottomSolidStop = bottomSolidStop,
        bottomSoftStop = bottomSoftStop,
        middleStop = middleStop,
    )
}
