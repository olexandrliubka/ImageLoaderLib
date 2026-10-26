package com.homeassignment.imageloader.internal

import android.view.View
import android.view.ViewTreeObserver
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Size to decode an image at; [width] or [height] of 0 means "original size". */
internal data class TargetSize(val width: Int, val height: Int)

/** The view's size if it is already known without waiting for a layout pass, else `null`. */
internal fun View.knownSizeOrNull(): TargetSize? {
    if (isLayoutRequested) return null
    val width = resolveDimension(width, layoutParams?.width) ?: return null
    val height = resolveDimension(height, layoutParams?.height) ?: return null
    return TargetSize(width, height)
}

/** Waits for the next layout pass and returns the view's size, falling back to the screen size. */
internal suspend fun View.awaitSize(): TargetSize = suspendCancellableCoroutine { continuation ->
    val observer = viewTreeObserver
    val listener = object : ViewTreeObserver.OnPreDrawListener {
        private var resumed = false

        override fun onPreDraw(): Boolean {
            removePreDrawListener(observer, this)
            if (!resumed) {
                resumed = true
                continuation.resume(sizeAfterLayout())
            }
            return true
        }
    }
    observer.addOnPreDrawListener(listener)
    continuation.invokeOnCancellation { removePreDrawListener(observer, listener) }
}

private fun View.sizeAfterLayout(): TargetSize {
    val display = resources.displayMetrics
    return TargetSize(
        width = resolveDimension(width, layoutParams?.width) ?: display.widthPixels,
        height = resolveDimension(height, layoutParams?.height) ?: display.heightPixels,
    )
}

private fun View.removePreDrawListener(observer: ViewTreeObserver, listener: ViewTreeObserver.OnPreDrawListener) {
    // An observer obtained before the view was attached is replaced on attach.
    if (observer.isAlive) observer.removeOnPreDrawListener(listener)
    else viewTreeObserver.removeOnPreDrawListener(listener)
}

/** Exact layout-param sizes win; MATCH_PARENT/WRAP_CONTENT are negative and need the measured size. */
private fun resolveDimension(measured: Int, layoutParam: Int?): Int? = when {
    layoutParam != null && layoutParam > 0 -> layoutParam
    measured > 0 -> measured
    else -> null
}

