package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class VideoVoicePresentationTest {
    @Test
    fun playerNameIsNotExposedAsVoiceTitle() {
        val video = matchingVideoVariant(dubbing = "Alloha")

        assertEquals("", video.matchingDubbingTitle)
        assertEquals("", video.matchingVoiceKey)
        assertEquals("Voice", video.matchingVoiceTitle)
    }

    @Test
    fun realVoiceTitleIsKeptWhenPlayerIsAlloha() {
        val video = matchingVideoVariant(dubbing = "AniDUB")

        assertEquals("AniDUB", video.matchingDubbingTitle)
        assertEquals("anidub", video.matchingVoiceKey)
    }
}
