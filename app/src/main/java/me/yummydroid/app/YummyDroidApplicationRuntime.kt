package me.yummydroid.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

// StateOperationCoordination
internal class StateOperationLease internal constructor(
    private val isCurrentGeneration: () -> Boolean,
) {
    val isCurrent: Boolean
        get() = isCurrentGeneration()
}

internal class LatestStateOperationCoordinator {
    private val executionMutex = Mutex()

    @Volatile
    private var generation = 0L
    private var job: Job? = null

    @get:Synchronized
    val isActive: Boolean
        get() = job?.isActive == true

    @Synchronized
    fun launchLatest(
        scope: CoroutineScope,
        block: suspend (StateOperationLease) -> Unit,
    ): Job {
        val operationGeneration = ++generation
        val lease = StateOperationLease { synchronized(this) { generation == operationGeneration } }
        job?.cancel()
        val launched = scope.launch(start = CoroutineStart.LAZY) {
            executionMutex.withLock {
                if (lease.isCurrent) block(lease)
            }
        }
        job = launched
        launched.invokeOnCompletion {
            synchronized(this) {
                if (job === launched) job = null
            }
        }
        launched.start()
        return launched
    }

    @Synchronized
    fun cancel() {
        generation += 1L
        job?.cancel()
        job = null
    }
}

internal class SerialStateOperationCoordinator {
    private val executionMutex = Mutex()

    @Volatile
    private var generation = 0L
    private var tail: Job? = null
    private val jobs = mutableSetOf<Job>()

    @Synchronized
    fun launch(
        scope: CoroutineScope,
        block: suspend (StateOperationLease) -> Unit,
    ): Job {
        val operationGeneration = ++generation
        val lease = StateOperationLease { synchronized(this) { generation == operationGeneration } }
        val predecessor = tail
        val launched = scope.launch(start = CoroutineStart.LAZY) {
            predecessor?.join()
            executionMutex.withLock { block(lease) }
        }
        jobs += launched
        tail = launched
        launched.invokeOnCompletion {
            synchronized(this) {
                jobs -= launched
                if (tail === launched) tail = null
            }
        }
        launched.start()
        return launched
    }

    @Synchronized
    fun cancel() {
        generation += 1L
        jobs.toList().forEach(Job::cancel)
        jobs.clear()
        tail = null
    }
}

internal class KeyedLatestStateOperationCoordinator<K> {
    private val coordinators = mutableMapOf<K, LatestStateOperationCoordinator>()

    @Synchronized
    fun launchLatest(
        key: K,
        scope: CoroutineScope,
        block: suspend (StateOperationLease) -> Unit,
    ) {
        val coordinator = coordinators.getOrPut(key) { LatestStateOperationCoordinator() }
        val job = coordinator.launchLatest(scope, block)
        job.invokeOnCompletion {
            synchronized(this) {
                if (!coordinator.isActive && coordinators[key] === coordinator) {
                    coordinators.remove(key)
                }
            }
        }
    }

    @Synchronized
    fun cancel(key: K) {
        coordinators.remove(key)?.cancel()
    }

    @Synchronized
    fun cancelAll() {
        coordinators.values.forEach(LatestStateOperationCoordinator::cancel)
        coordinators.clear()
    }
}
