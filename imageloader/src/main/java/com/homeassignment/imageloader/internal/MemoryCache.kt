package com.homeassignment.imageloader.internal

import android.graphics.Bitmap
import android.util.LruCache

internal data class MemoryKey(val url: String, val width: Int, val height: Int) {
    constructor(url: String, size: TargetSize) : this(url, size.width, size.height)
}

/** Size-bounded LRU of decoded bitmaps whose entries expire [ttlMillis] after creation. */
internal class MemoryCache(
    maxSizeBytes: Int,
    private val ttlMillis: Long,
    private val clock: Clock,
) {
    private class Entry(val bitmap: Bitmap, val createdAtMillis: Long)

    private val cache = object : LruCache<MemoryKey, Entry>(maxSizeBytes) {
        override fun sizeOf(key: MemoryKey, value: Entry): Int = value.bitmap.allocationByteCount
    }

    operator fun get(key: MemoryKey): Bitmap? {
        val entry = cache.get(key) ?: return null
        if (clock.now() - entry.createdAtMillis >= ttlMillis) {
            cache.remove(key)
            return null
        }
        return entry.bitmap
    }

    fun put(key: MemoryKey, bitmap: Bitmap, createdAtMillis: Long) {
        cache.put(key, Entry(bitmap, createdAtMillis))
    }

    fun remove(url: String) {
        cache.snapshot().keys.filter { it.url == url }.forEach { cache.remove(it) }
    }

    fun clear() {
        cache.evictAll()
    }
}
