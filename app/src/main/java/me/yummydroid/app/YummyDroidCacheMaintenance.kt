package me.yummydroid.app

import android.app.Application
import java.io.File
import me.yummydroid.app.data.OfflineAnimeStorage
import me.yummydroid.app.data.totalSizeBytes

internal fun calculateAppContentCacheSize(application: Application): Long {
    val roots = listOfNotNull(
        application.cacheDir,
        application.externalCacheDir,
        File(application.filesDir, "source_quality_cache.json"),
    )
        .distinctBy { file -> file.safeCanonicalPath() }
    return roots.sumOf { root -> root.totalSizeBytes() } +
        OfflineAnimeStorage.contentPayloadSizeBytes(application)
}

internal fun Application.clearRuntimeCacheDirectories() {
    cacheDir.deleteChildrenRecursively()
    externalCacheDir?.deleteChildrenRecursively()
}

private fun File.deleteChildrenRecursively() {
    if (!exists()) {
        mkdirs()
        return
    }
    listFiles()
        .orEmpty()
        .forEach { child -> child.deleteRecursively() }
    mkdirs()
}

private fun File.safeCanonicalPath(): String {
    return runCatching { canonicalPath }.getOrDefault(absolutePath)
}
