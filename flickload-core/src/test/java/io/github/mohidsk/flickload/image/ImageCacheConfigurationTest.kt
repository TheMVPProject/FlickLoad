package io.github.mohidsk.flickload.image

import org.junit.Assert.*
import org.junit.Test

class ImageCacheConfigurationTest {

    @Test
    fun `default configuration has expected values`() {
        val config = ImageCacheConfiguration.default
        assertEquals(100, config.criticalMemoryMB)
        assertEquals(150, config.hotMemoryMB)
        assertEquals(330, config.warmMemoryMB)
        assertEquals(30, config.diskExpirationDays)
        assertEquals(2000L, config.maxDiskCacheSizeMB)
    }

    @Test
    fun `lowMemory configuration has reduced values`() {
        val config = ImageCacheConfiguration.lowMemory
        assertEquals(50, config.criticalMemoryMB)
        assertEquals(75, config.hotMemoryMB)
        assertEquals(165, config.warmMemoryMB)
        assertEquals(1000L, config.maxDiskCacheSizeMB)
    }

    @Test
    fun `lowMemory total memory is roughly half of default`() {
        val defaultTotal = with(ImageCacheConfiguration.default) {
            criticalMemoryMB + hotMemoryMB + warmMemoryMB
        }
        val lowTotal = with(ImageCacheConfiguration.lowMemory) {
            criticalMemoryMB + hotMemoryMB + warmMemoryMB
        }
        assertTrue("Low memory total ($lowTotal) should be about half default ($defaultTotal)",
            lowTotal.toDouble() / defaultTotal.toDouble() in 0.4..0.6)
    }

    @Test
    fun `custom configuration preserves values`() {
        val config = ImageCacheConfiguration(
            criticalMemoryMB = 25,
            hotMemoryMB = 50,
            warmMemoryMB = 100,
            diskExpirationDays = 7,
            maxDiskCacheSizeMB = 500
        )
        assertEquals(25, config.criticalMemoryMB)
        assertEquals(50, config.hotMemoryMB)
        assertEquals(100, config.warmMemoryMB)
        assertEquals(7, config.diskExpirationDays)
        assertEquals(500L, config.maxDiskCacheSizeMB)
    }
}
