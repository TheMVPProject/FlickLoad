package io.github.mohidsk.flickload.image

/**
 * Configuration for FlickLoad's cache tiers.
 *
 * @param criticalMemoryMB Memory budget (MB) for the highest-priority LRU tier.
 * @param hotMemoryMB Memory budget (MB) for the hot LRU tier.
 * @param warmMemoryMB Memory budget (MB) for the warm LRU tier.
 * @param diskExpirationDays Number of days before disk cache entries expire.
 * @param maxDiskCacheSizeMB Maximum disk cache size in MB.
 */
data class ImageCacheConfiguration(
    val criticalMemoryMB: Int = 100,
    val hotMemoryMB: Int = 150,
    val warmMemoryMB: Int = 330,
    val diskExpirationDays: Int = 30,
    val maxDiskCacheSizeMB: Long = 2000
) {
    companion object {
        /** Default configuration for normal devices. */
        val default = ImageCacheConfiguration()

        /** Reduced configuration auto-selected on [android.app.ActivityManager.isLowRamDevice] devices. */
        val lowMemory = ImageCacheConfiguration(
            criticalMemoryMB = 50,
            hotMemoryMB = 75,
            warmMemoryMB = 165,
            maxDiskCacheSizeMB = 1000
        )
    }
}
