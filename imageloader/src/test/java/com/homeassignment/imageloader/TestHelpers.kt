package com.homeassignment.imageloader

import android.graphics.Bitmap
import android.graphics.Color
import com.homeassignment.imageloader.internal.Clock
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

internal class FakeClock(var time: Long = 1_700_000_000_000L) : Clock {
    override fun now(): Long = time
}

/** Serves a generated PNG for every URL and counts downloads per URL. */
internal class FakeFetcher(
    width: Int = 400,
    height: Int = 200,
    private val beforeWrite: suspend (url: String) -> Unit = {},
) : Fetcher {
    private val png: ByteArray = createPng(width, height)
    private val counts = HashMap<String, AtomicInteger>()

    override suspend fun fetch(url: String, destination: File) {
        synchronized(counts) { counts.getOrPut(url) { AtomicInteger() } }.incrementAndGet()
        beforeWrite(url)
        destination.writeBytes(png)
    }

    fun fetchCount(url: String): Int = synchronized(counts) { counts[url]?.get() ?: 0 }

    private companion object {
        fun createPng(width: Int, height: Int): ByteArray {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.RED)
            return ByteArrayOutputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                it.toByteArray()
            }
        }
    }
}
