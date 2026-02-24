package io.github.mohidsk.flickload.cache.memory

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CacheMemoryStorageTest {

    private lateinit var storage: CachedMemoryStorage<String>

    @Before
    fun setUp() {
        val config = CacheMemoryConfiguration(
            criticalMemoryMB = 1,
            hotMemoryMB = 1,
            warmMemoryMB = 1
        )
        storage = CachedMemoryStorage(config)
    }

    @Test
    fun `get returns null for missing key`() = runTest {
        assertNull(storage.get("missing"))
    }

    @Test
    fun `cacheItem and get round-trips value`() = runTest {
        storage.cacheItem("key1", "value1")
        assertEquals("value1", storage.get("key1"))
    }

    @Test
    fun `getSync returns null for missing key`() {
        assertNull(storage.getSync("missing"))
    }

    @Test
    fun `putSync and getSync round-trips value`() {
        storage.putSync("key1", "value1")
        assertEquals("value1", storage.getSync("key1"))
    }

    @Test
    fun `delete removes item`() = runTest {
        storage.cacheItem("key1", "value1")
        storage.delete("key1")
        assertNull(storage.get("key1"))
    }

    @Test
    fun `clear removes all items`() = runTest {
        storage.cacheItem("key1", "value1")
        storage.cacheItem("key2", "value2")
        storage.clear()
        assertNull(storage.get("key1"))
        assertNull(storage.get("key2"))
    }

    @Test
    fun `getBatchItems returns only existing items`() = runTest {
        storage.cacheItem("key1", "value1")
        storage.cacheItem("key3", "value3")

        val result = storage.getBatchItems(listOf("key1", "key2", "key3"))
        assertEquals(2, result.size)
        assertEquals("value1", result["key1"])
        assertEquals("value3", result["key3"])
        assertNull(result["key2"])
    }

    @Test
    fun `getBatchItems returns empty map for empty input`() = runTest {
        val result = storage.getBatchItems(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `cacheBatchItem with list caches all items`() = runTest {
        storage.cacheBatchItem(listOf("key1" to "value1", "key2" to "value2"))
        assertEquals("value1", storage.get("key1"))
        assertEquals("value2", storage.get("key2"))
    }

    @Test
    fun `cacheBatchItem with map caches all items`() = runTest {
        storage.cacheBatchItem(mapOf("key1" to "value1", "key2" to "value2"))
        assertEquals("value1", storage.get("key1"))
        assertEquals("value2", storage.get("key2"))
    }

    @Test
    fun `deleteBatch removes specified items`() = runTest {
        storage.cacheItem("key1", "value1")
        storage.cacheItem("key2", "value2")
        storage.cacheItem("key3", "value3")

        storage.deleteBatch(listOf("key1", "key3"))

        assertNull(storage.get("key1"))
        assertEquals("value2", storage.get("key2"))
        assertNull(storage.get("key3"))
    }

    @Test
    fun `totalMemoryMB configuration is sum of tiers`() {
        val config = CacheMemoryConfiguration(
            criticalMemoryMB = 10,
            hotMemoryMB = 20,
            warmMemoryMB = 30
        )
        assertEquals(60, config.totalMemoryMB)
    }

    @Test
    fun `default configuration values`() {
        val config = CacheMemoryConfiguration.default
        assertEquals(10, config.criticalMemoryMB)
        assertEquals(20, config.hotMemoryMB)
        assertEquals(30, config.warmMemoryMB)
    }
}
