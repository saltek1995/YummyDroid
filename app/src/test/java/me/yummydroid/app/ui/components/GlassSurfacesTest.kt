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
            firstVisibleOffset = 36,
            lastVisibleIndex = 2,
            lastVisibleEndOffset = 964,
            viewportEndOffset = 1000,
        )

        assertEquals(HorizontalScrollEdgeVisibility(backward = false, forward = false), visibility)
    }
}
