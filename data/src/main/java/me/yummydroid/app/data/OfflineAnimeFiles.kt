package me.yummydroid.app.data

internal fun OfflineAnimeEntry.withExistingOfflineFiles(
    filesBySlot: Map<String, List<OfflineVideoFile>>,
): OfflineAnimeEntry {
    val updatedVideos = videos.map { video ->
        video.withExistingOfflineFiles(filesBySlot[video.downloadRecordSlotKey()].orEmpty())
    }
    return copy(videos = updatedVideos)
}

internal fun VideoVariant.withMergedOfflineFiles(
    files: List<OfflineVideoFile>,
    previewFallback: String,
): VideoVariant {
    val mergedFiles = files.validOfflineFiles()
    val primaryFile = mergedFiles.firstOrNull()
    return if (primaryFile != null) {
        copy(
            localPlaybackUrl = primaryFile.playbackUrl,
            localMimeType = primaryFile.mimeType,
            localBytes = primaryFile.bytes,
            localFiles = mergedFiles,
            previewUrl = previewUrl.ifBlank { previewFallback },
        )
    } else {
        withoutOfflineFiles()
    }
}

internal fun VideoVariant.deleteOfflineFile(playbackUrl: String?): VideoVariant {
    if (playbackUrl.isNullOrBlank()) {
        offlineFiles.forEach { it.playbackUrl.toOfflineLocalFile()?.deleteOfflineDownloadPackage() }
        localPlaybackUrl.toOfflineLocalFile()?.deleteOfflineDownloadPackage()
        return withoutOfflineFiles()
    }

    val remainingFiles = offlineFiles
        .filterNot { it.playbackUrl == playbackUrl }
        .distinctBy { it.playbackUrl }
    playbackUrl.toOfflineLocalFile()?.deleteOfflineDownloadPackage()
    return withPrimaryOfflineFile(remainingFiles)
}

internal fun VideoVariant.mergeStoredPlayback(storedVideo: VideoVariant?): VideoVariant {
    if (storedVideo == null) return this
    return copy(
        localPlaybackUrl = storedVideo.localPlaybackUrl,
        localMimeType = storedVideo.localMimeType,
        localBytes = storedVideo.localBytes,
        localFiles = storedVideo.offlineFiles,
    )
}

internal fun VideoVariant.withDownloadedFile(
    sourceVideo: VideoVariant,
    offlineFile: OfflineVideoFile,
): VideoVariant {
    val videoWithPlaybackMetadata = copy(skipSegments = skipSegments.ifEmpty { sourceVideo.skipSegments })
    val mergedFiles = (offlineFiles + offlineFile)
        .filter { it.playbackUrl.isNotBlank() }
        .distinctBy { it.playbackUrl }
        .sortedOfflineFiles()
    val primaryFile = mergedFiles.firstOrNull() ?: offlineFile
    return videoWithPlaybackMetadata.copy(
        localPlaybackUrl = primaryFile.playbackUrl,
        localMimeType = primaryFile.mimeType,
        localBytes = primaryFile.bytes,
        localFiles = mergedFiles,
        previewUrl = videoWithPlaybackMetadata.previewUrl.ifBlank { sourceVideo.previewUrl },
    )
}

private fun VideoVariant.withExistingOfflineFiles(files: List<OfflineVideoFile>): VideoVariant {
    if (files.isNotEmpty()) return withPrimaryOfflineFile(files)
    return if (isOfflineAvailable) withoutOfflineFiles() else this
}

private fun VideoVariant.withPrimaryOfflineFile(files: List<OfflineVideoFile>): VideoVariant {
    if (files.isEmpty()) return withoutOfflineFiles()
    val sortedFiles = files.sortedOfflineFiles()
    val primaryFile = files.maxWith(
        compareBy<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.bytes },
    )
    return copy(
        localPlaybackUrl = primaryFile.playbackUrl,
        localMimeType = primaryFile.mimeType,
        localBytes = primaryFile.bytes,
        localFiles = sortedFiles,
    )
}

private fun VideoVariant.withoutOfflineFiles(): VideoVariant {
    return copy(localPlaybackUrl = "", localMimeType = null, localBytes = 0L, localFiles = emptyList())
}

private fun List<OfflineVideoFile>.validOfflineFiles(): List<OfflineVideoFile> {
    return mapNotNull { offlineFile ->
        val file = offlineFile.playbackUrl.toOfflineLocalFile()
        if (file?.isCompletedOfflineDownloadFile() == true) {
            offlineFile.copy(bytes = file.downloadPackageSizeBytes())
        } else {
            null
        }
    }
        .distinctBy { it.playbackUrl }
        .sortedOfflineFiles()
}

private fun List<OfflineVideoFile>.sortedOfflineFiles(): List<OfflineVideoFile> {
    return sortedWith(
        compareByDescending<OfflineVideoFile> { it.qualityHeight() }.thenBy { it.qualityTitle },
    )
}
