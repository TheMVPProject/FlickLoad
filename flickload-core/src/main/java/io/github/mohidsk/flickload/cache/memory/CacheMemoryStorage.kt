package io.github.mohidsk.flickload.cache.memory

import android.graphics.Bitmap
import android.util.LruCache
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class CacheMemoryConfiguration(
    val criticalMemoryMB: Int = 10,
    val hotMemoryMB: Int = 20,
    val warmMemoryMB: Int = 30
) {
    val totalMemoryMB: Int get() = criticalMemoryMB + hotMemoryMB + warmMemoryMB

    companion object {
        val default = CacheMemoryConfiguration()
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface CachedMemoryStorageProtocol<T> {
    suspend fun get(key: String): T?
    suspend fun getBatchItems(keys: List<String>): Map<String, T>
    suspend fun cacheItem(key: String, item: T)
    suspend fun cacheBatchItem(items: List<Pair<String, T>>)
    suspend fun cacheBatchItem(items: Map<String, T>)
    suspend fun delete(key: String)
    suspend fun deleteBatch(keys: List<String>)
    suspend fun clear()
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class CachedMemoryStorage<T : Any>(
    private val config: CacheMemoryConfiguration = CacheMemoryConfiguration.default
) : CachedMemoryStorageProtocol<T> {

    private val unifiedCache: LruCache<String, T>

    init {
        val totalMemoryBytes = config.totalMemoryMB * 1024 * 1024
        unifiedCache = object : LruCache<String, T>(totalMemoryBytes) {
            override fun sizeOf(key: String, value: T): Int = estimateSize(value)
        }
    }

    fun getSync(key: String): T? =
        unifiedCache.get(key)

    fun putSync(key: String, item: T) {
        unifiedCache.put(key, item)
    }

    override suspend fun get(key: String): T? =
        unifiedCache.get(key)

    override suspend fun getBatchItems(keys: List<String>): Map<String, T> {
        if (keys.isEmpty()) return emptyMap()

        return keys.mapNotNull { key ->
            unifiedCache.get(key)?.let { key to it }
        }.toMap()
    }

    override suspend fun cacheItem(key: String, item: T) {
        unifiedCache.put(key, item)
    }

    override suspend fun cacheBatchItem(items: List<Pair<String, T>>) {
        items.forEach { (key, item) ->
            unifiedCache.put(key, item)
        }
    }

    override suspend fun cacheBatchItem(items: Map<String, T>) {
        items.forEach { (key, item) ->
            unifiedCache.put(key, item)
        }
    }

    override suspend fun delete(key: String) {
        unifiedCache.remove(key)
    }

    override suspend fun deleteBatch(keys: List<String>) {
        keys.forEach { key ->
            unifiedCache.remove(key)
        }
    }

    override suspend fun clear() {
        unifiedCache.evictAll()
    }

    private fun estimateSize(item: T): Int {
        return when (item) {
            is Bitmap -> item.allocationByteCount
            else -> 50 * 1024
        }
    }
}
