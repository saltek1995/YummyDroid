package me.yummydroid.app.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DirectVideoDownloadRequestTest {
    private val stream = ResolvedVideoStream(
        url = "https://example.com/video.mp4",
        mimeType = "video/mp4",
        headers = mapOf("Authorization" to "Bearer token", "Accept-Encoding" to "gzip"),
    )

    @Test
    fun freshRequestPreservesSourceHeadersAndForcesIdentityEncoding() {
        val request = stream.directDownloadRequest(existingBytes = 0L)

        assertEquals("Bearer token", request.header("Authorization"))
        assertEquals("identity", request.header("Accept-Encoding"))
        assertNull(request.header("Range"))
    }

    @Test
    fun resumedRequestStartsRangeAfterExistingBytes() {
        val request = stream.directDownloadRequest(existingBytes = 42L)

        assertEquals("bytes=42-", request.header("Range"))
    }
}
