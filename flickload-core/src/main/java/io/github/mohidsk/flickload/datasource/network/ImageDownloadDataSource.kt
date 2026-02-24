package io.github.mohidsk.flickload.datasource.network

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.annotation.RestrictTo
import io.github.mohidsk.flickload.image.ImageFormat
import io.github.mohidsk.flickload.image.ImageProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.chromium.net.CronetEngine
import org.chromium.net.CronetException
import org.chromium.net.UrlRequest
import org.chromium.net.UrlResponseInfo
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "FlickLoad"
private const val MAX_CONCURRENT_PIPELINE = 50
private const val DEFAULT_CHUNK_SIZE = 100
private const val CRONET_READ_BUFFER_SIZE = 65536

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class ImageDownloadDataSource(
    private val cronetEngine: CronetEngine?,
    private val imageProcessor: ImageProcessor,
    private val cronetExecutor: Executor = Executors.newFixedThreadPool(4)
) {
    private val activeDownloads = ConcurrentHashMap<String, Deferred<ImageDownloadResult?>>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val useCronet: Boolean get() = cronetEngine != null

    private val okHttpClient: OkHttpClient by lazy { buildOkHttpClient() }

    companion object {
        fun buildCronetEngine(context: Context): CronetEngine? {
            return try {
                CronetEngine.Builder(context)
                    .enableQuic(true)
                    .enableHttp2(true)
                    .enableBrotli(true)
                    .build()
            } catch (_: Exception) {
                null
            }
        }

        fun detectFormat(bytes: ByteArray, contentType: String? = null): ImageFormat {
            if (bytes.size >= 4) {
                val magicResult = when {
                    bytes[0] == 0xFF.toByte() &&
                            bytes[1] == 0xD8.toByte() &&
                            bytes[2] == 0xFF.toByte() -> ImageFormat.JPEG

                    bytes[0] == 0x89.toByte() &&
                            bytes[1] == 0x50.toByte() &&
                            bytes[2] == 0x4E.toByte() &&
                            bytes[3] == 0x47.toByte() -> ImageFormat.PNG

                    bytes[0] == 0x47.toByte() &&
                            bytes[1] == 0x49.toByte() &&
                            bytes[2] == 0x46.toByte() &&
                            bytes[3] == 0x38.toByte() -> ImageFormat.GIF

                    bytes.size >= 12 &&
                            bytes[0] == 0x52.toByte() &&
                            bytes[1] == 0x49.toByte() &&
                            bytes[2] == 0x46.toByte() &&
                            bytes[3] == 0x46.toByte() &&
                            bytes[8] == 0x57.toByte() &&
                            bytes[9] == 0x45.toByte() &&
                            bytes[10] == 0x42.toByte() &&
                            bytes[11] == 0x50.toByte() -> ImageFormat.WEBP

                    // AVIF / HEIC: ISO Base Media File Format (ftyp box)
                    bytes.size >= 12 &&
                            bytes[4] == 0x66.toByte() &&
                            bytes[5] == 0x74.toByte() &&
                            bytes[6] == 0x79.toByte() &&
                            bytes[7] == 0x70.toByte() -> {
                        val brand = String(bytes, 8, 4, Charsets.US_ASCII)
                        when {
                            brand == "avif" || brand == "avis" -> ImageFormat.AVIF
                            brand == "heic" || brand == "heix" || brand == "mif1" -> ImageFormat.HEIC
                            else -> null
                        }
                    }

                    else -> null
                }

                if (magicResult != null) return magicResult
            }

            if (contentType != null) {
                val fromHeader = ImageFormat.fromContentType(contentType)
                if (fromHeader != ImageFormat.UNKNOWN) return fromHeader
            }

            return ImageFormat.UNKNOWN
        }
    }

    private fun buildOkHttpClient(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 200
            maxRequestsPerHost = 100
        }

        return OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .dispatcher(dispatcher)
            .connectionPool(ConnectionPool(50, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private suspend fun downloadBytesCronet(url: String): Pair<ByteArray, String?>? {
        val engine = cronetEngine ?: return null

        return suspendCancellableCoroutine { cont ->
            val buffer = ByteArrayOutputStream()

            val callback = object : UrlRequest.Callback() {
                private var contentType: String? = null

                override fun onRedirectReceived(
                    request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String
                ) {
                    request.followRedirect()
                }

                override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) {
                    if (info.httpStatusCode !in 200..299) {
                        request.cancel()
                        return
                    }
                    contentType = info.allHeaders["Content-Type"]?.firstOrNull()
                    request.read(ByteBuffer.allocateDirect(CRONET_READ_BUFFER_SIZE))
                }

                override fun onReadCompleted(
                    request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer
                ) {
                    byteBuffer.flip()
                    val bytes = ByteArray(byteBuffer.remaining())
                    byteBuffer.get(bytes)
                    buffer.write(bytes)
                    byteBuffer.clear()
                    request.read(byteBuffer)
                }

                override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                    if (cont.isActive) {
                        cont.resume(Pair(buffer.toByteArray(), contentType))
                    }
                }

                override fun onFailed(
                    request: UrlRequest, info: UrlResponseInfo?, error: CronetException
                ) {
                    Log.w(TAG, "Cronet download error: ${error.message}")
                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }

                override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) {
                    if (cont.isActive) {
                        cont.resume(null)
                    }
                }
            }

            val request = engine.newUrlRequestBuilder(url, callback, cronetExecutor)
                .setPriority(UrlRequest.Builder.REQUEST_PRIORITY_MEDIUM)
                .build()

            cont.invokeOnCancellation { request.cancel() }
            request.start()
        }
    }

    private suspend fun executeAsyncOkHttp(request: Request): Response {
        return suspendCancellableCoroutine { cont ->
            val call = okHttpClient.newCall(request)
            cont.invokeOnCancellation { call.cancel() }

            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    cont.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        cont.resumeWithException(e)
                    }
                }
            })
        }
    }

    private suspend fun downloadBytesOkHttp(url: String): Pair<ByteArray, String?>? {
        return try {
            val request = Request.Builder().url(url).build()
            val response = executeAsyncOkHttp(request)

            response.use { resp ->
                if (!resp.isSuccessful) return@use null
                val contentType = resp.header("Content-Type")
                val bytes = resp.body?.bytes() ?: return@use null
                Pair(bytes, contentType)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadImage(url: String): ImageDownloadResult? {
        activeDownloads[url]?.let { existing ->
            return try { existing.await() } catch (_: Exception) { null }
        }

        val newDeferred = scope.async {
            downloadAndDecode(url)
        }

        val existing = activeDownloads.putIfAbsent(url, newDeferred)
        if (existing != null) {
            newDeferred.cancel()
            return try { existing.await() } catch (_: Exception) { null }
        }

        return try {
            newDeferred.await()
        } catch (_: Exception) {
            null
        } finally {
            activeDownloads.remove(url)
        }
    }

    private suspend fun downloadAndDecode(url: String): ImageDownloadResult? {
        return try {
            val result = if (useCronet) {
                downloadBytesCronet(url)
            } else {
                downloadBytesOkHttp(url)
            } ?: return null

            val (bytes, contentType) = result
            val format = detectFormat(bytes, contentType)

            val bitmap = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inMutable = true }
            ) ?: return null

            ImageDownloadResult(bitmap, format, bytes)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadImagesWithPipeline(urls: List<String>): List<ImageDownloadResult?> = coroutineScope {
        if (urls.size <= MAX_CONCURRENT_PIPELINE) {
            urls.map { url ->
                async(Dispatchers.Default) {
                    downloadAndDecode(url)
                }
            }.awaitAll()
        } else {
            val semaphore = Semaphore(MAX_CONCURRENT_PIPELINE)
            urls.map { url ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        downloadAndDecode(url)
                    }
                }
            }.awaitAll()
        }
    }

    suspend fun downloadImagesInChunksWithPipeline(
        urls: List<String>,
        chunkSize: Int = DEFAULT_CHUNK_SIZE
    ): List<ImageDownloadResult?> {
        val results = mutableListOf<ImageDownloadResult?>()
        for (chunk in urls.chunked(chunkSize)) {
            results.addAll(downloadImagesWithPipeline(chunk))
        }
        return results
    }

    fun cancelAllDownloads() {
        activeDownloads.values.forEach { it.cancel() }
        activeDownloads.clear()
    }
}
