package me.yummydroid.app.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import me.yummydroid.app.data.VideoVariant

class DetailsSectionsTest {
    @Test
    fun focusKeysIncludeBlockAndAnimeIdentity() {
        assertEquals("related:related:42", detailsRelatedAnimeFocusKey("related", 42L))
        assertEquals("similar:anime:42", detailsAnimeRowFocusKey("similar", 42L))
        assertEquals(null, detailsRelatedAnimeFocusKey(null, 42L))
        assertEquals(null, detailsAnimeRowFocusKey(null, 42L))
    }

    @Test
    fun subscriptionGroupsFollowSiteVoiceOrderAndChooseStableRepresentative() {
        val voiceBFromKodik = video(id = 1L, player = "Kodik", dubbing = "Voice B")
        val voiceA = video(id = 2L, player = "CVH", dubbing = "Voice A")
        val voiceBFromAlloha = video(id = 3L, player = "Alloha", dubbing = "Voice B")

        val groups = listOf(
            voiceBFromKodik,
            voiceA,
            voiceBFromAlloha,
            video(id = 4L, player = "CVH", dubbing = ""),
        ).detailsSubscriptionVoiceGroups()

        assertEquals(2, groups.size)
        assertSame(voiceBFromAlloha, groups[0])
        assertSame(voiceA, groups[1])
    }

    @Test
    fun subscriptionGroupsAreLimitedToEighteenVoices() {
        val videos = (1L..20L).map { id ->
            video(id = id, player = "CVH", dubbing = "Dubbing Voice$id")
        }

        assertEquals(18, videos.detailsSubscriptionVoiceGroups().size)
    }

    @Test
    fun ratingScaleUsesSiteColorThresholds() {
        assertEquals(Color(0xFFFF6666), ratingScaleColorForValue(4))
        assertEquals(Color(0xFFF2B800), ratingScaleColorForValue(5))
        assertEquals(Color(0xFFF2B800), ratingScaleColorForValue(6))
        assertEquals(Color(0xFF3CCE7B), ratingScaleColorForValue(7))
        assertEquals(Color(0xFF3CCE7B), ratingScaleColorForValue(10))
    }

    private fun video(id: Long, player: String, dubbing: String): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10L,
            player = player,
            dubbing = dubbing,
            episode = "1",
            url = "https://example.test/$id",
            index = 1,
            durationSeconds = null,
            views = 0L,
        )
    }
}
