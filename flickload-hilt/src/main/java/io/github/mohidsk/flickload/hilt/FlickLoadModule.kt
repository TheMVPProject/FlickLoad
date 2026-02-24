package io.github.mohidsk.flickload.hilt

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.mohidsk.flickload.FlickLoadImageLoader
import io.github.mohidsk.flickload.cache.disk.CacheDiskConfiguration
import io.github.mohidsk.flickload.cache.disk.CacheMetadataDBManager
import io.github.mohidsk.flickload.cache.disk.CachedDiskStorage
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FlickLoadModule {

    @Provides
    @Singleton
    fun provideImageCacheConfiguration(
        @ApplicationContext context: Context
    ): ImageCacheConfiguration {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return if (activityManager.isLowRamDevice) {
            ImageCacheConfiguration.lowMemory
        } else {
            ImageCacheConfiguration.default
        }
    }

    @Provides
    @Singleton
    fun provideImageProcessor(): ImageProcessor {
        return ImageProcessor()
    }

    @Provides
    @Singleton
    @FlickLoadMemoryCache
    fun provideImageMemoryCache(
        config: ImageCacheConfiguration
    ): CachedMemoryStorage<Bitmap> {
        val memoryConfig = CacheMemoryConfiguration(
            criticalMemoryMB = config.criticalMemoryMB,
            hotMemoryMB = config.hotMemoryMB,
            warmMemoryMB = config.warmMemoryMB
        )
        return CachedMemoryStorage(memoryConfig)
    }

    @Provides
    @Singleton
    @FlickLoadDiskCache
    fun provideImageDiskCache(
        @ApplicationContext context: Context,
        config: ImageCacheConfiguration
    ): CachedDiskStorage<ByteArray> {
        val diskStorage = DiskStorage(context, "flickload_image_cache")
        val metadataStorage = CacheMetadataDBManager(context, "flickload_cache_metadata.db")
        val diskConfig = CacheDiskConfiguration(
            maxCacheSizeMB = config.maxDiskCacheSizeMB,
            expirationDays = config.diskExpirationDays
        )

        return createCachedDiskStorageBytes(diskStorage, metadataStorage, diskConfig)
        // Warm-up is handled by ImageLoaderRepositoryImpl's managed scope
    }

    @Provides
    @Singleton
    fun provideImageMemoryCacheDataSource(
        @FlickLoadMemoryCache storage: CachedMemoryStorage<Bitmap>,
        imageProcessor: ImageProcessor
    ): ImageMemoryCacheDataSource {
        return ImageMemoryCacheDataSource(storage, imageProcessor)
    }

    @Provides
    @Singleton
    fun provideImageDiskCacheDataSource(
        @FlickLoadDiskCache storage: CachedDiskStorage<ByteArray>,
        imageProcessor: ImageProcessor
    ): ImageDiskCacheDataSource {
        return ImageDiskCacheDataSource(storage, imageProcessor)
    }

    @Provides
    @Singleton
    fun provideImageDownloadDataSource(
        @ApplicationContext context: Context,
        imageProcessor: ImageProcessor
    ): ImageDownloadDataSource {
        val cronetEngine = ImageDownloadDataSource.buildCronetEngine(context)
        return ImageDownloadDataSource(
            cronetEngine = cronetEngine,
            imageProcessor = imageProcessor
        )
    }

    @Provides
    @Singleton
    fun provideFlickLoadImageLoader(
        memoryCache: ImageMemoryCacheDataSource,
        diskCache: ImageDiskCacheDataSource,
        downloadDataSource: ImageDownloadDataSource
    ): FlickLoadImageLoader {
        return ImageLoaderRepositoryImpl(memoryCache, diskCache, downloadDataSource)
    }
}
