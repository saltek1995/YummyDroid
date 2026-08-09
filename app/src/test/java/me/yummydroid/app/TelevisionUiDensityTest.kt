package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.yummydroid.app.data.InterfaceScale

class TelevisionUiDensityTest {
    @Test
    fun alreadyNormalized1080pTelevisionKeepsSystemDensity() {
        assertNull(
            resolveTelevisionUiDensity(
                isTelevision = true,
                widthPixels = 1920,
                heightPixels = 1080,
                currentDensityDpi = 320,
            ),
        )
    }

    @Test
    fun lowDensity1080pTelevisionUsesReferenceLogicalSize() {
        assertEquals(
            TelevisionUiDensity(
                densityDpi = 320,
                screenWidthDp = 960,
                screenHeightDp = 540,
            ),
            resolveTelevisionUiDensity(
                isTelevision = true,
                widthPixels = 1920,
                heightPixels = 1080,
                currentDensityDpi = 160,
            ),
        )
    }

    @Test
    fun lowDensity4kTelevisionUsesSameReferenceLogicalSize() {
        assertEquals(
            TelevisionUiDensity(
                densityDpi = 640,
                screenWidthDp = 960,
                screenHeightDp = 540,
            ),
            resolveTelevisionUiDensity(
                isTelevision = true,
                widthPixels = 3840,
                heightPixels = 2160,
                currentDensityDpi = 320,
            ),
        )
    }

    @Test
    fun properlyConfigured4kTelevisionKeepsSystemDensity() {
        assertNull(
            resolveTelevisionUiDensity(
                isTelevision = true,
                widthPixels = 3840,
                heightPixels = 2160,
                currentDensityDpi = 640,
            ),
        )
    }

    @Test
    fun phoneDensityIsNeverOverridden() {
        assertNull(
            resolveTelevisionUiDensity(
                isTelevision = false,
                widthPixels = 3840,
                heightPixels = 2160,
                currentDensityDpi = 320,
                interfaceScale = InterfaceScale.fromPercent(120),
            ),
        )
    }

    @Test
    fun minimumScaleExpandsLogical4kWorkspace() {
        assertEquals(
            TelevisionUiDensity(
                densityDpi = 320,
                screenWidthDp = 1920,
                screenHeightDp = 1080,
            ),
            resolveTelevisionUiDensity(
                isTelevision = true,
                widthPixels = 3840,
                heightPixels = 2160,
                currentDensityDpi = 640,
                interfaceScale = InterfaceScale.fromPercent(50),
            ),
        )
    }

    @Test
    fun maximumScaleReducesLogical4kWorkspace() {
        assertEquals(
            TelevisionUiDensity(
                densityDpi = 1280,
                screenWidthDp = 480,
                screenHeightDp = 270,
            ),
            resolveTelevisionUiDensity(
                isTelevision = true,
                widthPixels = 3840,
                heightPixels = 2160,
                currentDensityDpi = 320,
                interfaceScale = InterfaceScale.fromPercent(200),
            ),
        )
    }
}
