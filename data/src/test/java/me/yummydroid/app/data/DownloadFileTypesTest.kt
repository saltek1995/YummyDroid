package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DownloadFileTypesTest {
    @Test
    fun extensionIgnoresQueryAndFragmentAndDefaultsToMp4() {
        assertEquals("m3u8", "https://cdn.test/master.m3u8?token=1#live".fileExtensionForDownload())
        assertEquals("m4s", "https://cdn.test/segment.M4S".fileExtensionForDownload())
        assertEquals("mp4", "https://cdn.test/video".fileExtensionForDownload())
    }

    @Test
    fun mimeTypeRecognizesSupportedDownloadContainers() {
        assertEquals("application/x-mpegURL", "master.m3u8".mimeTypeFromFileName())
        assertEquals("application/dash+xml", "manifest.mpd".mimeTypeFromFileName())
        assertEquals("video/mp4", "segment.m4s".mimeTypeFromFileName())
        assertEquals("video/mp2t", "segment.ts".mimeTypeFromFileName())
        assertEquals("video/x-matroska", "video.mkv".mimeTypeFromFileName())
        assertEquals("video/webm", "video.webm".mimeTypeFromFileName())
        assertNull("subtitle.vtt".mimeTypeFromFileName())
    }
}
