package io.github.mohidsk.flickload.storage

import android.content.Context
import androidx.annotation.RestrictTo
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
sealed class DiskStorageError : Exception() {
    object InvalidDirectory : DiskStorageError()
    object CreateDirectoryFailed : DiskStorageError()
    object ReadFailed : DiskStorageError()
    object WriteFailed : DiskStorageError()
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface DiskStorageProtocol {
    suspend fun read(key: String): ByteArray?
    suspend fun write(key: String, data: ByteArray)
    suspend fun delete(key: String)
    suspend fun deleteBatchParallel(keys: List<String>)
    suspend fun exists(key: String): Boolean
    suspend fun writeBatch(items: List<Pair<String, ByteArray>>)
    suspend fun readBatchParallel(keys: List<String>): Map<String, ByteArray>
    suspend fun listKeys(prefix: String? = null): List<String>
    suspend fun clear()
    suspend fun getSize(): Long
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class DiskStorage(
    context: Context,
    folderName: String
) : DiskStorageProtocol {

    private val directory: File
    private val parallelThreshold = 3
    private val mediumBatchThreshold = 32

    private class WorkDistributor(
        private val totalCount: Int,
        workerCount: Int
    ) {
        private val nextIndex = AtomicInteger(0)
        private val batchSize = maxOf(5, minOf(50, totalCount / (workerCount * 3)))

        fun getNextBatch(): IntRange? {
            while (true) {
                val start = nextIndex.get()
                if (start >= totalCount) return null

                val end = minOf(start + batchSize, totalCount)
                if (nextIndex.compareAndSet(start, end)) {
                    return start until end
                }
            }
        }
    }

    init {
        val baseDir = context.cacheDir
        directory = File(baseDir, folderName)

        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw DiskStorageError.CreateDirectoryFailed
            }
        }
    }

    override suspend fun read(key: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(directory, key)
        if (!file.exists()) return@withContext null

        try {
            if (file.length() > 1024 * 1024) {
                Files.newByteChannel(file.toPath()).use { channel ->
                    val buffer = ByteBuffer.allocate(file.length().toInt())
                    channel.read(buffer)
                    buffer.array()
                }
            } else {
                file.readBytes()
            }
        } catch (e: IOException) {
            throw DiskStorageError.ReadFailed
        }
    }

    override suspend fun write(key: String, data: ByteArray): Unit = withContext(Dispatchers.IO) {
        val file = File(directory, key)
        try {
            val tempFile = File(directory, "$key.tmp")
            tempFile.writeBytes(data)
            tempFile.renameTo(file)
        } catch (e: IOException) {
            throw DiskStorageError.WriteFailed
        }
    }

    override suspend fun delete(key: String): Unit = withContext(Dispatchers.IO) {
        val file = File(directory, key)
        if (file.exists()) {
            file.delete()
        }
    }

    override suspend fun deleteBatchParallel(keys: List<String>): Unit = coroutineScope {
        keys.map { key ->
            async(Dispatchers.IO) {
                val file = File(directory, key)
                if (file.exists()) {
                    file.delete()
                }
            }
        }.awaitAll()
    }

