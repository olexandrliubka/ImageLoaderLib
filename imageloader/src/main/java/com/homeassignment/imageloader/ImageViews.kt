package com.homeassignment.imageloader

import android.widget.ImageView
import androidx.annotation.MainThread
import com.homeassignment.imageloader.internal.requestManagerOrNull

/**
 * Loads [url] into this view.
 *
 * ```
 * imageView.load(url) {
 *     placeholder(R.drawable.placeholder)
 *     error(R.drawable.error)
 * }
 * ```
 */
@JvmSynthetic
@MainThread
public fun ImageView.load(
    url: String?,
    imageLoader: ImageLoader = ImageLoader.get(context),
    builder: RequestBuilder.() -> Unit = {},
): Disposable = imageLoader.load(url).apply(builder).into(this)

/** Cancels the image request bound to this view, if any. */
@JvmSynthetic
@MainThread
public fun ImageView.cancelImageLoad() {
    requestManagerOrNull()?.clear()
}
