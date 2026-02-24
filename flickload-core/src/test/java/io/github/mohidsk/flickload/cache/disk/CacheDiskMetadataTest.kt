package io.github.mohidsk.flickload.cache.disk

import org.junit.Assert.*
import org.junit.Test

class CacheDiskMetadataTest {

    @Test
    fun `isExpired returns true for past expiration`() {
        val meta = CacheDiskMetadata(
            key = "test",
            size = 100,
            createdAt = 1000,
            lastAccessedAt = 1000,
            expirationDate = System.currentTimeMillis() - 1000
        )
        assertTrue(meta.isExpired)
    }

    @Test
    fun `isExpired returns false for future expiration`() {
        val meta = CacheDiskMetadata(
            key = "test",
            size = 100,
            createdAt = 1000,
            lastAccessedAt = 1000,
            expirationDate = System.currentTimeMillis() + 86_400_000
        )
        assertFalse(meta.isExpired)
    }

    @Test
    fun `default disk configuration values`() {
        val config = CacheDiskConfiguration.default
        assertEquals(2000L, config.maxCacheSizeMB)
        assertEquals(30, config.expirationDays)
    }

    @Test
    fun `custom disk configuration preserves values`() {
        val config = CacheDiskConfiguration(maxCacheSizeMB = 500, expirationDays = 7)
        assertEquals(500L, config.maxCacheSizeMB)
        assertEquals(7, config.expirationDays)
    }

    @Test
    fun `CacheMetadataError types have correct messages`() {
        val noConn = CacheMetadataError.NoConnection()
        assertEquals("No database connection", noConn.message)

        val corrupted = CacheMetadataError.CorruptedData("bad data")
        assertEquals("bad data", corrupted.message)

        val invalidSize = CacheMetadataError.InvalidSize("negative size")
        assertEquals("negative size", invalidSize.message)

        val invalidDate = CacheMetadataError.InvalidDate("bad date")
        assertEquals("bad date", invalidDate.message)

        val invalidMeta = CacheMetadataError.InvalidMetadata("invalid")
        assertEquals("invalid", invalidMeta.message)
    }

    @Test
    fun `SQLiteError types have correct messages`() {
        val exec = SQLiteError.ExecutionFailed("exec fail")
        assertEquals("exec fail", exec.message)

        val prep = SQLiteError.PrepareFailed("prep fail")
        assertEquals("prep fail", prep.message)

        val bind = SQLiteError.BindFailed("bind fail")
        assertEquals("bind fail", bind.message)

        val txn = SQLiteError.TransactionFailed("txn fail")
        assertEquals("txn fail", txn.message)
    }

    @Test
    fun `metadata data class equality works`() {
        val m1 = CacheDiskMetadata("k", 100, 1000, 1000, 2000)
        val m2 = CacheDiskMetadata("k", 100, 1000, 1000, 2000)
        assertEquals(m1, m2)
        assertEquals(m1.hashCode(), m2.hashCode())
    }

    @Test
    fun `metadata copy works`() {
        val original = CacheDiskMetadata("k", 100, 1000, 1000, 2000)
        val updated = original.copy(lastAccessedAt = 1500)
        assertEquals(1500L, updated.lastAccessedAt)
        assertEquals(original.key, updated.key)
        assertEquals(original.size, updated.size)
    }
}
