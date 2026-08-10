package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.PreferredQuality

class DownloadPlanModelTest {
    @Test
    fun explicitQualitiesAreValidatedDeduplicatedAndSorted() {
        val plan = plan(
            preferredQualityName = PreferredQuality.P480.name,
            qualityNames = listOf("invalid", PreferredQuality.P720.name, PreferredQuality.P1080.name, PreferredQuality.P720.name),
        )

        assertEquals(listOf(PreferredQuality.P1080, PreferredQuality.P720), plan.acceptableQualities)
        assertEquals(PreferredQuality.P1080, plan.preferredQuality)
        assertEquals("1080p, 720p", plan.qualityTitle)
    }

    @Test
    fun legacyPreferredQualityIsUsedWhenExplicitListIsEmpty() {
        val plan = plan(preferredQualityName = PreferredQuality.P480.name)

        assertEquals(listOf(PreferredQuality.P480), plan.acceptableQualities)
        assertEquals(PreferredQuality.P480, plan.preferredQuality)
        assertEquals("480p", plan.qualityTitle)
    }

    @Test
    fun invalidLegacyQualityFallsBackToAuto() {
        val plan = plan(preferredQualityName = "invalid")

        assertEquals(listOf(PreferredQuality.Auto), plan.acceptableQualities)
        assertEquals(PreferredQuality.Auto, plan.preferredQuality)
        assertEquals("Auto", plan.qualityTitle)
    }

    private fun plan(
        preferredQualityName: String,
        qualityNames: List<String> = emptyList(),
    ) = DownloadPlan(
        id = "plan",
        animeId = 1,
        animeTitle = "Anime",
        preferredQualityName = preferredQualityName,
        qualityNames = qualityNames,
        onlyMissing = false,
        items = emptyList(),
        createdAtMs = 1,
    )
}
