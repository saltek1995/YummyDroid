package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.yummydroid.app.data.InterfaceScale

class AppUiConfigurationTest {
    @Test
    fun defaultPhoneConfigurationIsNotOverridden() {
        assertNull(
            resolveAppUiConfiguration(
                isTelevision = false,
                widthPixels = 1080,
                heightPixels = 2400,
                currentDensityDpi = 440,
                stableDensityDpi = 440,
                currentFontScale = AppFontScale,
            ),
        )
    }

    @Test
    fun phoneInterfaceScaleChangesDensityAndLogicalSize() {
        assertEquals(
            AppUiConfiguration(
                densityDpi = 528,
                screenWidthDp = 327,
                screenHeightDp = 727,
                fontScale = AppFontScale,
            ),
            resolveAppUiConfiguration(
                isTelevision = false,
                widthPixels = 1080,
                heightPixels = 2400,
                currentDensityDpi = 440,
                stableDensityDpi = 440,
                currentFontScale = AppFontScale,
                interfaceScale = InterfaceScale.fromPercent(120),
            ),
        )
    }

    @Test
    fun systemFontScaleIsAlwaysReset() {
        assertEquals(
            AppUiConfiguration(
                densityDpi = 440,
                screenWidthDp = 393,
                screenHeightDp = 873,
                fontScale = AppFontScale,
            ),
            resolveAppUiConfiguration(
                isTelevision = false,
                widthPixels = 1080,
                heightPixels = 2400,
                currentDensityDpi = 440,
                stableDensityDpi = 440,
                currentFontScale = 1.5f,
            ),
        )
    }

    @Test
    fun systemDisplaySizeIsResetToStableDeviceDensity() {
        assertEquals(
            AppUiConfiguration(
                densityDpi = 440,
                screenWidthDp = 393,
                screenHeightDp = 873,
                fontScale = AppFontScale,
            ),
            resolveAppUiConfiguration(
                isTelevision = false,
                widthPixels = 1080,
                heightPixels = 2400,
                currentDensityDpi = 528,
                stableDensityDpi = 440,
                currentFontScale = AppFontScale,
            ),
        )
    }

    @Test
    fun alreadyNormalized1080pTelevisionKeepsSystemDensity() {
        assertNull(
            resolveAppUiConfiguration(
                isTelevision = true,
                widthPixels = 1920,
                heightPixels = 1080,
                currentDensityDpi = 320,
                stableDensityDpi = 320,
                currentFontScale = AppFontScale,
            ),
        )
    }

    @Test
    fun lowDensity1080pTelevisionUsesReferenceLogicalSize() {
        assertEquals(
            AppUiConfiguration(
                densityDpi = 320,
                screenWidthDp = 960,
                screenHeightDp = 540,
                fontScale = AppFontScale,
            ),
            resolveAppUiConfiguration(
                isTelevision = true,
                widthPixels = 1920,
                heightPixels = 1080,
                currentDensityDpi = 160,
                stableDensityDpi = 160,
                currentFontScale = AppFontScale,
            ),
        )
    }

    @Test
    fun lowDensity4kTelevisionUsesSameReferenceLogicalSize() {
        assertEquals(
            AppUiConfiguration(
                densityDpi = 640,
                screenWidthDp = 960,
                screenHeightDp = 540,
                fontScale = AppFontScale,
            ),
            resolveAppUiConfiguration(
                isTelevision = true,
                widthPixels = 3840,
                heightPixels = 2160,
                currentDensityDpi = 320,
                stableDensityDpi = 320,
                currentFontScale = AppFontScale,
            ),
        )
    }

    @Test
    fun minimumScaleExpandsLogical4kWorkspace() {
        assertEquals(
            AppUiConfiguration(
                densityDpi = 320,
                screenWidthDp = 1920,
                screenHeightDp = 1080,
                fontScale = AppFontScale,
            ),
            resolveAppUiConfiguration(
                isTelevision = true,
                widthPixels = 3840,
                heightPixels = 2160,
                currentDensityDpi = 640,
                stableDensityDpi = 640,
                currentFontScale = AppFontScale,
                interfaceScale = InterfaceScale.fromPercent(50),
            ),
        )
    }

    @Test
    fun maximumScaleReducesLogical4kWorkspace() {
        assertEquals(
            AppUiConfiguration(
                densityDpi = 832,
                screenWidthDp = 738,
                screenHeightDp = 415,
                fontScale = AppFontScale,
            ),
            resolveAppUiConfiguration(
                isTelevision = true,
                widthPixels = 3840,
                heightPixels = 2160,
                currentDensityDpi = 320,
                stableDensityDpi = 320,
                currentFontScale = AppFontScale,
                interfaceScale = InterfaceScale.fromPercent(130),
            ),
        )
    }
}
