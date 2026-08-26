package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class OfflineVideoFileOrderingTest {
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
