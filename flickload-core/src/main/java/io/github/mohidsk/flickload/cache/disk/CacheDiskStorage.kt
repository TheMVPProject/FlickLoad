package io.github.mohidsk.flickload.cache.disk

import androidx.annotation.RestrictTo
import io.github.mohidsk.flickload.storage.DiskStorageProtocol
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface CachedDiskStorageProtocol<T> {
    suspend fun get(key: String): T?
    suspend fun getBatch(keys: List<String>): Map<String, T>
    suspend fun save(key: String, item: T)
    suspend fun saveBatch(items: List<Pair<String, T>>)
    suspend fun delete(key: String)
    suspend fun deleteBatch(keys: List<String>)
    suspend fun exists(key: String): Boolean
    suspend fun clearAll()
    suspend fun clearExpired()
    suspend fun warmUp()
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class CachedDiskStorage<T>(
    private val diskStorage: DiskStorageProtocol,
    private val cacheMetadataStorage: CacheDiskMetadataStorageProtocol,
    private val configuration: CacheDiskConfiguration = CacheDiskConfiguration.default,
    private val serialize: suspend (T) -> ByteArray,
    private val deserialize: suspend (ByteArray) -> T
) : CachedDiskStorageProtocol<T>, Closeable {

    private val mutex = Mutex()
    private var metadataCache = mutableMapOf<String, CacheDiskMetadata>()
    private var currentSize = 0L
    private val maxSize = configuration.maxCacheSizeMB * 1024 * 1024
    private val backgroundJob = SupervisorJob()
    private val backgroundScope = CoroutineScope(Dispatchers.IO + backgroundJob)

    override suspend fun warmUp() {
        loadMetadata()
        cleanupExpired()
        enforceStorageLimit()
    }

    override suspend fun get(key: String): T? {
        val meta = mutex.withLock { metadataCache[key] } ?: return null

        if (meta.isExpired) {
            delete(key)
            return null
        }

        val data = diskStorage.read(key) ?: run {
            deleteMetadata(key)
            return null
        }

        updateAccessTime(key)

        return withContext(Dispatchers.Default) {
            deserialize(data)
        }
    }

    override suspend fun getBatch(keys: List<String>): Map<String, T> {
        val (validKeys, expiredKeys) = mutex.withLock {
            val valid = mutableListOf<String>()
            val expired = mutableListOf<String>()

            keys.forEach { key ->
                metadataCache[key]?.let { meta ->
                    if (meta.isExpired) expired.add(key) else valid.add(key)
                }
            }
            valid to expired
        }

        if (expiredKeys.isNotEmpty()) deleteBatch(expiredKeys)
        if (validKeys.isEmpty()) return emptyMap()

        val dataDict = diskStorage.readBatchParallel(validKeys)
        updateAccessTimeBatch(dataDict.keys.toList())

        val result = HashMap<String, T>(dataDict.size)
        for ((k, d) in dataDict) {
            try {
                result[k] = deserialize(d)
            } catch (_: Exception) { }
        }
        return result
    }

    override suspend fun save(key: String, item: T) {
        val data = withContext(Dispatchers.Default) { serialize(item) }
        diskStorage.write(key, data)
        addMetadata(key, data)
        enforceStorageLimit()
    }

    override suspend fun saveBatch(items: List<Pair<String, T>>) {
        val dataItems = coroutineScope {
            items.map { (k, i) ->
                async(Dispatchers.Default) { k to serialize(i) }
            }.awaitAll()
        }

        diskStorage.writeBatch(dataItems)
        addMetadataBatch(dataItems)
        enforceStorageLimit()
    }

    override suspend fun delete(key: String) {
        diskStorage.delete(key)
        deleteMetadata(key)
    }

    override suspend fun deleteBatch(keys: List<String>) {
        if (keys.isEmpty()) return
        diskStorage.deleteBatchParallel(keys)
        deleteMetadataBatch(keys)
    }

    override suspend fun exists(key: String): Boolean = mutex.withLock {
        metadataCache[key]?.let { !it.isExpired } ?: false
    }

    override suspend fun clearAll() {
        diskStorage.clear()
        mutex.withLock {
            metadataCache.clear()
            currentSize = 0
        }
        cacheMetadataStorage.clearAll()
    }

    override suspend fun clearExpired() = cleanupExpired()

    override fun close() {
        backgroundJob.cancel()
    }

    private suspend fun cleanupExpired() {
        val expiredKeys = mutex.withLock {
            metadataCache.filter { it.value.isExpired }.keys.toList()
        }
        if (expiredKeys.isNotEmpty()) {
            deleteBatch(expiredKeys)
        }
    }

    private suspend fun enforceStorageLimit() {
        val needsCleanup = mutex.withLock { currentSize > maxSize }
        if (needsCleanup) {
            val bytesToFree = mutex.withLock { currentSize - maxSize }
            freeUpSpace(bytesToFree)
        }
    }

    private suspend fun freeUpSpace(bytes: Long) {
        var freedBytes = 0L
        val keysToRemove = mutex.withLock {
            metadataCache.entries.sortedBy { it.value.lastAccessedAt }.map { it.key }
        }

        val keysToDelete = mutableListOf<String>()
        for (key in keysToRemove) {
            if (freedBytes >= bytes) break
            mutex.withLock { metadataCache[key]?.size }?.let {
                freedBytes += it
                keysToDelete.add(key)
            }
        }
        deleteBatch(keysToDelete)
    }

    private suspend fun updateAccessTime(key: String) {
        val now = System.currentTimeMillis()
        mutex.withLock {
            metadataCache[key]?.let {
                metadataCache[key] = it.copy(lastAccessedAt = now)
            }
        }
        backgroundScope.launch {
            cacheMetadataStorage.updateAccessTime(key, now)
        }
    }

    private suspend fun updateAccessTimeBatch(keys: List<String>) {
        val now = System.currentTimeMillis()
        mutex.withLock {
            keys.forEach { key ->
                metadataCache[key]?.let {
                    metadataCache[key] = it.copy(lastAccessedAt = now)
                }
            }
        }
        backgroundScope.launch {
            cacheMetadataStorage.updateAccessTimeBatch(keys, now)
        }
    }

    private suspend fun addMetadata(key: String, data: ByteArray) {
        val now = System.currentTimeMillis()
        val expirationMs = now + configuration.expirationDays * 86_400_000L

        val meta = mutex.withLock {
            metadataCache[key]?.let { currentSize -= it.size }

            val m = CacheDiskMetadata(
                key = key,
                size = data.size.toLong(),
                createdAt = now,
                lastAccessedAt = now,
                expirationDate = expirationMs
            )
            metadataCache[key] = m
            currentSize += data.size.toLong()
            m
        }

        cacheMetadataStorage.upsert(meta)
    }

    private suspend fun deleteMetadata(key: String) {
        mutex.withLock {
            metadataCache[key]?.let { currentSize -= it.size }
            metadataCache.remove(key)
        }
        cacheMetadataStorage.delete(key)
    }

    private suspend fun deleteMetadataBatch(keys: List<String>) {
        mutex.withLock {
            keys.forEach { key ->
                metadataCache[key]?.let { currentSize -= it.size }
                metadataCache.remove(key)
            }
        }
        cacheMetadataStorage.deleteBatch(keys)
    }

    private suspend fun addMetadataBatch(items: List<Pair<String, ByteArray>>) {
        val now = System.currentTimeMillis()
        val expirationMs = now + configuration.expirationDays * 86_400_000L

        val metaList = mutex.withLock {
            items.map { (key, data) ->
                metadataCache[key]?.let { currentSize -= it.size }

                val meta = CacheDiskMetadata(
                    key = key,
                    size = data.size.toLong(),
                    createdAt = now,
                    lastAccessedAt = now,
                    expirationDate = expirationMs
                )
                metadataCache[key] = meta
                currentSize += data.size.toLong()
                meta
            }
        }
        cacheMetadataStorage.upsertBatch(metaList)
    }

    private suspend fun loadMetadata() {
        val loaded = cacheMetadataStorage.getAll()
        mutex.withLock {
            metadataCache = loaded.toMutableMap()
            currentSize = loaded.values.sumOf { it.size }
        }
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun createCachedDiskStorageBytes(
    diskStorage: DiskStorageProtocol,
    cacheMetadataStorage: CacheDiskMetadataStorageProtocol,
    configuration: CacheDiskConfiguration = CacheDiskConfiguration.default
) = CachedDiskStorage(
    diskStorage = diskStorage,
    cacheMetadataStorage = cacheMetadataStorage,
    configuration = configuration,
    serialize = { it },
    deserialize = { it }
)
