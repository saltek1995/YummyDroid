package me.yummydroid.app.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.PreferredQuality

class NativeVideoPlayerTest {
    @Test
    fun resolvedStreamQualityTakesPriority() {
        val selection = resolvePlaybackQualitySelection(
            resolvedSourceKey = "height:720",
            qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
            trackOptions = emptyList(),
            playbackPreferredQuality = PreferredQuality.P1080,
            defaultQuality = PreferredQuality.Auto,
            actualQualityKey = "height:1080",
        )

        assertEquals("height:720", selection.key)
        assertFalse(selection.shouldUpdateDisplayMode)
    }

    @Test
    fun explicitPlaybackPreferenceTakesPriorityOverDefault() {
        val selection = resolvePlaybackQualitySelection(
            resolvedSourceKey = null,
            qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
            trackOptions = emptyList(),
            playbackPreferredQuality = PreferredQuality.P720,
            defaultQuality = PreferredQuality.P1080,
            actualQualityKey = "height:1080",
        )

        assertEquals("height:720", selection.key)
        assertFalse(selection.shouldUpdateDisplayMode)
    }

    @Test
    fun actualTrackQualityIsUsedInAutomaticMode() {
        val selection = resolvePlaybackQualitySelection(
            resolvedSourceKey = null,
            qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
            trackOptions = listOf(qualityOption(720)),
            playbackPreferredQuality = PreferredQuality.Auto,
            defaultQuality = PreferredQuality.Auto,
            actualQualityKey = "720p",
        )

        assertEquals("height:720", selection.key)
        assertTrue(selection.shouldUpdateDisplayMode)
    }

    @Test
    fun initialQualityKeepsLocalSelectionAheadOfResolvedAndPreferredQuality() {
        assertEquals(
            "local:1080",
            resolveInitialNativeQualityKey(
                selectedLocalQualityKey = "local:1080",
                streamSelectedQualityKey = "height:720",
                qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
                playbackPreferredQuality = PreferredQuality.P1080,
                defaultQuality = PreferredQuality.Auto,
            ),
        )
    }

    @Test
    fun initialQualityIgnoresUnavailableResolvedHeight() {
        assertEquals(
            "height:1080",
            resolveInitialNativeQualityKey(
                selectedLocalQualityKey = null,
                streamSelectedQualityKey = "height:480",
                qualityOptions = listOf(qualityOption(1080), qualityOption(720)),
                playbackPreferredQuality = PreferredQuality.P1080,
                defaultQuality = PreferredQuality.Auto,
            ),
        )
    }

    private fun qualityOption(height: Int): QualityOption {
        return QualityOption(
            group = null,
            trackIndex = 0,
            label = "${height}p",
            height = height,
            bitrate = 0,
            key = "height:$height",
            preferredQuality = PreferredQuality.fromHeight(height),
        )
    }
}
