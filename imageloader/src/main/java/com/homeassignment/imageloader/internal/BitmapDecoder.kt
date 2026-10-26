package com.homeassignment.imageloader.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Decodes image files, downsampling them to the smallest size that still covers the target. */
internal class BitmapDecoder {

    fun decode(file: File, size: TargetSize): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, size.width, size.height)
        }
        return BitmapFactory.decodeFile(file.path, options)
    }

    companion object {
        /**
         * Largest power of two that keeps the decoded image at least [reqWidth] x [reqHeight], so it
         * can still fill the target (e.g. with `centerCrop`) without upscaling.
         */
        fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
            if (reqWidth <= 0 || reqHeight <= 0) return 1
            var sampleSize = 1
            while (srcWidth / (sampleSize * 2) >= reqWidth && srcHeight / (sampleSize * 2) >= reqHeight) {
                sampleSize *= 2
            }
            return sampleSize
        }
    }
}
