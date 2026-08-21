package me.yummydroid.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import me.yummydroid.app.data.OfflineVideoFile
import me.yummydroid.app.data.PreferredQuality
import me.yummydroid.app.data.VideoVariant

class YummyCastPlaybackTest {
    @Test
    fun payloadKeepsCurrentVoiceAndPreferredSourceForEveryEpisode() {
        val current = video(id = 20, episode = "2", index = 2, player = "Alloha")
        val videos = listOf(
            video(id = 10, episode = "1", index = 1, player = "Kodik"),
            video(id = 11, episode = "1", index = 1, player = "Alloha"),
            current,
            video(id = 21, episode = "2", index = 2, player = "Kodik"),
            video(id = 30, episode = "3", index = 3, player = "Alloha", dubbing = "AniDUB"),
        )

        val payload = createYummyCastPlaybackPayload(
            animeTitle = "Anime",
            currentVideo = current,
            allVideos = videos,
            preferredQuality = PreferredQuality.P720,
            skipOpeningsAndEndings = false,
            autoplayNextEpisode = false,
            hasPreviousEpisode = true,
            hasNextEpisode = true,
        )

        assertEquals(listOf(11L, 20L), payload.episodeVideos.map(VideoVariant::id))
        assertTrue(payload.episodeVideos.all { it.player == "Alloha" && it.dubbing == "AniLibria" })
        assertEquals(PreferredQuality.P720, payload.preferredQuality)
        assertFalse(payload.skipOpeningsAndEndings)
        assertFalse(payload.autoplayNextEpisode)
        assertTrue(payload.hasPreviousEpisode)
        assertTrue(payload.hasNextEpisode)
    }

    @Test
    fun payloadNeverSendsDeviceLocalFilesToReceiver() {
        val current = video(id = 20, episode = "2", index = 2, player = "Alloha").copy(
            localPlaybackUrl = "file:///storage/episode.mp4",
            localMimeType = "video/mp4",
            localBytes = 123L,
            localFiles = listOf(
                OfflineVideoFile(
                    playbackUrl = "file:///storage/episode-720.mp4",
                    mimeType = "video/mp4",
                    bytes = 456L,
                ),
            ),
        )

        val payload = createYummyCastPlaybackPayload(
            animeTitle = "Anime",
            currentVideo = current,
            allVideos = listOf(current),
            preferredQuality = PreferredQuality.Auto,
        )

        assertEquals("", payload.video.localPlaybackUrl)
        assertNull(payload.video.localMimeType)
        assertEquals(0L, payload.video.localBytes)
        assertTrue(payload.video.localFiles.isEmpty())
        assertTrue(payload.episodeVideos.single().localFiles.isEmpty())
    }

    @Test
    fun directPlaybackUsesSenderHttpUrlAndOnlyCurrentEpisode() {
        val first = video(id = 10, episode = "1", index = 1, player = "Alloha")
        val current = video(id = 20, episode = "2", index = 2, player = "Alloha")
        val payload = createYummyCastPlaybackPayload(
            animeTitle = "Anime",
            currentVideo = current,
            allVideos = listOf(first, current),
            preferredQuality = PreferredQuality.P1080,
        ).withDirectPlayback(
            playbackUrl = "http://192.168.1.5:38447/media",
            mimeType = "video/mp4",
        )

        assertEquals("http://192.168.1.5:38447/media", payload.video.localPlaybackUrl)
        assertEquals("video/mp4", payload.video.localMimeType)
        assertEquals(listOf(payload.video), payload.episodeVideos)
    }

    @Test
    fun payloadRoundTripsThroughCastCodec() {
        val current = video(id = 20, episode = "2", index = 2, player = "Alloha")
        val payload = createYummyCastPlaybackPayload(
            animeTitle = "Anime",
            currentVideo = current,
            allVideos = listOf(current),
            preferredQuality = PreferredQuality.P480,
        )

        val encoded = encodeYummyCastPlaybackPayload(payload)

        assertEquals(payload, decodeYummyCastPlaybackPayload(encoded))
    }

    @Test
    fun receiverRejectsUnknownPayloadVersion() {
        val current = video(id = 20, episode = "2", index = 2, player = "Alloha")
        val payload = createYummyCastPlaybackPayload(
            animeTitle = "Anime",
            currentVideo = current,
            allVideos = listOf(current),
            preferredQuality = PreferredQuality.Auto,
        )
        val encoded = encodeYummyCastPlaybackPayload(payload)
            .replace("\"version\":1", "\"version\":2")

        assertNull(decodeYummyCastPlaybackPayload(encoded))
    }

    private fun video(
        id: Long,
        episode: String,
        index: Int,
        player: String,
        dubbing: String = "AniLibria",
    ): VideoVariant {
        return VideoVariant(
            id = id,
            animeId = 100,
            player = player,
            dubbing = dubbing,
            episode = episode,
            url = "https://${player.lowercase()}.test/$id",
            index = index,
            durationSeconds = 1_400,
            views = 0,
        )
    }
}
