package me.yummydroid.app.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class YummySpacingTest {
    @Test
    fun spacingScaleRemainsMonotonic() {
        assertEquals(
            listOf(2.dp, 4.dp, 8.dp, 12.dp, 16.dp, 24.dp),
            listOf(
                YummySpacing.xxs,
                YummySpacing.xs,
                YummySpacing.sm,
                YummySpacing.md,
                YummySpacing.lg,
                YummySpacing.xl,
            ),
        )
    }
}
