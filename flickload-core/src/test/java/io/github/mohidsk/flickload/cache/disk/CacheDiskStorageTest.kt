package io.github.mohidsk.flickload.cache.disk

import io.github.mohidsk.flickload.storage.DiskStorageProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class CacheDiskStorageTest {

    private lateinit var diskStorage: FakeInMemoryDiskStorage
    private lateinit var metadataStorage: FakeMetadataStorage
    private lateinit var cachedStorage: CachedDiskStorage<ByteArray>

    @Before
    fun setUp() = runTest {
        diskStorage = FakeInMemoryDiskStorage()
        metadataStorage = FakeMetadataStorage()
        cachedStorage = CachedDiskStorage(
            diskStorage = diskStorage,
            cacheMetadataStorage = metadataStorage,
            configuration = CacheDiskConfiguration(maxCacheSizeMB = 10, expirationDays = 30),
            serialize = { it },
            deserialize = { it }
        )
        cachedStorage.warmUp()
    }

    @Test
    fun `save and get round-trips data`() = runTest {
        val data = "hello".toByteArray()
        cachedStorage.save("key1", data)
        val result = cachedStorage.get("key1")
        assertArrayEquals(data, result)
    }

    @Test
    fun `get returns null for non-existent key`() = runTest {
        assertNull(cachedStorage.get("missing"))
    }

    @Test
    fun `exists returns true for saved item`() = runTest {
        cachedStorage.save("key1", "data".toByteArray())
        assertTrue(cachedStorage.exists("key1"))
    }

    @Test
    fun `exists returns false for missing item`() = runTest {
        assertFalse(cachedStorage.exists("missing"))
    }

    @Test
    fun `delete removes item`() = runTest {
        cachedStorage.save("key1", "data".toByteArray())
        cachedStorage.delete("key1")
        assertNull(cachedStorage.get("key1"))
        assertFalse(cachedStorage.exists("key1"))
    }

    @Test
    fun `clearAll removes everything`() = runTest {
        cachedStorage.save("key1", "data1".toByteArray())
        cachedStorage.save("key2", "data2".toByteArray())
        cachedStorage.clearAll()
        assertNull(cachedStorage.get("key1"))
        assertNull(cachedStorage.get("key2"))
    }

    @Test
    fun `getBatch returns matching items`() = runTest {
        cachedStorage.save("key1", "data1".toByteArray())
        cachedStorage.save("key2", "data2".toByteArray())

        val result = cachedStorage.getBatch(listOf("key1", "key2", "key3"))
        assertEquals(2, result.size)
        assertArrayEquals("data1".toByteArray(), result["key1"])
        assertArrayEquals("data2".toByteArray(), result["key2"])
    }

    @Test
    fun `saveBatch saves multiple items`() = runTest {
        val items = listOf(
            "key1" to "data1".toByteArray(),
            "key2" to "data2".toByteArray()
        )
        cachedStorage.saveBatch(items)
        assertArrayEquals("data1".toByteArray(), cachedStorage.get("key1"))
        assertArrayEquals("data2".toByteArray(), cachedStorage.get("key2"))
    }

    @Test
    fun `expired items return null`() = runTest {
        // Manually inject expired metadata
        val now = System.currentTimeMillis()
        val meta = CacheDiskMetadata("expired_key", 5, now - 1000, now - 1000, now - 1)
        metadataStorage.upsert(meta)
        diskStorage.write("expired_key", "data".toByteArray())

        // Re-warm to load the expired metadata
        cachedStorage.warmUp()

        assertNull(cachedStorage.get("expired_key"))
    }

    @Test
    fun `deleteBatch removes specified items`() = runTest {
        cachedStorage.save("key1", "data1".toByteArray())
        cachedStorage.save("key2", "data2".toByteArray())
        cachedStorage.save("key3", "data3".toByteArray())

        cachedStorage.deleteBatch(listOf("key1", "key3"))

        assertNull(cachedStorage.get("key1"))
        assertNotNull(cachedStorage.get("key2"))
        assertNull(cachedStorage.get("key3"))
    }

    @Test
    fun `deleteBatch with empty list is no-op`() = runTest {
        cachedStorage.save("key1", "data".toByteArray())
        cachedStorage.deleteBatch(emptyList())
        assertNotNull(cachedStorage.get("key1"))
    }
}

/** In-memory fake for DiskStorageProtocol */
private class FakeInMemoryDiskStorage : DiskStorageProtocol {
    private val store = ConcurrentHashMap<String, ByteArray>()

    override suspend fun read(key: String): ByteArray? = store[key]
    override suspend fun write(key: String, data: ByteArray) { store[key] = data }
    override suspend fun delete(key: String) { store.remove(key) }
    override suspend fun deleteBatchParallel(keys: List<String>) { keys.forEach { store.remove(it) } }
    override suspend fun exists(key: String): Boolean = store.containsKey(key)
    override suspend fun writeBatch(items: List<Pair<String, ByteArray>>) { items.forEach { (k, v) -> store[k] = v } }
    override suspend fun readBatchParallel(keys: List<String>): Map<String, ByteArray> = keys.mapNotNull { k -> store[k]?.let { k to it } }.toMap()
    override suspend fun listKeys(prefix: String?): List<String> = store.keys().toList().let { keys -> if (prefix != null) keys.filter { it.startsWith(prefix) } else keys }
    override suspend fun clear() { store.clear() }
    override suspend fun getSize(): Long = store.values.sumOf { it.size.toLong() }
}

/** In-memory fake for CacheDiskMetadataStorageProtocol */
private class FakeMetadataStorage : CacheDiskMetadataStorageProtocol {
    private val store = mutableMapOf<String, CacheDiskMetadata>()

    override suspend fun get(key: String) = store[key]
    override suspend fun getAll() = store.toMap()
    override suspend fun exists(key: String) = store[key]?.let { !it.isExpired } ?: false
    override suspend fun getExpiredKeys(before: Long) = store.filter { it.value.expirationDate < before }.keys.toList()
    override suspend fun getTotalSize() = store.values.sumOf { it.size }
    override suspend fun upsert(meta: CacheDiskMetadata) { store[meta.key] = meta }
    override suspend fun upsertBatch(items: List<CacheDiskMetadata>) { items.forEach { store[it.key] = it } }
    override suspend fun updateAccessTime(key: String, date: Long) { store[key]?.let { store[key] = it.copy(lastAccessedAt = date) } }
    override suspend fun updateAccessTimeBatch(keys: List<String>, date: Long) { keys.forEach { k -> store[k]?.let { store[k] = it.copy(lastAccessedAt = date) } } }
    override suspend fun delete(key: String) { store.remove(key) }
    override suspend fun deleteBatch(keys: List<String>) { keys.forEach { store.remove(it) } }
    override suspend fun clearAll() { store.clear() }
}
