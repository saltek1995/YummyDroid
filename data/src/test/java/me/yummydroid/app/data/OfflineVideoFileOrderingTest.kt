package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineVideoFileOrderingTest {
    @Test
    fun offlinePlaybackUsesTheSameQualityPreferenceForEveryEpisode() {
        for (episode in listOf("1", "2")) {
            val files = listOf(1080, 720, 480).map { height ->
                offlineFile("file:///episode-$episode-$height.mp4", "${height}p")
            }
            val video = video(episode, files)
            assertEquals(files[1].playbackUrl, video.forOfflineQuality(PreferredQuality.P720).localPlaybackUrl)
            assertEquals(files[0].playbackUrl, video.forOfflineQuality(PreferredQuality.Auto).localPlaybackUrl)
        }
    }

    @Test
    fun unavailableOfflineQualityUsesTheSharedNearestLowerQualityPolicy() {
        val files = listOf(1080, 480).map { height -> offlineFile("file:///$height.mp4", "${height}p") }
        assertEquals(files[1].playbackUrl, video("1", files).forOfflineQuality(PreferredQuality.P720).localPlaybackUrl)
    }

    @Test
    fun onlineSelectionClearsEveryOfflineInput() {
        val files = listOf(offlineFile("file:///720.mp4", "720p"))
        val online = video("1", files).withoutLocalPlayback()
        assertEquals(false, online.isOfflineAvailable)
        assertEquals(emptyList(), online.offlineFiles)
        assertEquals(online, online.forOfflineQuality(PreferredQuality.P720))
    }

    private fun video(episode: String, files: List<OfflineVideoFile>): VideoVariant = VideoVariant(
        id = 1, animeId = 10, player = "CVH", dubbing = "Voice", episode = episode,
        url = "https://player.test/$episode", index = 1, durationSeconds = null, views = 0,
        localPlaybackUrl = files.first().playbackUrl, localFiles = files,
    )

    @Test
    fun playableOfflineFilesAreSortedByQuality() {
        val files = listOf(
            offlineFile("file:///episode-720.mp4", "720p"),
            offlineFile("", "2160p"),
            offlineFile("file:///episode-auto.mp4", "Auto"),
            offlineFile("file:///episode-1080.mp4", "1080p"),
        )

        assertEquals(
            listOf("1080p", "720p", "Auto"),
            files.playableOfflineFilesByQuality().map(OfflineVideoFile::qualityTitle),
        )
    }

    @Test
    fun uniquePlayableOfflineFilesKeepOneFilePerUrlBeforeSorting() {
        val files = listOf(
            offlineFile("file:///episode.mp4", "720p"),
            offlineFile("file:///episode.mp4", "1080p"),
            offlineFile("file:///other.mp4", "480p"),
        )

        assertEquals(
            listOf("720p", "480p"),
            files.uniquePlayableOfflineFilesByQuality().map(OfflineVideoFile::qualityTitle),
        )
    }

    @Test
    fun uniquePlayableOfflineFilesKeepFirstUrlOrder() {
        val files = listOf(
            offlineFile("", "1080p"),
            offlineFile("file:///first.mp4", "480p"),
            offlineFile("file:///second.mp4", "720p"),
            offlineFile("file:///first.mp4", "1080p"),
        )

        assertEquals(
            listOf("480p", "720p"),
            files.uniquePlayableOfflineFiles().map(OfflineVideoFile::qualityTitle),
        )
    }

    private fun offlineFile(url: String, qualityTitle: String): OfflineVideoFile {
        return OfflineVideoFile(playbackUrl = url, qualityTitle = qualityTitle)
    }
}
