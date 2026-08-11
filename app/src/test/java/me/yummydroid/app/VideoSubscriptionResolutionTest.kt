package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import me.yummydroid.app.data.VideoSubscription
import me.yummydroid.app.data.VideoSubscriptionHint
import me.yummydroid.app.data.VideoVariant
import me.yummydroid.app.ui.profileSubscriptionsForManagement

class VideoSubscriptionResolutionTest {
    @Test
    fun voiceHintsMatchAnimeVoiceAndPlayer() {
        val subscription = subscription(
            animeId = 10,
            player = "Alloha",
            dubbing = "MiraiDUB",
            playerId = 44,
        )
        val hints = listOf(
            hint(animeId = 10, playerId = 44, voiceKey = "miraidub", voiceTitle = "MiraiDUB"),
            hint(animeId = 10, playerId = 44, voiceKey = "miraidub", voiceTitle = "Duplicate"),
            hint(animeId = 10, playerId = 45, voiceKey = "miraidub", voiceTitle = "Wrong player"),
            hint(animeId = 11, playerId = 44, voiceKey = "miraidub", voiceTitle = "Wrong anime"),
        )

        val resolvedHints = subscription.resolveVoiceHints(hints)

        assertEquals(listOf("MiraiDUB"), resolvedHints.map { it.voiceTitle })
    }

    @Test
    fun canonicalizeSubscriptionsExpandsHintedVoiceToAvailableSources() {
        val videos = listOf(
            video(id = 1, player = "Alloha", playerId = 7, dubbing = "MiraiDUB"),
            video(id = 2, player = "CVH", playerId = 9, dubbing = "MiraiDUB"),
            video(id = 3, player = "CVH", playerId = 9, dubbing = "AniDUB"),
        )
        val subscriptions = listOf(
            subscription(animeId = 10, player = "Alloha", dubbing = "", playerId = 7),
        )
        val hints = listOf(
            hint(animeId = 10, playerId = 7, voiceKey = "miraidub", voiceTitle = "MiraiDUB"),
        )

        val canonical = canonicalizeVideoSubscriptionsForVideos(
            subscriptions = subscriptions,
            videos = videos,
            hints = hints,
            title = "Anime",
            posterUrl = "poster.jpg",
        )

        assertEquals(setOf(1L, 2L), canonical.map { it.videoId }.toSet())
        assertEquals(setOf("MiraiDUB"), canonical.map { it.dubbing }.toSet())
    }

    @Test
    fun unsubscribeTargetResolvesVoiceFromCurrentSubscriptionByVideoId() {
        val currentSubscriptions = listOf(
            subscription(animeId = 10, player = "Alloha", dubbing = "MiraiDUB", playerId = 7).copy(videoId = 1),
            subscription(animeId = 10, player = "CVH", dubbing = "MiraiDUB", playerId = 9).copy(videoId = 2),
            subscription(animeId = 10, player = "CVH", dubbing = "AniDUB", playerId = 9).copy(videoId = 3),
        )

        val target = subscription(animeId = 10, player = "", dubbing = "", playerId = 0)
            .copy(videoId = 1)
            .unsubscribeTarget(currentSubscriptions)

        assertEquals("miraidub", target?.voiceKey)
        assertEquals(listOf(1L, 2L), target?.videoIds)
    }

    @Test
    fun unsubscribeTargetFilterRemovesResolvedVoiceSubscriptionsOnly() {
        val currentSubscriptions = listOf(
            subscription(animeId = 10, player = "Alloha", dubbing = "MiraiDUB", playerId = 7).copy(videoId = 1),
            subscription(animeId = 10, player = "CVH", dubbing = "MiraiDUB", playerId = 9).copy(videoId = 2),
            subscription(animeId = 10, player = "CVH", dubbing = "AniDUB", playerId = 9).copy(videoId = 3),
        )
        val target = currentSubscriptions.first().unsubscribeTarget(currentSubscriptions)!!

        val retained = currentSubscriptions.withoutUnsubscribeTarget(target)

        assertEquals(listOf(3L), retained.map { it.videoId })
    }

    @Test
    fun unsubscribeTargetAddsLoadedMatchingSourceIds() {
        val currentSubscriptions = listOf(
            subscription(animeId = 10, player = "Alloha", dubbing = "MiraiDUB", playerId = 7).copy(videoId = 1),
        )
        val loadedVideos = listOf(
            video(id = 1, player = "Alloha", playerId = 7, dubbing = "MiraiDUB"),
            video(id = 2, player = "CVH", playerId = 9, dubbing = "MiraiDUB"),
            video(id = 3, player = "CVH", playerId = 9, dubbing = "AniDUB"),
        )
        val target = currentSubscriptions.first().unsubscribeTarget(currentSubscriptions)!!

        val resolved = target.withResolvedVideoIds(loadedVideos)

        assertEquals(listOf(1L, 2L), resolved.videoIds)
    }

    @Test
    fun profileManagementSubscriptionsGroupFilterAndSortEntries() {
        val subscriptions = listOf(
            subscription(animeId = 2, player = "Alloha", dubbing = "MiraiDUB", playerId = 7)
                .copy(title = "Beta", videoId = 1),
            subscription(animeId = 2, player = "CVH", dubbing = "MiraiDUB", playerId = 9)
                .copy(title = "Beta", videoId = 2),
            subscription(animeId = 1, player = "CVH", dubbing = "AniDUB", playerId = 9)
                .copy(title = "alpha", videoId = 3),
            subscription(animeId = 3, player = "Alloha", dubbing = "Alloha", playerId = 7)
                .copy(title = "Hidden", videoId = 4),
        )

        val visible = subscriptions.profileSubscriptionsForManagement()

        assertEquals(listOf(3L, 1L), visible.map { it.videoId })
    }

    private fun subscription(
        animeId: Long,
        player: String,
        dubbing: String,
        playerId: Long,
    ): VideoSubscription {
        return VideoSubscription(
            animeId = animeId,
            title = "",
            posterUrl = "",
            player = player,
            dubbing = dubbing,
            playerId = playerId,
        )
    }

    private fun hint(
        animeId: Long,
        playerId: Long,
        voiceKey: String,
        voiceTitle: String,
    ): VideoSubscriptionHint {
        return VideoSubscriptionHint(
            animeId = animeId,
            playerId = playerId,
            voiceKey = voiceKey,
            voiceTitle = voiceTitle,
            title = "Anime",
            posterUrl = "poster.jpg",
        )
    }

    private fun video(
        id: Long,
        player: String,
        playerId: Long,
        dubbing: String,
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 10,
            player = player,
            playerId = playerId,
            dubbing = dubbing,
            episode = "1",
            url = "https://example.test/$id",
            index = id.toInt(),
            durationSeconds = null,
            views = 0,
        )
    }
}
