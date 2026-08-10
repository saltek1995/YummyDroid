package me.yummydroid.app.data

import java.io.File

internal class HlsDownloadSession(
    val target: File,
    val plan: HlsSingleFilePlan,
    val qualityTitle: String,
    val voiceTitle: String,
    private val startedAtMs: Long = System.currentTimeMillis(),
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    val temp: File = target.partFile()
    private val stateFile = temp.hlsStateFile()
    private val signature = plan.signature()
    private var resumeState: HlsResumeState? = stateFile.readHlsResumeState(signature)
    private var sessionDownloadedBytes = 0L
    private var initWritten = resumeState?.initWritten ?: false
    var nextSegmentIndex: Int = resumeState?.nextSegmentIndex ?: 0
        private set

    fun prepareResume() {
        if (temp.exists() && temp.length() > 0L && resumeState == null) {
            temp.delete()
            stateFile.delete()
        }
    }

    fun pendingInitUrl(): String? = plan.initUrl?.takeUnless { initWritten }

    fun recordInit(payloadSize: Int) {
        sessionDownloadedBytes += payloadSize.toLong()
        initWritten = true
        stateFile.writeHlsResumeState(signature, initWritten, nextSegmentIndex)
    }

    fun recordSegment(index: Int, payloadSize: Int): DownloadProgressInfo {
        nextSegmentIndex = index + 1
        sessionDownloadedBytes += payloadSize.toLong()
        stateFile.writeHlsResumeState(signature, initWritten = true, nextSegmentIndex = nextSegmentIndex)
        return hlsSegmentDownloadProgress(
            nextSegmentIndex = nextSegmentIndex,
            segmentCount = plan.segments.size,
            downloadedBytes = temp.length().coerceAtLeast(0L),
            sessionDownloadedBytes = sessionDownloadedBytes,
            elapsedMs = (nowMs() - startedAtMs).coerceAtLeast(1L),
            qualityTitle = qualityTitle,
            voiceTitle = voiceTitle,
        )
    }

    fun complete() {
        stateFile.delete()
        temp.moveCompleteTo(target)
    }

    fun deletePartial() {
        temp.delete()
        stateFile.delete()
    }
}
