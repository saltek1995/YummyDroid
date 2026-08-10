package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.PreferredQuality

class DownloadPlanItemTest {
    @Test
    fun preferredQualityUsesStoredEnumName() {
        assertEquals(PreferredQuality.P1080, item(PreferredQuality.P1080.name).preferredQuality)
    }

    @Test
    fun preferredQualityFallsBackToAutoForUnknownName() {
        assertEquals(PreferredQuality.Auto, item("invalid").preferredQuality)
    }

    private fun item(qualityName: String) = DownloadPlanItem(
        episodeKey = "1",
        episodeTitle = "Episode 1",
        videoId = 1,
        voiceKey = "voice",
        voiceTitle = "Voice",
        groupKey = "group",
        qualityName = qualityName,
    )
}
