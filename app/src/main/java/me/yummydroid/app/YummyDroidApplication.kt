package me.yummydroid.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

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
                    .maxSizePercent(0.20)
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
