package io.github.mohidsk.flickload.image

import org.junit.Assert.*
import org.junit.Test

class ImageFormatTest {

    @Test
    fun `fromExtension returns correct format for known extensions`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromExtension("jpg"))
        assertEquals(ImageFormat.PNG, ImageFormat.fromExtension("png"))
        assertEquals(ImageFormat.WEBP, ImageFormat.fromExtension("webp"))
        assertEquals(ImageFormat.GIF, ImageFormat.fromExtension("gif"))
        assertEquals(ImageFormat.AVIF, ImageFormat.fromExtension("avif"))
        assertEquals(ImageFormat.HEIC, ImageFormat.fromExtension("heic"))
    }

    @Test
    fun `fromExtension is case insensitive`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromExtension("JPG"))
        assertEquals(ImageFormat.PNG, ImageFormat.fromExtension("PNG"))
        assertEquals(ImageFormat.WEBP, ImageFormat.fromExtension("WEBP"))
    }

    @Test
    fun `fromExtension returns UNKNOWN for unrecognized`() {
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.fromExtension("bmp"))
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.fromExtension("tiff"))
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.fromExtension(""))
    }

    @Test
    fun `fromFileName extracts extension correctly`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromFileName("photo.jpg"))
        assertEquals(ImageFormat.PNG, ImageFormat.fromFileName("avatar.png"))
        assertEquals(ImageFormat.WEBP, ImageFormat.fromFileName("image.webp"))
    }

    @Test
    fun `fromFileName handles no extension`() {
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.fromFileName("noext"))
    }

    @Test
    fun `fromFileName handles multiple dots`() {
        assertEquals(ImageFormat.PNG, ImageFormat.fromFileName("my.photo.file.png"))
    }

    @Test
    fun `fromContentType parses MIME types correctly`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromContentType("image/jpeg"))
        assertEquals(ImageFormat.PNG, ImageFormat.fromContentType("image/png"))
        assertEquals(ImageFormat.WEBP, ImageFormat.fromContentType("image/webp"))
        assertEquals(ImageFormat.GIF, ImageFormat.fromContentType("image/gif"))
        assertEquals(ImageFormat.AVIF, ImageFormat.fromContentType("image/avif"))
        assertEquals(ImageFormat.HEIC, ImageFormat.fromContentType("image/heic"))
    }

    @Test
    fun `fromContentType handles charset parameters`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromContentType("image/jpeg; charset=utf-8"))
    }

    @Test
    fun `fromContentType is case insensitive`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromContentType("Image/JPEG"))
    }

    @Test
    fun `fromContentType returns UNKNOWN for unrecognized`() {
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.fromContentType("text/html"))
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.fromContentType("application/json"))
    }

    @Test
    fun `contentType returns correct MIME type`() {
        assertEquals("image/jpeg", ImageFormat.JPEG.contentType)
        assertEquals("image/png", ImageFormat.PNG.contentType)
        assertEquals("image/webp", ImageFormat.WEBP.contentType)
        assertEquals("image/gif", ImageFormat.GIF.contentType)
        assertEquals("image/avif", ImageFormat.AVIF.contentType)
        assertEquals("image/heic", ImageFormat.HEIC.contentType)
        assertEquals("application/octet-stream", ImageFormat.UNKNOWN.contentType)
    }

    @Test
    fun `supportsTransparency is correct`() {
        assertTrue(ImageFormat.PNG.supportsTransparency)
        assertTrue(ImageFormat.WEBP.supportsTransparency)
        assertTrue(ImageFormat.GIF.supportsTransparency)
        assertTrue(ImageFormat.AVIF.supportsTransparency)
        assertFalse(ImageFormat.JPEG.supportsTransparency)
        assertFalse(ImageFormat.HEIC.supportsTransparency)
        assertFalse(ImageFormat.UNKNOWN.supportsTransparency)
    }

    @Test
    fun `isLossless is only true for PNG`() {
        assertTrue(ImageFormat.PNG.isLossless)
        assertFalse(ImageFormat.JPEG.isLossless)
        assertFalse(ImageFormat.WEBP.isLossless)
        assertFalse(ImageFormat.GIF.isLossless)
    }

    @Test
    fun `extension property returns correct values`() {
        assertEquals("jpg", ImageFormat.JPEG.extension)
        assertEquals("png", ImageFormat.PNG.extension)
        assertEquals("webp", ImageFormat.WEBP.extension)
        assertEquals("gif", ImageFormat.GIF.extension)
        assertEquals("avif", ImageFormat.AVIF.extension)
        assertEquals("heic", ImageFormat.HEIC.extension)
        assertEquals("unknown", ImageFormat.UNKNOWN.extension)
    }
}
