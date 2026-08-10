package me.yummydroid.app.ui.theme

import kotlin.test.Test
import kotlin.test.assertSame

class YummyDroidThemeTest {
    @Test
    fun themeEntryPointUsesTheStableDarkScheme() {
        assertSame(YummyDarkColors, yummyDroidColorScheme())
    }
}
