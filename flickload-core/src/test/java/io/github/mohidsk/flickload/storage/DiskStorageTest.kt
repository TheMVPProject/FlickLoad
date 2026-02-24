package io.github.mohidsk.flickload.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DiskStorageTest {

    private lateinit var storage: DiskStorage
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        cacheDir = File(context.cacheDir, "test_disk_storage")
        storage = DiskStorage(context, "test_disk_storage")
    }

    @After
    fun tearDown() = runTest {
        storage.clear()
    }

    @Test
    fun `write and read round-trips data`() = runTest {
        val data = "hello world".toByteArray()
        storage.write("key1", data)
        val result = storage.read("key1")
        assertArrayEquals(data, result)
    }

    @Test
    fun `read returns null for non-existent key`() = runTest {
        assertNull(storage.read("nonexistent"))
    }

    @Test
    fun `exists returns true for written key`() = runTest {
        storage.write("key1", "data".toByteArray())
        assertTrue(storage.exists("key1"))
    }

    @Test
    fun `exists returns false for non-existent key`() = runTest {
        assertFalse(storage.exists("nonexistent"))
    }

    @Test
    fun `delete removes key`() = runTest {
        storage.write("key1", "data".toByteArray())
        storage.delete("key1")
        assertNull(storage.read("key1"))
        assertFalse(storage.exists("key1"))
    }

    @Test
    fun `clear removes all data`() = runTest {
        storage.write("key1", "data1".toByteArray())
        storage.write("key2", "data2".toByteArray())
        storage.clear()
        assertNull(storage.read("key1"))
        assertNull(storage.read("key2"))
    }

    @Test
    fun `writeBatch writes multiple items`() = runTest {
        val items = listOf(
            "key1" to "data1".toByteArray(),
            "key2" to "data2".toByteArray(),
            "key3" to "data3".toByteArray()
        )
        storage.writeBatch(items)
        assertArrayEquals("data1".toByteArray(), storage.read("key1"))
        assertArrayEquals("data2".toByteArray(), storage.read("key2"))
        assertArrayEquals("data3".toByteArray(), storage.read("key3"))
    }

    @Test
    fun `readBatchParallel reads multiple items`() = runTest {
        storage.write("key1", "data1".toByteArray())
        storage.write("key2", "data2".toByteArray())

        val result = storage.readBatchParallel(listOf("key1", "key2", "key3"))
        assertEquals(2, result.size)
        assertArrayEquals("data1".toByteArray(), result["key1"])
        assertArrayEquals("data2".toByteArray(), result["key2"])
        assertNull(result["key3"])
    }

    @Test
    fun `deleteBatchParallel removes multiple keys`() = runTest {
        storage.write("key1", "data1".toByteArray())
        storage.write("key2", "data2".toByteArray())
        storage.write("key3", "data3".toByteArray())

        storage.deleteBatchParallel(listOf("key1", "key3"))

        assertNull(storage.read("key1"))
        assertNotNull(storage.read("key2"))
        assertNull(storage.read("key3"))
    }

    @Test
    fun `getSize returns total size of stored data`() = runTest {
        val data1 = ByteArray(1000)
        val data2 = ByteArray(2000)
        storage.write("key1", data1)
        storage.write("key2", data2)

        val size = storage.getSize()
        assertTrue("Size should be at least 3000, got $size", size >= 3000)
    }

    @Test
    fun `overwrite replaces existing data`() = runTest {
        storage.write("key1", "original".toByteArray())
        storage.write("key1", "updated".toByteArray())
        assertArrayEquals("updated".toByteArray(), storage.read("key1"))
    }

    @Test
    fun `large batch write with more than 32 items`() = runTest {
        val items = (1..50).map { "key_$it" to "data_$it".toByteArray() }
        storage.writeBatch(items)

        items.forEach { (key, data) ->
            assertArrayEquals("Failed for $key", data, storage.read(key))
        }
    }
}
