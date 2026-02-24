package io.github.mohidsk.flickload

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import io.github.mohidsk.flickload.cache.disk.CacheDiskConfiguration
import io.github.mohidsk.flickload.cache.disk.CacheMetadataDBManager
import io.github.mohidsk.flickload.cache.disk.createCachedDiskStorageBytes
import io.github.mohidsk.flickload.cache.memory.CacheMemoryConfiguration
import io.github.mohidsk.flickload.cache.memory.CachedMemoryStorage
import io.github.mohidsk.flickload.datasource.local.ImageDiskCacheDataSource
import io.github.mohidsk.flickload.datasource.local.ImageMemoryCacheDataSource
import io.github.mohidsk.flickload.datasource.network.ImageDownloadDataSource
import io.github.mohidsk.flickload.image.ImageCacheConfiguration
import io.github.mohidsk.flickload.image.ImageProcessor
import io.github.mohidsk.flickload.repository.ImageLoaderRepositoryImpl
import io.github.mohidsk.flickload.storage.DiskStorage

/**
 * Entry point for creating a [FlickLoadImageLoader] instance.
 *
 * Usage:
 * ```kotlin
 * val loader = FlickLoad.Builder(context).build()
 * ```
 */
object FlickLoad {

    class Builder(private val context: Context) {

        private var config: ImageCacheConfiguration? = null
        private var autoDetectLowMemory: Boolean = true

        /**
         * Override the cache configuration. If not set, defaults are chosen
         * automatically based on the device's memory class.
         */
        fun configuration(config: ImageCacheConfiguration) = apply {
            this.config = config
            this.autoDetectLowMemory = false
        }

        /**
         * Disable automatic low-memory detection. Uses default configuration.
         */
        fun disableLowMemoryDetection() = apply {
            this.autoDetectLowMemory = false
        }

        fun build(): FlickLoadImageLoader {
            val resolvedConfig = config ?: resolveConfig()
            val imageProcessor = ImageProcessor()

            // Memory cache
            val memoryConfig = CacheMemoryConfiguration(
                criticalMemoryMB = resolvedConfig.criticalMemoryMB,
                hotMemoryMB = resolvedConfig.hotMemoryMB,
                warmMemoryMB = resolvedConfig.warmMemoryMB
            )
            val memoryStorage = CachedMemoryStorage<Bitmap>(memoryConfig)
            val memoryDataSource = ImageMemoryCacheDataSource(memoryStorage, imageProcessor)

            // Disk cache
            val diskStorage = DiskStorage(context, "flickload_image_cache")
            val metadataStorage = CacheMetadataDBManager(context, "flickload_cache_metadata.db")
            val diskConfig = CacheDiskConfiguration(
                maxCacheSizeMB = resolvedConfig.maxDiskCacheSizeMB,
                expirationDays = resolvedConfig.diskExpirationDays
            )
            val cachedDiskStorage = createCachedDiskStorageBytes(diskStorage, metadataStorage, diskConfig)
            val diskDataSource = ImageDiskCacheDataSource(cachedDiskStorage, imageProcessor)

            // Network
            val cronetEngine = ImageDownloadDataSource.buildCronetEngine(context)
            val downloadDataSource = ImageDownloadDataSource(
                cronetEngine = cronetEngine,
                imageProcessor = imageProcessor
            )

            // Warm-up is launched on the repository's managed scope
            return ImageLoaderRepositoryImpl(memoryDataSource, diskDataSource, downloadDataSource)
        }

        private fun resolveConfig(): ImageCacheConfiguration {
            if (!autoDetectLowMemory) return ImageCacheConfiguration.default

            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return if (activityManager.isLowRamDevice) {
                ImageCacheConfiguration.lowMemory
            } else {
                ImageCacheConfiguration.default
            }
        }
    }
}
