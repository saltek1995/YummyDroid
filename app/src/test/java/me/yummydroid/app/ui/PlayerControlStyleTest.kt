package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PlayerControlStyleTest {
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
