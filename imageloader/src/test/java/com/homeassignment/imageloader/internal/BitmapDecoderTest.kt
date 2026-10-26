package com.homeassignment.imageloader.internal

import com.homeassignment.imageloader.internal.BitmapDecoder.Companion.calculateInSampleSize
import org.junit.Assert.assertEquals
import org.junit.Test

class BitmapDecoderTest {

    @Test
    fun `no downsampling when target size is unknown`() {
        assertEquals(1, calculateInSampleSize(1920, 1080, 0, 0))
    }

    @Test
    fun `no downsampling when target is larger than source`() {
        assertEquals(1, calculateInSampleSize(800, 600, 1600, 1200))
    }

    @Test
    fun `downsamples by largest power of two that still covers the target`() {
        // 1920x1080 / 4 = 480x270 still covers 400x225; / 8 = 240x135 would not.
        assertEquals(4, calculateInSampleSize(1920, 1080, 400, 225))
    }

    @Test
    fun `the tighter dimension limits downsampling`() {
        // Width alone would allow 8, but height only allows 2.
        assertEquals(2, calculateInSampleSize(1920, 1080, 200, 500))
    }
}
