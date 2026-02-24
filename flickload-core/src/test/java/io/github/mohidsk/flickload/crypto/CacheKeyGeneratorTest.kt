package io.github.mohidsk.flickload.crypto

import org.junit.Assert.*
import org.junit.Test

class CacheKeyGeneratorTest {

    @Test
    fun `generateKey produces consistent hash for same URL`() {
        val url = "https://cdn.example.com/images/avatar.png"
        val key1 = CacheKeyGenerator.generateKey(url)
        val key2 = CacheKeyGenerator.generateKey(url)
        assertEquals(key1, key2)
    }

    @Test
    fun `generateKey preserves file extension`() {
        val url = "https://cdn.example.com/images/avatar.png"
        val key = CacheKeyGenerator.generateKey(url)
        assertTrue("Key should end with .png", key.endsWith(".png"))
    }

    @Test
    fun `generateKey preserves jpg extension`() {
        val key = CacheKeyGenerator.generateKey("https://example.com/photo.jpg")
        assertTrue(key.endsWith(".jpg"))
    }

    @Test
    fun `generateKey preserves webp extension`() {
        val key = CacheKeyGenerator.generateKey("https://example.com/photo.webp")
        assertTrue(key.endsWith(".webp"))
    }

    @Test
    fun `generateKey strips query params from extension`() {
        val key = CacheKeyGenerator.generateKey("https://example.com/photo.jpg?width=200&quality=80")
        assertTrue("Key should end with .jpg, got: $key", key.endsWith(".jpg"))
    }

    @Test
    fun `generateKey produces different hashes for different URLs`() {
        val key1 = CacheKeyGenerator.generateKey("https://example.com/a.png")
        val key2 = CacheKeyGenerator.generateKey("https://example.com/b.png")
        assertNotEquals(key1, key2)
    }

    @Test
    fun `generateKey handles URL without extension`() {
        val key = CacheKeyGenerator.generateKey("https://example.com/api/image/12345")
        assertFalse("Key should not have a dot extension", key.contains("."))
    }

    @Test
    fun `generateKey ignores very long extension`() {
        val key = CacheKeyGenerator.generateKey("https://example.com/file.toolongextension")
        assertFalse("Key should not have long extension", key.endsWith(".toolongextension"))
    }

    @Test
    fun `generateKeys batch matches individual generation`() {
        val urls = listOf(
            "https://example.com/a.png",
            "https://example.com/b.jpg",
            "https://example.com/c.webp"
        )
        val batchKeys = CacheKeyGenerator.generateKeys(urls)
        val individualKeys = urls.map { CacheKeyGenerator.generateKey(it) }

        assertEquals(individualKeys, batchKeys)
    }

    @Test
    fun `generateKeys returns empty list for empty input`() {
        val keys = CacheKeyGenerator.generateKeys(emptyList())
        assertTrue(keys.isEmpty())
    }

    @Test
    fun `generateKey hash is 64 characters (SHA-256 hex)`() {
        val key = CacheKeyGenerator.generateKey("https://example.com/test")
        // No extension, so entire key is the hash
        assertEquals(64, key.length)
    }

    @Test
    fun `generateKey hash with extension is 64 + dot + ext`() {
        val key = CacheKeyGenerator.generateKey("https://example.com/test.png")
        // hash.png = 64 + 1 + 3 = 68
        assertEquals(68, key.length)
    }
}
