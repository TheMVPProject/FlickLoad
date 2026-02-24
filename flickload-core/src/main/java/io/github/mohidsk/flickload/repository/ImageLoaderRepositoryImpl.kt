package io.github.mohidsk.flickload.repository

import android.graphics.Bitmap
import androidx.annotation.RestrictTo
import io.github.mohidsk.flickload.FlickLoadImageLoader
import io.github.mohidsk.flickload.crypto.CacheKeyGenerator
import io.github.mohidsk.flickload.datasource.local.ImageDiskCacheDataSource
import io.github.mohidsk.flickload.datasource.local.ImageMemoryCacheDataSource
import io.github.mohidsk.flickload.datasource.network.ImageDownloadDataSource
import io.github.mohidsk.flickload.image.ImageLoadResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val LARGE_BATCH_THRESHOLD = 200

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ImageLoaderRepositoryImpl(
    private val memoryCache: ImageMemoryCacheDataSource,
    private val diskCache: ImageDiskCacheDataSource,
    private val downloadDataSource: ImageDownloadDataSource
) : FlickLoadImageLoader {

    private val prefetchLock = Any()
    private var nextBatchId = 0L
    private val activePrefetchJobs = mutableMapOf<Long, Job>()
    private val batchUrlKeys = mutableMapOf<Long, MutableSet<String>>()
    private val urlKeyToBatchIds = mutableMapOf<String, MutableSet<Long>>()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        scope.launch(Dispatchers.IO) {
            diskCache.warmUp()
        }
    }

    private fun cacheKey(key: String): String {
        return if (key.contains('/')) {
            CacheKeyGenerator.generateKey(key)
        } else {
            key
        }
    }

    private fun cleanupBatch(batchId: Long) {
        activePrefetchJobs.remove(batchId)
        batchUrlKeys.remove(batchId)?.forEach { urlKey ->
            urlKeyToBatchIds[urlKey]?.let { batchIds ->
                batchIds.remove(batchId)
                if (batchIds.isEmpty()) {
                    urlKeyToBatchIds.remove(urlKey)
                }
            }
        }
    }

    override fun getImageFromMemorySync(key: String): Bitmap? {
        return memoryCache.getImageSync(cacheKey(key))
    }

    override suspend fun getImageFromMemory(key: String): Bitmap? {
        return memoryCache.getImage(cacheKey(key))
    }

    override suspend fun getImage(url: String): Bitmap? {
        val filename = cacheKey(url)
        memoryCache.getImage(filename)?.let { return it }
        return loadFromDiskOrNetwork(url, filename)
    }

    override suspend fun getImageWithResult(url: String): ImageLoadResult {
        return try {
            val filename = cacheKey(url)
            memoryCache.getImage(filename)?.let {
                return ImageLoadResult.Success(it, ImageLoadResult.Source.MEMORY)
            }

            diskCache.getImageAsync(filename)?.let { diskImage ->
                memoryCache.cacheImage(diskImage, filename)
                return ImageLoadResult.Success(diskImage, ImageLoadResult.Source.DISK)
            }

            val result = downloadDataSource.downloadImage(url)
                ?: return ImageLoadResult.Error(Exception("Download failed for: $url"))

            memoryCache.cacheImage(result.bitmap, filename)
            diskCache.saveRawBytes(filename, result.rawBytes)

            ImageLoadResult.Success(result.bitmap, ImageLoadResult.Source.NETWORK)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ImageLoadResult.Error(e)
        }
    }

    override fun getImageAsFlow(url: String): Flow<ImageLoadResult> = flow {
        emit(ImageLoadResult.Loading)
        emit(getImageWithResult(url))
    }

    private suspend fun loadFromDiskOrNetwork(url: String, fileName: String): Bitmap? {
        diskCache.getImageAsync(fileName)?.let { diskImage ->
            memoryCache.cacheImage(diskImage, fileName)
            return diskImage
        }

        val result = downloadDataSource.downloadImage(url) ?: return null
        val downloadedImage = result.bitmap

        memoryCache.cacheImage(downloadedImage, fileName)
        diskCache.saveRawBytes(fileName, result.rawBytes)

        return downloadedImage
    }

    override suspend fun getBatchImagesFromMemory(urls: List<String>): Map<String, Bitmap> {
        if (urls.isEmpty()) return emptyMap()

        val keys = CacheKeyGenerator.generateKeys(urls)
        return memoryCache.getBatchImages(keys)
    }

    override suspend fun getImages(urls: List<String>): Map<String, Bitmap> {
        if (urls.isEmpty()) return emptyMap()

        waitForPrefetch(urls)

        val keys = CacheKeyGenerator.generateKeys(urls)
        val keyToUrl = HashMap<String, String>(urls.size)
        for (i in keys.indices) {
            keyToUrl[keys[i]] = urls[i]
        }

        val results = memoryCache.getBatchImages(keys).toMutableMap()

        val missingKeys = keys.filter { results[it] == null }
        if (missingKeys.isNotEmpty()) {
            val diskResults = diskCache.getBatchImages(missingKeys)
            memoryCache.cacheBatchImages(diskResults)
            results.putAll(diskResults)
        }

        val stillMissingKeys = keys.filter { results[it] == null }
        if (stillMissingKeys.isNotEmpty()) {
            val urlsToDownload = stillMissingKeys.mapNotNull { keyToUrl[it] }
            val keysForDownload = stillMissingKeys.filter { keyToUrl[it] != null }
            val downloadedResults = downloadBatchAndCache(urlsToDownload, keysForDownload)
            results.putAll(downloadedResults)
        }

        return results
    }

    override suspend fun getImagesWithTiming(urls: List<String>): Pair<Map<String, Bitmap>, String> {
        if (urls.isEmpty()) return emptyMap<String, Bitmap>() to "No URLs"

        val timings = StringBuilder()
        var t0 = System.nanoTime()

        waitForPrefetch(urls)

        val keys = CacheKeyGenerator.generateKeys(urls)
        val keyToUrl = HashMap<String, String>(urls.size)
        for (i in keys.indices) { keyToUrl[keys[i]] = urls[i] }
        var t1 = System.nanoTime()
        timings.append("Key gen: ${(t1 - t0) / 1_000_000}ms\n")

        t0 = System.nanoTime()
        val results = memoryCache.getBatchImages(keys).toMutableMap()
        t1 = System.nanoTime()
        timings.append("Memory check: ${(t1 - t0) / 1_000_000}ms (${results.size} hits)\n")

        t0 = System.nanoTime()
        val missingKeys = keys.filter { results[it] == null }
        if (missingKeys.isNotEmpty()) {
            val diskResults = diskCache.getBatchImages(missingKeys)
            memoryCache.cacheBatchImages(diskResults)
            results.putAll(diskResults)
            t1 = System.nanoTime()
            timings.append("Disk check: ${(t1 - t0) / 1_000_000}ms (${diskResults.size} hits)\n")
        } else {
            timings.append("Disk check: skipped (all in memory)\n")
        }

        t0 = System.nanoTime()
        val stillMissingKeys = keys.filter { results[it] == null }
        if (stillMissingKeys.isNotEmpty()) {
            val urlsToDownload = stillMissingKeys.mapNotNull { keyToUrl[it] }
            val keysForDownload = stillMissingKeys.filter { keyToUrl[it] != null }
            val downloadedResults = downloadBatchAndCache(urlsToDownload, keysForDownload)
            results.putAll(downloadedResults)
            t1 = System.nanoTime()
            timings.append("Download+decode: ${(t1 - t0) / 1_000_000}ms (${downloadedResults.size} images)\n")
        } else {
            timings.append("Download: skipped (all cached)\n")
        }

        return results to timings.toString()
    }

    private suspend fun downloadBatchAndCache(
        urls: List<String>,
        precomputedKeys: List<String>
    ): Map<String, Bitmap> {
        if (urls.isEmpty()) return emptyMap()

        val downloadResults = if (urls.size > LARGE_BATCH_THRESHOLD) {
            downloadDataSource.downloadImagesInChunksWithPipeline(urls)
        } else {
            downloadDataSource.downloadImagesWithPipeline(urls)
        }

        val results = mutableMapOf<String, Bitmap>()
        val imagesToMemory = mutableListOf<Pair<String, Bitmap>>()
        val rawBytesToDisk = mutableListOf<Pair<String, ByteArray>>()

        urls.forEachIndexed { index, _ ->
            downloadResults.getOrNull(index)?.let { downloadResult ->
                val key = precomputedKeys[index]
                results[key] = downloadResult.bitmap
                imagesToMemory.add(key to downloadResult.bitmap)
                rawBytesToDisk.add(key to downloadResult.rawBytes)
            }
        }

        if (imagesToMemory.isNotEmpty()) {
            memoryCache.cacheBatchImages(imagesToMemory)

            scope.launch {
                diskCache.saveBatchRawBytes(rawBytesToDisk)
            }
        }

        return results
    }

    override fun cacheImage(image: Bitmap, url: String) {
        val key = cacheKey(url)

        scope.launch {
            memoryCache.cacheImage(image, key)
            diskCache.saveImage(image, key)
        }
    }

    override fun prefetchImages(urls: List<String>) {
        if (urls.isEmpty()) return

        val urlKeys = CacheKeyGenerator.generateKeys(urls).toSet()

        synchronized(prefetchLock) {
            val batchId = nextBatchId++

            batchUrlKeys[batchId] = urlKeys.toMutableSet()
            for (urlKey in urlKeys) {
                urlKeyToBatchIds.getOrPut(urlKey) { mutableSetOf() }.add(batchId)
            }

            val task = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    prefetchBatch(urls)
                } finally {
                    synchronized(prefetchLock) {
                        cleanupBatch(batchId)
                    }
                }
            }

            activePrefetchJobs[batchId] = task
            task.start()
        }
    }

    private suspend fun prefetchBatch(urls: List<String>) {
        if (urls.isEmpty()) return

        val keys = CacheKeyGenerator.generateKeys(urls)
        val urlToKey = HashMap<String, String>(urls.size).also {
            for (i in urls.indices) it[urls[i]] = keys[i]
        }

        val cachedImages = memoryCache.getBatchImages(keys)
        val urlsToFetch = urls.zip(keys)
            .filter { cachedImages[it.second] == null }
            .map { it.first }

        if (urlsToFetch.isEmpty()) return

        val keysToFetch = urlsToFetch.map { urlToKey[it]!! }
        val diskBytes = diskCache.getBatchRawBytes(keysToFetch)

        if (diskBytes.isNotEmpty()) {
            memoryCache.cacheBatchBytes(diskBytes)

            scope.launch {
                memoryCache.getBatchImages(diskBytes.keys.toList())
            }
        }

        val stillMissingPairs = urlsToFetch.zip(keysToFetch)
            .filter { diskBytes[it.second] == null }

        if (stillMissingPairs.isEmpty()) return

        val stillMissingUrls = stillMissingPairs.map { it.first }
        val stillMissingKeys = stillMissingPairs.map { it.second }

        val downloadResults = if (stillMissingUrls.size > LARGE_BATCH_THRESHOLD) {
            downloadDataSource.downloadImagesInChunksWithPipeline(stillMissingUrls)
        } else {
            downloadDataSource.downloadImagesWithPipeline(stillMissingUrls)
        }
        val imagesToMemory = mutableListOf<Pair<String, Bitmap>>()
        val rawBytesToDisk = mutableListOf<Pair<String, ByteArray>>()

        stillMissingUrls.forEachIndexed { index, _ ->
            downloadResults.getOrNull(index)?.let { downloadResult ->
                val key = stillMissingKeys[index]
                imagesToMemory.add(key to downloadResult.bitmap)
                rawBytesToDisk.add(key to downloadResult.rawBytes)
            }
        }

        if (imagesToMemory.isNotEmpty()) {
            memoryCache.cacheBatchImages(imagesToMemory)

            scope.launch {
                diskCache.saveBatchRawBytes(rawBytesToDisk)
            }
        }
    }

    override fun cancelPrefetch(urls: List<String>) {
        val urlKeys = CacheKeyGenerator.generateKeys(urls).toSet()

        synchronized(prefetchLock) {
            val batchIdsToCancel = mutableSetOf<Long>()
            for (urlKey in urlKeys) {
                urlKeyToBatchIds[urlKey]?.let { batchIdsToCancel.addAll(it) }
            }

            for (batchId in batchIdsToCancel) {
                activePrefetchJobs[batchId]?.cancel()
                cleanupBatch(batchId)
            }
        }
    }

    override fun cancelAllPrefetching() {
        synchronized(prefetchLock) {
            activePrefetchJobs.values.forEach { it.cancel() }
            activePrefetchJobs.clear()
            batchUrlKeys.clear()
            urlKeyToBatchIds.clear()
        }
    }

    override fun getActivePrefetchCount(): Int {
        return synchronized(prefetchLock) { activePrefetchJobs.size }
    }

    override suspend fun waitForPrefetch(urls: List<String>): Boolean {
        val urlKeys = CacheKeyGenerator.generateKeys(urls).toSet()

        val jobsToWait: List<Job>
        synchronized(prefetchLock) {
            val batchIds = mutableSetOf<Long>()
            for (urlKey in urlKeys) {
                urlKeyToBatchIds[urlKey]?.let { batchIds.addAll(it) }
            }
            jobsToWait = batchIds.mapNotNull { activePrefetchJobs[it] }
        }

        return if (jobsToWait.isNotEmpty()) {
            jobsToWait.forEach { it.join() }
            true
        } else {
            false
        }
    }

    override suspend fun clearMemoryCache() {
        memoryCache.clear()
    }

    override suspend fun clearAllCaches() {
        cancelAllPrefetching()
        downloadDataSource.cancelAllDownloads()
        memoryCache.clear()
        diskCache.clear()
    }

    override fun dispose() {
        cancelAllPrefetching()
        downloadDataSource.cancelAllDownloads()
        diskCache.close()
        scope.cancel()
    }
}
