package io.github.mohidsk.flickload.benchmark

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mohidsk.flickload.storage.DiskStorage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class DiskStorageBenchmark {

    @get:Rule
    val benchmarkRule = BenchmarkRule()

    private lateinit var storage: DiskStorage
    private val testData1KB = ByteArray(1024) { it.toByte() }
    private val testData100KB = ByteArray(100 * 1024) { it.toByte() }
    private val testData1MB = ByteArray(1024 * 1024) { it.toByte() }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        storage = DiskStorage(context, "benchmark_disk_cache")
    }

    @After
    fun tearDown() {
        runBlocking { storage.clear() }
    }

    @Test
    fun write_1KB() {
        var i = 0
        benchmarkRule.measureRepeated {
            runBlocking { storage.write("bench_1kb_${i++}", testData1KB) }
        }
    }

    @Test
    fun write_100KB() {
        var i = 0
        benchmarkRule.measureRepeated {
            runBlocking { storage.write("bench_100kb_${i++}", testData100KB) }
        }
    }

    @Test
    fun write_1MB() {
        var i = 0
        benchmarkRule.measureRepeated {
            runBlocking { storage.write("bench_1mb_${i++}", testData1MB) }
        }
    }

    @Test
    fun read_1KB() {
        runBlocking { storage.write("read_key_1kb", testData1KB) }
        benchmarkRule.measureRepeated {
            runBlocking { storage.read("read_key_1kb") }
        }
    }

    @Test
    fun read_100KB() {
        runBlocking { storage.write("read_key_100kb", testData100KB) }
        benchmarkRule.measureRepeated {
            runBlocking { storage.read("read_key_100kb") }
        }
    }

    @Test
    fun read_1MB() {
        runBlocking { storage.write("read_key_1mb", testData1MB) }
        benchmarkRule.measureRepeated {
            runBlocking { storage.read("read_key_1mb") }
        }
    }

    @Test
    fun batchWrite_10items_1KB() {
        var batch = 0
        benchmarkRule.measureRepeated {
            val items = (1..10).map { "batch_${batch}_$it" to testData1KB }
            runBlocking { storage.writeBatch(items) }
            batch++
        }
    }

    @Test
    fun batchWrite_50items_1KB() {
        var batch = 0
        benchmarkRule.measureRepeated {
            val items = (1..50).map { "batch50_${batch}_$it" to testData1KB }
            runBlocking { storage.writeBatch(items) }
            batch++
        }
    }

    @Test
    fun batchRead_10items() {
        val keys = (1..10).map { "batchread_$it" }
        runBlocking {
            keys.forEach { storage.write(it, testData1KB) }
        }
        benchmarkRule.measureRepeated {
            runBlocking { storage.readBatchParallel(keys) }
        }
    }

    @Test
    fun batchRead_50items() {
        val keys = (1..50).map { "batchread50_$it" }
        runBlocking {
            keys.forEach { storage.write(it, testData1KB) }
        }
        benchmarkRule.measureRepeated {
            runBlocking { storage.readBatchParallel(keys) }
        }
    }
}
