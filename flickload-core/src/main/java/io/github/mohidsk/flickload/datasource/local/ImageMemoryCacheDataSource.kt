package io.github.mohidsk.flickload.datasource.local

import android.graphics.Bitmap
import android.util.LruCache
import androidx.annotation.RestrictTo
import io.github.mohidsk.flickload.cache.memory.CachedMemoryStorage
import io.github.mohidsk.flickload.image.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

private const val DEFAULT_BYTES_CACHE_SIZE_MB = 50

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ImageMemoryCacheDataSource(
    private val cachedStorage: CachedMemoryStorage<Bitmap>,
    private val imageProcessor: ImageProcessor,
    bytesCacheSizeMB: Int = DEFAULT_BYTES_CACHE_SIZE_MB
) {

    private val bytesCache: LruCache<String, ByteArray> = object : LruCache<String, ByteArray>(
        bytesCacheSizeMB * 1024 * 1024
    ) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }

    fun getImageSync(key: String): Bitmap? {
        cachedStorage.getSync(key)?.let { return it }

        bytesCache.get(key)?.let { bytes ->
            val bitmap = imageProcessor.decompressSync(bytes) ?: return null
            cachedStorage.putSync(key, bitmap)
            bytesCache.remove(key)
            return bitmap
        }

        return null
    }

    suspend fun getImage(key: String): Bitmap? {
        cachedStorage.get(key)?.let { return it }

        bytesCache.get(key)?.let { bytes ->
            val bitmap = imageProcessor.decompressSync(bytes) ?: return null
            cachedStorage.cacheItem(key, bitmap)
            bytesCache.remove(key)
            return bitmap
        }

        return null
    }

    suspend fun cacheImage(image: Bitmap, key: String) {
        cachedStorage.cacheItem(key, image)
    }

    suspend fun getBatchImages(keys: List<String>): Map<String, Bitmap> {
        if (keys.isEmpty()) return emptyMap()

        val results = cachedStorage.getBatchItems(keys).toMutableMap()

        val missingKeys = keys.filter { results[it] == null }
        if (missingKeys.isNotEmpty()) {
            val byteHits = missingKeys.mapNotNull { key ->
                bytesCache.get(key)?.let { key to it }
            }

            if (byteHits.isNotEmpty()) {
                coroutineScope {
                    byteHits.map { (key, bytes) ->
                        async(Dispatchers.Default) {
                            imageProcessor.decompressSync(bytes)?.let { bitmap ->
                                cachedStorage.cacheItem(key, bitmap)
                                bytesCache.remove(key)
                                key to bitmap
                            }
                        }
                    }.awaitAll().filterNotNull().forEach { (key, bitmap) ->
                        results[key] = bitmap
                    }
                }
            }
        }

        return results
    }

    suspend fun cacheBatchImages(images: List<Pair<String, Bitmap>>) {
        if (images.isEmpty()) return
        cachedStorage.cacheBatchItem(images)
    }

    suspend fun cacheBatchImages(images: Map<String, Bitmap>) {
        if (images.isEmpty()) return
        cachedStorage.cacheBatchItem(images)
    }

    fun cacheBatchBytes(items: Map<String, ByteArray>) {
        items.forEach { (key, bytes) ->
            bytesCache.put(key, bytes)
        }
    }

    suspend fun deleteImage(key: String) {
        cachedStorage.delete(key)
        bytesCache.remove(key)
    }

    suspend fun clear() {
        cachedStorage.clear()
        bytesCache.evictAll()
    }
}
