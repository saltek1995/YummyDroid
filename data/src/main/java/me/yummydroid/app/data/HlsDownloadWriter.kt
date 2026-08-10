package me.yummydroid.app.data

import java.io.FileOutputStream

internal suspend fun YummyAnimeRepository.writeHlsDownload(
    session: HlsDownloadSession,
    stream: ResolvedVideoStream,
    onProgress: (DownloadProgressInfo) -> Unit,
    isCancelled: () -> Boolean,
    bandwidthLimiter: DownloadBandwidthLimiter,
) {
    val keyCache = mutableMapOf<String, ByteArray>()
    FileOutputStream(session.temp, true).use { output ->
        session.pendingInitUrl()?.let { initUrl ->
            val bytes = downloadUrlBytes(initUrl, stream.headers, bandwidthLimiter)
            output.write(bytes)
            output.flush()
            session.recordInit(bytes.size)
        }
        while (session.nextSegmentIndex < session.plan.segments.size) {
            val index = session.nextSegmentIndex
            val segment = session.plan.segments[index]
            check(!isCancelled()) { "Download cancelled" }
            val payload = downloadHlsSegmentPayload(segment, index, session.plan, stream, keyCache, bandwidthLimiter)
            output.write(payload)
            output.flush()
            onProgress(session.recordSegment(index, payload.size))
        }
    }
}

private suspend fun YummyAnimeRepository.downloadHlsSegmentPayload(
    segment: HlsMediaSegment,
    index: Int,
    plan: HlsSingleFilePlan,
    stream: ResolvedVideoStream,
    keyCache: MutableMap<String, ByteArray>,
    bandwidthLimiter: DownloadBandwidthLimiter,
): ByteArray {
    val bytes = downloadUrlBytes(segment.url, stream.headers, bandwidthLimiter)
    val encryption = segment.encryption ?: return bytes
    return decryptHlsSegment(
        bytes = bytes,
        encryption = encryption,
        sequenceNumber = plan.mediaSequence + index,
        headers = stream.headers,
        keyCache = keyCache,
        bandwidthLimiter = bandwidthLimiter,
    )
}