    override suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        File(directory, key).exists()
    }

    override suspend fun writeBatch(items: List<Pair<String, ByteArray>>) {
        when (items.size) {
            in 0..parallelThreshold -> writeSmallBatch(items)
            in (parallelThreshold + 1)..mediumBatchThreshold -> writeMediumBatch(items)
            else -> writeLargeBatch(items)
        }
    }

    private suspend fun writeSmallBatch(items: List<Pair<String, ByteArray>>) =
        withContext(Dispatchers.IO) {
            for ((key, data) in items) {
                val file = File(directory, key)
                try {
                    val tempFile = File(directory, "$key.tmp")
                    tempFile.writeBytes(data)
                    tempFile.renameTo(file)
                } catch (e: IOException) {
                    throw DiskStorageError.WriteFailed
                }
            }
        }

    private suspend fun writeMediumBatch(items: List<Pair<String, ByteArray>>) =
        coroutineScope {
            items.map { (key, data) ->
                async(Dispatchers.IO) {
                    val file = File(directory, key)
                    try {
                        val tempFile = File(directory, "$key.tmp")
                        tempFile.writeBytes(data)
                        tempFile.renameTo(file)
                    } catch (e: IOException) {
                        throw DiskStorageError.WriteFailed
                    }
                }
            }.awaitAll()
        }

    private suspend fun writeLargeBatch(items: List<Pair<String, ByteArray>>) =
        coroutineScope {
            val workerCount = calculateWorkerCount(items.size)
            val distributor = WorkDistributor(items.size, workerCount)

            val results = (0 until workerCount).map {
                async(Dispatchers.IO) {
                    var writeCount = 0

                    while (true) {
                        val batchRange = distributor.getNextBatch() ?: break

                        for (itemIndex in batchRange) {
                            val (key, data) = items[itemIndex]
                            val file = File(directory, key)

                            try {
                                val tempFile = File(directory, "$key.tmp")
                                tempFile.writeBytes(data)
                                tempFile.renameTo(file)
                                writeCount++
                            } catch (e: IOException) {
                                throw DiskStorageError.WriteFailed
                            }
                        }
                    }

                    writeCount
                }
            }

            val totalWritten = results.awaitAll().sum()

            if (totalWritten != items.size) {
                throw DiskStorageError.WriteFailed
            }
        }

    override suspend fun readBatchParallel(keys: List<String>): Map<String, ByteArray> {
        return when (keys.size) {
            in 0..parallelThreshold -> readSmallBatch(keys)
            in (parallelThreshold + 1)..mediumBatchThreshold -> readMediumBatch(keys)
            else -> readLargeBatch(keys)
        }
    }

    private suspend fun readSmallBatch(keys: List<String>): Map<String, ByteArray> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, ByteArray>()

            for (key in keys) {
                val file = File(directory, key)
                if (!file.exists()) continue

                try {
                    val data = if (file.length() > 1024 * 1024) {
                        Files.newByteChannel(file.toPath()).use { channel ->
                            val buffer = ByteBuffer.allocate(file.length().toInt())
                            channel.read(buffer)
                            buffer.array()
                        }
                    } else {
                        file.readBytes()
                    }
                    results[key] = data
                } catch (e: IOException) {
                    // Skip failed reads
                }
            }

            results
        }

    private suspend fun readMediumBatch(keys: List<String>): Map<String, ByteArray> =
        coroutineScope {
            val results = keys.map { key ->
                async(Dispatchers.IO) {
                    val file = File(directory, key)
                    if (!file.exists()) return@async null

                    try {
                        val data = if (file.length() > 1024 * 1024) {
                            Files.newByteChannel(file.toPath()).use { channel ->
                                val buffer = ByteBuffer.allocate(file.length().toInt())
                                channel.read(buffer)
                                buffer.array()
                            }
                        } else {
                            file.readBytes()
                        }
                        key to data
                    } catch (e: IOException) {
                        null
                    }
                }
            }.awaitAll().filterNotNull()

            results.toMap()
        }

    private suspend fun readLargeBatch(keys: List<String>): Map<String, ByteArray> =
        coroutineScope {
            val workerCount = calculateWorkerCount(keys.size)
            val distributor = WorkDistributor(keys.size, workerCount)

            val results = (0 until workerCount).map {
                async(Dispatchers.IO) {
                    val workerResults = mutableMapOf<String, ByteArray>()

                    while (true) {
                        val batchRange = distributor.getNextBatch() ?: break

                        for (keyIndex in batchRange) {
                            val key = keys[keyIndex]
                            val file = File(directory, key)

                            if (!file.exists()) continue

                            try {
                                val data = if (file.length() > 1024 * 1024) {
                                    Files.newByteChannel(file.toPath()).use { channel ->
                                        val buffer = ByteBuffer.allocate(
                                            file.length().toInt()
                                        )
                                        channel.read(buffer)
                                        buffer.array()
                                    }
                                } else {
                                    file.readBytes()
                                }
                                workerResults[key] = data
                            } catch (e: IOException) {
                                // Skip failed reads
                            }
                        }
                    }

                    workerResults
                }
            }.awaitAll()

            results.fold(mutableMapOf<String, ByteArray>()) { acc, map ->
                acc.putAll(map)
                acc
            }
        }

    override suspend fun listKeys(prefix: String?): List<String> = withContext(Dispatchers.IO) {
        val contents = directory.listFiles() ?: emptyArray()
        val keys = contents.map { it.name }

        if (prefix != null) {
            keys.filter { it.startsWith(prefix) }
        } else {
            keys
        }
    }

    override suspend fun clear(): Unit = withContext(Dispatchers.IO) {
        if (directory.exists()) {
            directory.deleteRecursively()
        }
        directory.mkdirs()
    }

    override suspend fun getSize(): Long = withContext(Dispatchers.IO) {
        val contents = directory.listFiles() ?: emptyArray()
        contents.sumOf { it.length() }
    }

    private fun calculateWorkerCount(itemCount: Int): Int {
        return when (itemCount) {
            in 0..20 -> minOf(itemCount, 10)
            in 21..50 -> 15
            in 51..100 -> 25
            in 101..200 -> 35
            in 201..500 -> 50
            in 501..1000 -> 60
            else -> 80
        }
    }
}
