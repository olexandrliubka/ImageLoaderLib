package com.homeassignment.imageloader.internal

import com.homeassignment.imageloader.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class DiskCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val clock = FakeClock()
    private val ttl = 4 * 60 * 60 * 1000L

    private fun cache(maxSize: Long = Long.MAX_VALUE) =
        DiskCache(tempFolder.root, maxSize, ttl, clock)

    @Test
    fun `stored file is returned until it expires`() = runTest {
        val cache = cache()
        cache.put(URL_A) { it.writeText("a") }

        clock.time += ttl - 1
        assertEquals("a", cache[URL_A]?.readText())

        clock.time += 1
        assertNull(cache[URL_A])
    }

    @Test
    fun `expired file is deleted on access`() = runTest {
        val cache = cache()
        val file = cache.put(URL_A) { it.writeText("a") }

        clock.time += ttl
        cache[URL_A]

        assertFalse(file.exists())
    }

    @Test
    fun `remove only affects the given url`() = runTest {
        val cache = cache()
        cache.put(URL_A) { it.writeText("a") }
        cache.put(URL_B) { it.writeText("b") }

        cache.remove(URL_A)

        assertNull(cache[URL_A])
        assertNotNull(cache[URL_B])
    }

    @Test
    fun `clear removes everything`() = runTest {
        val cache = cache()
        cache.put(URL_A) { it.writeText("a") }
        cache.put(URL_B) { it.writeText("b") }

        cache.clear()

        assertNull(cache[URL_A])
        assertNull(cache[URL_B])
    }

    @Test
    fun `failed write leaves no file behind`() = runTest {
        val cache = cache()

        val result = runCatching {
            cache.put(URL_A) {
                it.writeText("partial")
                throw IOException("connection reset")
            }
        }

        assertTrue(result.isFailure)
        assertNull(cache[URL_A])
        assertEquals(0, tempFolder.root.listFiles()!!.size)
    }

    @Test
    fun `oldest entries are evicted when over max size`() = runTest {
        val cache = cache(maxSize = 10)
        cache.put(URL_A) { it.writeText("123456") }
        clock.time += 1_000
        cache.put(URL_B) { it.writeText("123456") }

        assertNull(cache[URL_A])
        assertNotNull(cache[URL_B])
    }

    private companion object {
        const val URL_A = "https://example.com/a.jpg"
        const val URL_B = "https://example.com/b.jpg"
    }
}
