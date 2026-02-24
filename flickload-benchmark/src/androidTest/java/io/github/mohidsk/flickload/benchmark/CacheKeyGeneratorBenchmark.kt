package io.github.mohidsk.flickload.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import io.github.mohidsk.flickload.crypto.CacheKeyGenerator
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CacheKeyGeneratorBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private val sampleUrl = "https://cdn.example.com/images/high-res/photo_2024_summer_vacation_beach.jpg?width=1920&quality=90"

    private val sampleUrls100 = (1..100).map {
        "https://cdn.example.com/images/photo_$it.jpg?w=800"
    }

    private val sampleUrls1000 = (1..1000).map {
        "https://cdn.example.com/images/photo_$it.jpg?w=800"
    }

    @Test
    fun singleKeyGeneration() {
        benchmarkRule.measureRepeated {
            CacheKeyGenerator.generateKey(sampleUrl)
        }
    }

    @Test
    fun batchKeyGeneration_100() {
        benchmarkRule.measureRepeated {
            CacheKeyGenerator.generateKeys(sampleUrls100)
        }
    }

    @Test
    fun batchKeyGeneration_1000() {
        benchmarkRule.measureRepeated {
            CacheKeyGenerator.generateKeys(sampleUrls1000)
        }
    }
}
