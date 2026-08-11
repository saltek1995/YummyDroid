package me.yummydroid.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.yummydroid.app.data.OfflineAnimeStorage
import me.yummydroid.app.data.totalSizeBytes

// YummyDroidApplicationRuntime
class YummyDroidApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        SubscriptionNotificationScheduler.configureFromStoredStateAsync(
            context = this,
            runImmediately = false,
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .dispatcher(Dispatchers.IO.limitedParallelism(2))
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.32)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve(IMAGE_CACHE_DIR_NAME))
                    .maxSizeBytes(IMAGE_CACHE_MAX_BYTES)
                    .build()
            }
            .crossfade(false)
            .build()
    }

    private companion object {
        const val IMAGE_CACHE_DIR_NAME = "image_cache"
        const val IMAGE_CACHE_MAX_BYTES = 256L * 1024L * 1024L
    }
}

// YummyDroidCacheMaintenance
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
