package io.github.mohidsk.flickload.benchmark

import android.graphics.Bitmap
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import io.github.mohidsk.flickload.cache.memory.CacheMemoryConfiguration
import io.github.mohidsk.flickload.cache.memory.CachedMemoryStorage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class MemoryCacheBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var storage: CachedMemoryStorage<Bitmap>
    private lateinit var testBitmap: Bitmap

    @Before
    fun setUp() {
        val config = CacheMemoryConfiguration(
            criticalMemoryMB = 50,
            hotMemoryMB = 50,
            warmMemoryMB = 50
        )
        storage = CachedMemoryStorage(config)
        testBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        // Pre-populate cache
        for (i in 1..500) {
            storage.putSync("key_$i", Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888))
        }
    }

    @Test
    fun syncPut() {
        var i = 0
        benchmarkRule.measureRepeated {
            storage.putSync("bench_${i++}", testBitmap)
        }
    }

    @Test
    fun syncGet_hit() {
        storage.putSync("hit_key", testBitmap)
        benchmarkRule.measureRepeated {
            storage.getSync("hit_key")
        }
    }

    @Test
    fun syncGet_miss() {
        benchmarkRule.measureRepeated {
            storage.getSync("nonexistent_key")
        }
    }
}
