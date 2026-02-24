package io.github.mohidsk.flickload.datasource.local

import android.graphics.Bitmap
import androidx.annotation.RestrictTo
import io.github.mohidsk.flickload.cache.disk.CachedDiskStorageProtocol
import io.github.mohidsk.flickload.image.ImageFormat
import io.github.mohidsk.flickload.image.ImageProcessor
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

private const val BATCH_THRESHOLD = 40
private val MAX_CONCURRENT_ENCODING = Runtime.getRuntime().availableProcessors().let {
    minOf(16, maxOf(4, it - 1))
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ImageDiskCacheDataSource(
    private val cachedStorage: CachedDiskStorageProtocol<ByteArray>,
    private val imageProcessor: ImageProcessor
) {
    private val formatCache = ConcurrentHashMap<String, ImageFormat>()

    private fun getFormat(fileName: String): ImageFormat {
        return formatCache.getOrPut(fileName) {
            ImageFormat.fromFileName(fileName)
        }
    }

    suspend fun warmUp() {
        cachedStorage.warmUp()
    }

    suspend fun getImageAsync(fileName: String): Bitmap? {
        val data = cachedStorage.get(fileName) ?: return null

        return withContext(Dispatchers.Default) {
            imageProcessor.decompressSync(data)
        }
    }

    suspend fun saveRawBytes(fileName: String, bytes: ByteArray) {
        cachedStorage.save(fileName, bytes)
    }

    suspend fun saveBatchRawBytes(items: List<Pair<String, ByteArray>>) {
        if (items.isEmpty()) return
        cachedStorage.saveBatch(items)
    }

    suspend fun saveImage(image: Bitmap, fileName: String) {
        val imageData = withContext(Dispatchers.Default) {
            encodeImage(image, getFormat(fileName))
        } ?: return

        cachedStorage.save(fileName, imageData)
    }

    suspend fun saveBatchImages(images: List<Pair<String, Bitmap>>) {
        if (images.isEmpty()) return

        if (images.size < BATCH_THRESHOLD) {
            saveBatchImagesSmallBatch(images)
        } else {
            saveBatchImagesLargeBatch(images)
        }
    }

    private suspend fun saveBatchImagesSmallBatch(images: List<Pair<String, Bitmap>>) = coroutineScope {
        val encodedItems = images.map { (key, bitmap) ->
            async(Dispatchers.Default) {
                encodeImage(bitmap, getFormat(key))?.let { key to it }
            }
        }.awaitAll().filterNotNull()

        if (encodedItems.isEmpty()) return@coroutineScope

        cachedStorage.saveBatch(encodedItems)
    }

    private suspend fun saveBatchImagesLargeBatch(images: List<Pair<String, Bitmap>>) = coroutineScope {
        class WorkDistributor(
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

        val optimalWorkerCount = minOf(MAX_CONCURRENT_ENCODING, maxOf(1, images.size / 10))
        val distributor = WorkDistributor(images.size, optimalWorkerCount)

        val allEncodedItems = (0 until optimalWorkerCount).map {
            async(Dispatchers.Default) {
                val workerResults = mutableListOf<Pair<String, ByteArray>>()

                while (true) {
                    val batchRange = distributor.getNextBatch() ?: break

                    for (imageIndex in batchRange) {
                        try {
                            val (key, bitmap) = images[imageIndex]
                            val imageData = encodeImage(bitmap, getFormat(key)) ?: continue
                            workerResults.add(key to imageData)
                        } catch (_: Exception) { }
                    }
                }

                workerResults
            }
        }.awaitAll().flatten()

        if (allEncodedItems.isEmpty()) return@coroutineScope

        cachedStorage.saveBatch(allEncodedItems)
    }

    suspend fun getBatchRawBytes(keys: List<String>): Map<String, ByteArray> {
        if (keys.isEmpty()) return emptyMap()
        return cachedStorage.getBatch(keys)
    }

    suspend fun getBatchImages(keys: List<String>): Map<String, Bitmap> {
        if (keys.isEmpty()) return emptyMap()

        val data = cachedStorage.getBatch(keys)

        return coroutineScope {
            data.map { (key, bytes) ->
                async(Dispatchers.Default) {
                    imageProcessor.decompressSync(bytes)?.let { key to it }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    private fun encodeImage(image: Bitmap, format: ImageFormat): ByteArray? {
        return when (format) {
            ImageFormat.PNG -> {
                ByteArrayOutputStream().use { stream ->
                    image.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    stream.toByteArray()
                }
            }
            ImageFormat.JPEG -> {
                ByteArrayOutputStream().use { stream ->
                    image.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                    stream.toByteArray()
                }
            }
            ImageFormat.WEBP -> {
                ByteArrayOutputStream().use { stream ->
                    image.compress(Bitmap.CompressFormat.WEBP_LOSSY, 90, stream)
                    stream.toByteArray()
                }
            }
            ImageFormat.HEIC, ImageFormat.AVIF -> {
                ByteArrayOutputStream().use { stream ->
                    image.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, stream)
                    stream.toByteArray()
                }
            }
            ImageFormat.GIF -> {
                ByteArrayOutputStream().use { stream ->
                    image.compress(Bitmap.CompressFormat.PNG, 100, stream)
                    stream.toByteArray()
                }
            }
            ImageFormat.UNKNOWN -> {
                if (image.hasAlpha()) {
                    ByteArrayOutputStream().use { stream ->
                        image.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        stream.toByteArray()
                    }
                } else {
                    ByteArrayOutputStream().use { stream ->
                        image.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                        stream.toByteArray()
                    }
                }
            }
        }
    }

    suspend fun deleteImage(key: String) {
        cachedStorage.delete(key)
        formatCache.remove(key)
    }

    suspend fun clear() {
        cachedStorage.clearAll()
        formatCache.clear()
    }

    fun close() {
        (cachedStorage as? java.io.Closeable)?.close()
    }
}
