package io.github.mohidsk.flickload.datasource.network

import io.github.mohidsk.flickload.image.ImageFormat
import org.junit.Assert.*
import org.junit.Test

class ImageFormatDetectionTest {

    @Test
    fun `detectFormat identifies JPEG from magic bytes`() {
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        assertEquals(ImageFormat.JPEG, ImageDownloadDataSource.detectFormat(jpegBytes))
    }

    @Test
    fun `detectFormat identifies PNG from magic bytes`() {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
        assertEquals(ImageFormat.PNG, ImageDownloadDataSource.detectFormat(pngBytes))
    }

    @Test
    fun `detectFormat identifies GIF from magic bytes`() {
        val gifBytes = byteArrayOf(0x47.toByte(), 0x49.toByte(), 0x46.toByte(), 0x38.toByte())
        assertEquals(ImageFormat.GIF, ImageDownloadDataSource.detectFormat(gifBytes))
    }

    @Test
    fun `detectFormat identifies WebP from magic bytes`() {
        val webpBytes = ByteArray(12)
        webpBytes[0] = 0x52 // R
        webpBytes[1] = 0x49 // I
        webpBytes[2] = 0x46 // F
        webpBytes[3] = 0x46 // F
        webpBytes[8] = 0x57 // W
        webpBytes[9] = 0x45 // E
        webpBytes[10] = 0x42 // B
        webpBytes[11] = 0x50 // P
        assertEquals(ImageFormat.WEBP, ImageDownloadDataSource.detectFormat(webpBytes))
    }

    @Test
    fun `detectFormat identifies AVIF from ftyp box`() {
        val avifBytes = ByteArray(12)
        avifBytes[4] = 0x66 // 'f'
        avifBytes[5] = 0x74 // 't'
        avifBytes[6] = 0x79 // 'y'
        avifBytes[7] = 0x70 // 'p'
        avifBytes[8] = 0x61 // 'a'
        avifBytes[9] = 0x76 // 'v'
        avifBytes[10] = 0x69 // 'i'
        avifBytes[11] = 0x66 // 'f'
        assertEquals(ImageFormat.AVIF, ImageDownloadDataSource.detectFormat(avifBytes))
    }

    @Test
    fun `detectFormat identifies AVIF avis brand`() {
        val avisBytes = ByteArray(12)
        avisBytes[4] = 0x66 // 'f'
        avisBytes[5] = 0x74 // 't'
        avisBytes[6] = 0x79 // 'y'
        avisBytes[7] = 0x70 // 'p'
        avisBytes[8] = 0x61 // 'a'
        avisBytes[9] = 0x76 // 'v'
        avisBytes[10] = 0x69 // 'i'
        avisBytes[11] = 0x73 // 's'
        assertEquals(ImageFormat.AVIF, ImageDownloadDataSource.detectFormat(avisBytes))
    }

    @Test
    fun `detectFormat identifies HEIC from ftyp box`() {
        val heicBytes = ByteArray(12)
        heicBytes[4] = 0x66 // 'f'
        heicBytes[5] = 0x74 // 't'
        heicBytes[6] = 0x79 // 'y'
        heicBytes[7] = 0x70 // 'p'
        heicBytes[8] = 0x68 // 'h'
        heicBytes[9] = 0x65 // 'e'
        heicBytes[10] = 0x69 // 'i'
        heicBytes[11] = 0x63 // 'c'
        assertEquals(ImageFormat.HEIC, ImageDownloadDataSource.detectFormat(heicBytes))
    }

    @Test
    fun `detectFormat identifies HEIC heix brand`() {
        val heixBytes = ByteArray(12)
        heixBytes[4] = 0x66 // 'f'
        heixBytes[5] = 0x74 // 't'
        heixBytes[6] = 0x79 // 'y'
        heixBytes[7] = 0x70 // 'p'
        heixBytes[8] = 0x68 // 'h'
        heixBytes[9] = 0x65 // 'e'
        heixBytes[10] = 0x69 // 'i'
        heixBytes[11] = 0x78 // 'x'
        assertEquals(ImageFormat.HEIC, ImageDownloadDataSource.detectFormat(heixBytes))
    }

    @Test
    fun `detectFormat identifies HEIC mif1 brand`() {
        val mif1Bytes = ByteArray(12)
        mif1Bytes[4] = 0x66 // 'f'
        mif1Bytes[5] = 0x74 // 't'
        mif1Bytes[6] = 0x79 // 'y'
        mif1Bytes[7] = 0x70 // 'p'
        mif1Bytes[8] = 0x6D // 'm'
        mif1Bytes[9] = 0x69 // 'i'
        mif1Bytes[10] = 0x66 // 'f'
        mif1Bytes[11] = 0x31 // '1'
        assertEquals(ImageFormat.HEIC, ImageDownloadDataSource.detectFormat(mif1Bytes))
    }

    @Test
    fun `detectFormat falls back to content-type`() {
        val unknownBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertEquals(ImageFormat.JPEG, ImageDownloadDataSource.detectFormat(unknownBytes, "image/jpeg"))
        assertEquals(ImageFormat.PNG, ImageDownloadDataSource.detectFormat(unknownBytes, "image/png"))
        assertEquals(ImageFormat.WEBP, ImageDownloadDataSource.detectFormat(unknownBytes, "image/webp"))
    }

    @Test
    fun `detectFormat returns UNKNOWN when nothing matches`() {
        val unknownBytes = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        assertEquals(ImageFormat.UNKNOWN, ImageDownloadDataSource.detectFormat(unknownBytes))
    }

    @Test
    fun `detectFormat returns UNKNOWN for empty bytes`() {
        assertEquals(ImageFormat.UNKNOWN, ImageDownloadDataSource.detectFormat(byteArrayOf()))
    }

    @Test
    fun `detectFormat magic bytes take priority over content-type`() {
        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        // Even with PNG content-type, magic bytes say JPEG
        assertEquals(ImageFormat.JPEG, ImageDownloadDataSource.detectFormat(jpegBytes, "image/png"))
    }

    @Test
    fun `detectFormat handles short byte arrays gracefully`() {
        assertEquals(ImageFormat.UNKNOWN, ImageDownloadDataSource.detectFormat(byteArrayOf(0xFF.toByte())))
        assertEquals(ImageFormat.UNKNOWN, ImageDownloadDataSource.detectFormat(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
    }
}
