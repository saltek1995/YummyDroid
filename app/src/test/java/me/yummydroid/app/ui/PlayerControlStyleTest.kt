package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

class PlayerControlStyleTest {
    @Test
    fun controlColorsAreCreatedAfterPaletteInitialization() {
        assertNotSame(playerControlContentColors(active = false), playerControlContentColors(active = false))
        assertNotSame(playerControlContentColors(active = true), playerControlContentColors(active = true))
        assertEquals(0xFFFFB454.toInt(), PLAYER_ACCENT_COLOR)
        assertEquals(0xFF1B1305.toInt(), PLAYER_ACCENT_CONTENT_COLOR)
        assertEquals(0xFFF3F6FA.toInt(), PLAYER_CONTROL_CONTENT_COLOR)
    }

    @Test
    fun qualityControlUsesSelectedResolutionHeight() {
        val options = listOf(
            qualityOption(label = "Full HD", height = 1080, key = "source:1080"),
            qualityOption(label = "HD", height = 720, key = "source:720"),
        )

        assertEquals("720p", options.selectedQualityControlText("source:720"))
    }

    @Test
    fun qualityControlExtractsResolutionFromSelectedLabel() {
        val options = listOf(
            qualityOption(label = "Adaptive 1080p stream", height = 0, key = "adaptive:1080"),
        )

        assertEquals("1080p", options.selectedQualityControlText("adaptive:1080"))
    }

    @Test
    fun qualityControlFallsBackToAutoForAdaptiveOrMissingSelection() {
        assertEquals(PLAYER_AUTO_QUALITY_LABEL, emptyList<QualityOption>().selectedQualityControlText("adaptive"))
        assertEquals(PLAYER_AUTO_QUALITY_LABEL, emptyList<QualityOption>().selectedQualityControlText(null))
    }

    private fun qualityOption(
        label: String,
        height: Int,
        key: String,
    ): QualityOption {
        return QualityOption(
            group = null,
            trackIndex = 0,
            label = label,
            height = height,
            bitrate = 0,
            key = key,
        )
    }
}
