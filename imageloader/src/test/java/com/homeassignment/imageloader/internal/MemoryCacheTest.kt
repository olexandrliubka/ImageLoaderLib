package com.homeassignment.imageloader.internal

import android.graphics.Bitmap
import com.homeassignment.imageloader.FakeClock
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MemoryCacheTest {

    private val clock = FakeClock()
    private val ttl = 1_000L
    private val cache = MemoryCache(maxSizeBytes = 10 * 1024 * 1024, ttlMillis = ttl, clock = clock)
    private val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

    @Test
    fun `entry expires ttl after its creation time`() {
        val key = MemoryKey(URL, 10, 10)
        cache.put(key, bitmap, createdAtMillis = clock.time - 400)

        assertSame(bitmap, cache[key])
        clock.time += 600
        assertNull(cache[key])
    }

    @Test
    fun `remove drops every size of a url`() {
        cache.put(MemoryKey(URL, 10, 10), bitmap, clock.time)
        cache.put(MemoryKey(URL, 20, 20), bitmap, clock.time)
        cache.put(MemoryKey(OTHER_URL, 10, 10), bitmap, clock.time)

        cache.remove(URL)

        assertNull(cache[MemoryKey(URL, 10, 10)])
        assertNull(cache[MemoryKey(URL, 20, 20)])
        assertNotNull(cache[MemoryKey(OTHER_URL, 10, 10)])
    }

    @Test
    fun `clear empties the cache`() {
        val key = MemoryKey(URL, 10, 10)
        cache.put(key, bitmap, clock.time)

        cache.clear()

        assertNull(cache[key])
    }

    private companion object {
        const val URL = "https://example.com/a.jpg"
        const val OTHER_URL = "https://example.com/b.jpg"
    }
}
