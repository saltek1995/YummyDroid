package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadVoiceKeyTest {
    @Test
    fun downloadVoiceKeyFallsBackToSourceGroupWhenVoiceIsUnknown() {
        val video = matchingVideoVariant(dubbing = "Alloha")

        assertEquals("", video.matchingVoiceKey)
        assertEquals(video.groupKey.lowercase(), video.downloadPlanVoiceKey)
    }
}
