package me.yummydroid.app.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import me.yummydroid.app.data.UserAnimeListMark
import me.yummydroid.app.data.VideoVariant

class DetailsSectionsTest {
    @Test
    fun animeListMarksUseStableLocalizationKeys() {
        assertEquals(UiStringKey.Watching, UserAnimeListMark.Watching.localizedTitleKey())
        assertEquals(UiStringKey.Planned, UserAnimeListMark.Planned.localizedTitleKey())
        assertEquals(UiStringKey.Watched, UserAnimeListMark.Watched.localizedTitleKey())
        assertEquals(UiStringKey.Postponed, UserAnimeListMark.Postponed.localizedTitleKey())
        assertEquals(UiStringKey.Dropped, UserAnimeListMark.Dropped.localizedTitleKey())
    }

    @Test
    fun focusKeysIncludeBlockAndAnimeIdentity() {
        assertEquals("related:related:42", detailsRelatedAnimeFocusKey("related", 42L))
        assertEquals("similar:anime:42", detailsAnimeRowFocusKey("similar", 42L))
        assertEquals(null, detailsRelatedAnimeFocusKey(null, 42L))
        assertEquals(null, detailsAnimeRowFocusKey(null, 42L))
    }

    @Test
    fun subscriptionGroupsFollowSiteVoiceOrderAndKeepSourcesIndependent() {
        val voiceBFromKodik = video(id = 1L, player = "Kodik", dubbing = "Voice B")
        val voiceA = video(id = 2L, player = "CVH", dubbing = "Voice A")
        val voiceBFromAlloha = video(id = 3L, player = "Alloha", dubbing = "Voice B")

        val groups = listOf(
            voiceBFromKodik,
            voiceA,
            voiceBFromAlloha,
            video(id = 4L, player = "CVH", dubbing = ""),
        ).detailsSubscriptionSourceGroups()

        assertEquals(3, groups.size)
        assertEquals(voiceBFromAlloha.id, groups[0].id)
        assertEquals(voiceBFromKodik.id, groups[1].id)
        assertEquals(voiceA.id, groups[2].id)
    }

    @Test
    fun subscriptionGroupIsActiveWhenAnyEpisodeIsSubscribed() {
        val firstEpisode = video(
            id = 1L,
            player = "Alloha",
            dubbing = "Voice A",
            index = 1,
            subscribed = false,
        )
        val subscribedEpisode = video(
            id = 2L,
            player = "Alloha",
            dubbing = "Voice A",
            index = 2,
            subscribed = true,
        )
        val otherSource = video(
            id = 3L,
            player = "Kodik",
            dubbing = "Voice A",
            subscribed = false,
        )

        val groups = listOf(firstEpisode, subscribedEpisode, otherSource)
            .detailsSubscriptionSourceGroups()

        assertEquals(2, groups.size)
        assertEquals(firstEpisode.id, groups[0].id)
        assertTrue(groups[0].subscribed)
        assertFalse(groups[1].subscribed)
    }

    @Test
    fun subscriptionGroupsAreLimitedToEighteenSources() {
        val videos = (1L..20L).map { id ->
            video(id = id, player = "CVH", dubbing = "Dubbing Voice$id")
        }

        assertEquals(18, videos.detailsSubscriptionSourceGroups().size)
    }

    @Test
    fun ratingScaleUsesSiteColorThresholds() {
        assertEquals(Color(0xFFFF6666), ratingScaleColorForValue(4))
        assertEquals(Color(0xFFF2B800), ratingScaleColorForValue(5))
        assertEquals(Color(0xFFF2B800), ratingScaleColorForValue(6))
        assertEquals(Color(0xFF3CCE7B), ratingScaleColorForValue(7))
        assertEquals(Color(0xFF3CCE7B), ratingScaleColorForValue(10))
    }

    private fun video(
        id: Long,
        player: String,
        dubbing: String,
        index: Int = 1,
        subscribed: Boolean = false,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10L,
            player = player,
            dubbing = dubbing,
            episode = "1",
            url = "https://example.test/$id",
            index = index,
            durationSeconds = null,
            views = 0L,
            subscribed = subscribed,
        )
    }
}
