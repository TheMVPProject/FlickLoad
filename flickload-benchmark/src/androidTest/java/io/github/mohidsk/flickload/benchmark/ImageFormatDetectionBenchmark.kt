package io.github.mohidsk.flickload.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import io.github.mohidsk.flickload.datasource.network.ImageDownloadDataSource
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class ImageFormatDetectionBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(1000)
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte()) + ByteArray(1000)
    private val unknownBytes = ByteArray(1000) { it.toByte() }

    @Test
    fun detectFormat_jpeg_magicBytes() {
        benchmarkRule.measureRepeated {
            ImageDownloadDataSource.detectFormat(jpegBytes)
        }
    }

    @Test
    fun detectFormat_png_magicBytes() {
        benchmarkRule.measureRepeated {
            ImageDownloadDataSource.detectFormat(pngBytes)
        }
    }

    @Test
    fun detectFormat_unknown_fallbackToContentType() {
        benchmarkRule.measureRepeated {
            ImageDownloadDataSource.detectFormat(unknownBytes, "image/webp")
        }
    }

    @Test
    fun detectFormat_unknown_noContentType() {
        benchmarkRule.measureRepeated {
            ImageDownloadDataSource.detectFormat(unknownBytes)
        }
    }
}
