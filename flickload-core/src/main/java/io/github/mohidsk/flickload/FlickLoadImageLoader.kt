package io.github.mohidsk.flickload

import android.graphics.Bitmap
import io.github.mohidsk.flickload.image.ImageLoadResult
import kotlinx.coroutines.flow.Flow

/**
 * Public API contract for FlickLoad image loading and caching.
 *
 * Obtain an instance via [FlickLoad.Builder]:
 * ```kotlin
 * val loader = FlickLoad.Builder(context).build()
 * ```
 *
 * Call [dispose] when the loader is no longer needed to release resources.
 */
interface FlickLoadImageLoader {

    /** Synchronous memory-only lookup. Returns `null` on cache miss. Safe to call from the main thread. */
    fun getImageFromMemorySync(key: String): Bitmap?

    /** Suspend memory-only lookup. */
    suspend fun getImageFromMemory(key: String): Bitmap?

    /** Load an image from memory, disk, or network. Returns `null` if all sources fail. */
    suspend fun getImage(url: String): Bitmap?

    /** Load an image and get a detailed [ImageLoadResult] including the source tier. */
    suspend fun getImageWithResult(url: String): ImageLoadResult

    /** Reactive image loading — emits [ImageLoadResult.Loading] then the final result. */
    fun getImageAsFlow(url: String): Flow<ImageLoadResult>

    /** Batch memory-only lookup for multiple URLs. */
    suspend fun getBatchImagesFromMemory(urls: List<String>): Map<String, Bitmap>

    /** Load multiple images concurrently from all cache tiers. */
    suspend fun getImages(urls: List<String>): Map<String, Bitmap>

    /** Load multiple images with detailed timing information for diagnostics. */
    suspend fun getImagesWithTiming(urls: List<String>): Pair<Map<String, Bitmap>, String>

    /** Manually cache a bitmap for the given URL key. */
    fun cacheImage(image: Bitmap, url: String)

    /** Start background prefetching for the given URLs. */
    fun prefetchImages(urls: List<String>)

    /** Cancel prefetch jobs that involve any of the given URLs. */
    fun cancelPrefetch(urls: List<String>)

    /** Cancel all active prefetch jobs. */
    fun cancelAllPrefetching()

    /** Return the number of active prefetch batches. */
    fun getActivePrefetchCount(): Int

    /** Suspend until all prefetch jobs for the given URLs complete. Returns `true` if any jobs were awaited. */
    suspend fun waitForPrefetch(urls: List<String>): Boolean

    /** Clear the in-memory cache only. */
    suspend fun clearMemoryCache()

    /** Clear all caches (memory + disk) and cancel active operations. */
    suspend fun clearAllCaches()

    /** Release all resources. The loader should not be used after this call. */
    fun dispose()
}
