package me.yummydroid.app.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GlassSurfacesTest {
    @Test
    fun solidBackdropKeepsOpaqueStyleBounds() {
        val parameters = resolveLiquidGlassBackdropParameters(
            intensity = 1f,
            topFadeFraction = 0f,
            bottomFadeFraction = 0f,
        )

        assertTrue(parameters.startsSolid)
        assertEquals(0.22f, parameters.baseAlpha, 0.0001f)
        assertEquals(0.78f, parameters.tintAlpha, 0.0001f)
        assertEquals(80f, parameters.blurRadiusDp)
        assertEquals(0.01f, parameters.topSolidStop)
        assertEquals(1f, parameters.bottomSolidStop)
    }

    @Test
    fun fadedBackdropClampsInputsAndPreservesOrderedStops() {
        val parameters = resolveLiquidGlassBackdropParameters(
            intensity = 3f,
            topFadeFraction = 1f,
            bottomFadeFraction = 1f,
        )

        assertFalse(parameters.startsSolid)
        assertEquals(1.85f, parameters.intensity)
        assertEquals(0.60f, parameters.topFadeFraction)
        assertEquals(0.75f, parameters.bottomFadeFraction)
        assertEquals(0.24f, parameters.baseAlpha, 0.0001f)
        assertEquals(0.72f, parameters.tintAlpha, 0.0001f)
        assertEquals(34f, parameters.blurRadiusDp)
        assertEquals(0.20f, parameters.topSoftStop, 0.0001f)
        assertEquals(0.60f, parameters.topSolidStop, 0.0001f)
        assertEquals(0.60f, parameters.bottomSolidStop, 0.0001f)
        assertEquals(0.6625f, parameters.bottomSoftStop, 0.0001f)
        assertEquals(0.60f, parameters.middleStop, 0.0001f)
    }

    @Test
    fun negativeInputsUseMinimumSolidBackdropValues() {
        val parameters = resolveLiquidGlassBackdropParameters(
            intensity = -1f,
            topFadeFraction = -1f,
            bottomFadeFraction = -1f,
        )

        assertEquals(0f, parameters.intensity)
        assertTrue(parameters.startsSolid)
        assertEquals(0.16f, parameters.baseAlpha, 0.0001f)
        assertEquals(0.58f, parameters.tintAlpha, 0.0001f)
    }

    @Test
    fun scrollEdgeIgnoresPaddingAfterFullyVisibleLastItem() {
        val visibility = resolveHorizontalScrollEdgeVisibility(
            canScrollBackward = true,
            canScrollForward = true,
            totalItemsCount = 3,
            firstVisibleIndex = 0,
            firstVisibleOffset = -120,
            lastVisibleIndex = 2,
            lastVisibleEndOffset = 900,
            viewportEndOffset = 1000,
            edgeWidthPx = 72f,
        )

        assertEquals(HorizontalScrollEdgeVisibility(backward = true, forward = false), visibility)
    }

    @Test
    fun scrollEdgeShowsCuesForClippedRealItems() {
        val visibility = resolveHorizontalScrollEdgeVisibility(
            canScrollBackward = true,
            canScrollForward = true,
            totalItemsCount = 5,
            firstVisibleIndex = 1,
            firstVisibleOffset = -40,
            lastVisibleIndex = 4,
            lastVisibleEndOffset = 1040,
            viewportEndOffset = 1000,
            edgeWidthPx = 72f,
        )

        assertEquals(HorizontalScrollEdgeVisibility(backward = true, forward = true), visibility)
    }

    @Test
    fun scrollEdgeHidesCuesWhenAllRealItemsAreFullyVisible() {
        val visibility = resolveHorizontalScrollEdgeVisibility(
            canScrollBackward = true,
            canScrollForward = true,
            totalItemsCount = 3,
            firstVisibleIndex = 0,
            firstVisibleOffset = 80,
            lastVisibleIndex = 2,
            lastVisibleEndOffset = 920,
            viewportEndOffset = 1000,
            edgeWidthPx = 72f,
        )

        assertEquals(HorizontalScrollEdgeVisibility(backward = false, forward = false), visibility)
    }

    @Test
    fun scrollEdgeStartsWhenRealItemsEnterEdgeFadeZone() {
        val visibility = resolveHorizontalScrollEdgeVisibility(
            canScrollBackward = true,
            canScrollForward = true,
            totalItemsCount = 5,
            firstVisibleIndex = 1,
            firstVisibleOffset = 24,
            lastVisibleIndex = 3,
            lastVisibleEndOffset = 976,
            viewportEndOffset = 1000,
            edgeWidthPx = 72f,
        )

        assertEquals(true, visibility.backward)
        assertEquals(true, visibility.forward)
        assertEquals(0.7407f, visibility.backwardFraction, 0.0001f)
        assertEquals(0.7407f, visibility.forwardFraction, 0.0001f)
    }

    @Test
    fun scrollEdgeBackwardInsetMovesTheLeftFadeBoundary() {
        val visibility = resolveHorizontalScrollEdgeVisibility(
            canScrollBackward = true,
            canScrollForward = false,
            totalItemsCount = 5,
            firstVisibleIndex = 1,
            firstVisibleOffset = 104,
            lastVisibleIndex = 3,
            lastVisibleEndOffset = 500,
            viewportEndOffset = 1000,
            edgeWidthPx = 128f,
            backwardEdgeInsetPx = 104f,
        )

        assertTrue(visibility.backward)
        assertEquals(1f, visibility.backwardFraction, 0.0001f)
        assertFalse(visibility.forward)
    }

    @Test
    fun scrollEdgeBackwardInsetFadesSymmetricallyAfterBoundary() {
        val visibility = resolveHorizontalScrollEdgeVisibility(
            canScrollBackward = true,
            canScrollForward = true,
            totalItemsCount = 5,
            firstVisibleIndex = 1,
            firstVisibleOffset = 168,
            lastVisibleIndex = 3,
            lastVisibleEndOffset = 936,
            viewportEndOffset = 1000,
            edgeWidthPx = 128f,
            backwardEdgeInsetPx = 104f,
        )

        assertTrue(visibility.backward)
        assertTrue(visibility.forward)
        assertEquals(0.5f, visibility.backwardFraction, 0.0001f)
        assertEquals(0.5f, visibility.forwardFraction, 0.0001f)
    }

    @Test
    fun scrollEdgeAppearsWhenRealItemsTouchPhysicalEdges() {
        val visibility = resolveHorizontalScrollEdgeVisibility(
            canScrollBackward = true,
            canScrollForward = true,
            totalItemsCount = 5,
            firstVisibleIndex = 1,
            firstVisibleOffset = 0,
            lastVisibleIndex = 3,
            lastVisibleEndOffset = 1000,
            viewportEndOffset = 1000,
            edgeWidthPx = 72f,
        )

        assertEquals(HorizontalScrollEdgeVisibility(backward = true, forward = true), visibility)
    }

    @Test
    fun edgeFadeMaskAlphaInterpolatesFromOpaqueToTargetAlpha() {
        assertEquals(1f, edgeFadeMaskAlpha(baseAlpha = 0f, visibilityFraction = 0f), 0.0001f)
        assertEquals(0.5f, edgeFadeMaskAlpha(baseAlpha = 0f, visibilityFraction = 0.5f), 0.0001f)
        assertEquals(0f, edgeFadeMaskAlpha(baseAlpha = 0f, visibilityFraction = 1f), 0.0001f)
        assertEquals(0.62f, edgeFadeMaskAlpha(baseAlpha = 0.62f, visibilityFraction = 1f), 0.0001f)
    }

    @Test
    fun edgeFadeProgressStartsBeforePhysicalClip() {
        assertEquals(0f, edgeFadeProgress(distanceToEdgePx = 96f, fadeWidthPx = 72f), 0.0001f)
        assertEquals(0.15625f, edgeFadeProgress(distanceToEdgePx = 54f, fadeWidthPx = 72f), 0.0001f)
        assertEquals(0.5f, edgeFadeProgress(distanceToEdgePx = 36f, fadeWidthPx = 72f), 0.0001f)
        assertEquals(0.84375f, edgeFadeProgress(distanceToEdgePx = 18f, fadeWidthPx = 72f), 0.0001f)
        assertEquals(1f, edgeFadeProgress(distanceToEdgePx = 0f, fadeWidthPx = 72f), 0.0001f)
        assertEquals(1f, edgeFadeProgress(distanceToEdgePx = -24f, fadeWidthPx = 72f), 0.0001f)
    }

    @Test
    fun edgeFadeColorStopsMirrorLeftAndRightEdges() {
        val startStops = edgeFadeColorStops(visibilityFraction = 1f, fadeFromStart = true)
        val endStops = edgeFadeColorStops(visibilityFraction = 1f, fadeFromStart = false)

        assertEquals(startStops.size, endStops.size)
        startStops.forEachIndexed { index, startStop ->
            val endStop = endStops[endStops.lastIndex - index]
            assertEquals(1f - startStop.first, endStop.first, 0.0001f)
            assertEquals(startStop.second.alpha, endStop.second.alpha, 0.0001f)
        }
    }
}
