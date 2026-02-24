package io.github.mohidsk.flickload.cache.disk

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.annotation.RestrictTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class CacheDiskMetadata(
    val key: String,
    val size: Long,
    val createdAt: Long,
    val lastAccessedAt: Long,
    val expirationDate: Long
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expirationDate
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
data class CacheDiskConfiguration(
    val maxCacheSizeMB: Long = 2000,
    val expirationDays: Int = 30
) {
    companion object {
        val default = CacheDiskConfiguration()
    }
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
sealed class CacheMetadataError(message: String) : Exception(message) {
    class NoConnection : CacheMetadataError("No database connection")
    class CorruptedData(msg: String) : CacheMetadataError(msg)
    class InvalidSize(msg: String) : CacheMetadataError(msg)
    class InvalidDate(msg: String) : CacheMetadataError(msg)
    class InvalidMetadata(msg: String) : CacheMetadataError(msg)
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
sealed class SQLiteError(message: String) : Exception(message) {
    class ExecutionFailed(msg: String) : SQLiteError(msg)
    class PrepareFailed(msg: String) : SQLiteError(msg)
    class BindFailed(msg: String) : SQLiteError(msg)
    class TransactionFailed(msg: String) : SQLiteError(msg)
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface CacheDiskMetadataStorageProtocol {
    suspend fun get(key: String): CacheDiskMetadata?
    suspend fun getAll(): Map<String, CacheDiskMetadata>
    suspend fun exists(key: String): Boolean
    suspend fun getExpiredKeys(before: Long = System.currentTimeMillis()): List<String>
    suspend fun getTotalSize(): Long
    suspend fun upsert(meta: CacheDiskMetadata)
    suspend fun upsertBatch(items: List<CacheDiskMetadata>)
    suspend fun updateAccessTime(key: String, date: Long = System.currentTimeMillis())
    suspend fun updateAccessTimeBatch(keys: List<String>, date: Long = System.currentTimeMillis())
    suspend fun delete(key: String)
    suspend fun deleteBatch(keys: List<String>)
    suspend fun clearAll()
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class CacheMetadataDBManager(
    context: Context,
    dbName: String = "cache_metadata.db"
) : CacheDiskMetadataStorageProtocol {

    private val dbHelper = DBHelper(context, dbName)
    private val db: SQLiteDatabase = dbHelper.writableDatabase
    private val mutex = Mutex()

    override suspend fun get(key: String): CacheDiskMetadata? = withContext(Dispatchers.IO) {
        mutex.withLock {
            db.rawQuery(
                "SELECT `key`, size, created_at, last_accessed_at, expiration_date FROM cache_metadata WHERE `key` = ?",
                arrayOf(key)
            ).use { cursor ->
                if (cursor.moveToFirst()) readRow(cursor) else null
            }
        }
    }

    override suspend fun getAll(): Map<String, CacheDiskMetadata> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val result = mutableMapOf<String, CacheDiskMetadata>()
            db.rawQuery(
                "SELECT `key`, size, created_at, last_accessed_at, expiration_date FROM cache_metadata",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val meta = readRow(cursor)
                    result[meta.key] = meta
                }
            }
            result
        }
    }

    override suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            db.rawQuery(
                "SELECT 1 FROM cache_metadata WHERE `key` = ? AND expiration_date > ?",
                arrayOf(key, System.currentTimeMillis().toString())
            ).use { it.moveToFirst() }
        }
    }

    override suspend fun getExpiredKeys(before: Long): List<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val keys = mutableListOf<String>()
            db.rawQuery(
                "SELECT `key` FROM cache_metadata WHERE expiration_date < ?",
                arrayOf(before.toString())
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    keys.add(cursor.getString(0))
                }
            }
            keys
        }
    }

    override suspend fun getTotalSize(): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            db.rawQuery("SELECT COALESCE(SUM(size), 0) FROM cache_metadata", null).use {
                if (it.moveToFirst()) it.getLong(0) else 0L
            }
        }
    }

    override suspend fun upsert(meta: CacheDiskMetadata) = withContext(Dispatchers.IO) {
        mutex.withLock {
            validateMetadata(meta)
            val values = meta.toContentValues()
            val result = db.insertWithOnConflict(
                "cache_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE
            )
            if (result == -1L) throw SQLiteError.ExecutionFailed("Failed to upsert")
        }
    }

    override suspend fun upsertBatch(items: List<CacheDiskMetadata>) = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext

        mutex.withLock {
            db.beginTransaction()
            try {
                items.forEach { meta ->
                    validateMetadata(meta)
                    val values = meta.toContentValues()
                    db.insertWithOnConflict("cache_metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun updateAccessTime(key: String, date: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            db.execSQL(
                "UPDATE cache_metadata SET last_accessed_at = ? WHERE `key` = ?",
                arrayOf<Any>(date, key)
            )
        }
    }

    override suspend fun updateAccessTimeBatch(keys: List<String>, date: Long) = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) return@withContext

        mutex.withLock {
            db.beginTransaction()
            try {
                keys.forEach { key ->
                    db.execSQL(
                        "UPDATE cache_metadata SET last_accessed_at = ? WHERE `key` = ?",
                        arrayOf<Any>(date, key)
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun delete(key: String): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            db.delete("cache_metadata", "`key` = ?", arrayOf(key))
        }
    }

    override suspend fun deleteBatch(keys: List<String>) = withContext(Dispatchers.IO) {
        if (keys.isEmpty()) return@withContext

        mutex.withLock {
            db.beginTransaction()
            try {
                keys.forEach { key ->
                    db.delete("cache_metadata", "`key` = ?", arrayOf(key))
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    override suspend fun clearAll(): Unit = withContext(Dispatchers.IO) {
        mutex.withLock {
            db.delete("cache_metadata", null, null)
        }
    }

    fun close() {
        dbHelper.close()
    }

    private fun readRow(cursor: Cursor) = CacheDiskMetadata(
        key = cursor.getString(0),
        size = cursor.getLong(1),
        createdAt = cursor.getLong(2),
        lastAccessedAt = cursor.getLong(3),
        expirationDate = cursor.getLong(4)
    )

    private fun validateMetadata(meta: CacheDiskMetadata) {
        if (meta.size < 0) throw CacheMetadataError.InvalidSize("Size must be non-negative")
        if (meta.createdAt <= 0) throw CacheMetadataError.InvalidDate("Created date must be positive")
        if (meta.lastAccessedAt < meta.createdAt)
            throw CacheMetadataError.InvalidMetadata("Last access cannot be before creation")
        if (meta.expirationDate <= meta.createdAt)
            throw CacheMetadataError.InvalidMetadata("Expiration must be after creation")
    }

    private class DBHelper(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS cache_metadata (
                    `key` TEXT PRIMARY KEY,
                    size INTEGER NOT NULL CHECK(size >= 0),
                    created_at INTEGER NOT NULL CHECK(created_at > 0),
                    last_accessed_at INTEGER NOT NULL CHECK(last_accessed_at > 0),
                    expiration_date INTEGER NOT NULL CHECK(expiration_date > 0),
                    CHECK(last_accessed_at >= created_at),
                    CHECK(expiration_date > created_at)
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expiration ON cache_metadata(expiration_date)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_lru ON cache_metadata(last_accessed_at)")
        }
        override fun onUpgrade(db: SQLiteDatabase, old: Int, new: Int) {
            db.execSQL("DROP TABLE IF EXISTS cache_metadata")
            onCreate(db)
        }
    }
}

private fun CacheDiskMetadata.toContentValues() = ContentValues().apply {
    put("key", key)
    put("size", size)
    put("created_at", createdAt)
    put("last_accessed_at", lastAccessedAt)
    put("expiration_date", expirationDate)
}
