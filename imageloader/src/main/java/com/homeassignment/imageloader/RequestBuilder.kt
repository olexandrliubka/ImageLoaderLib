package com.homeassignment.imageloader

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.MainThread
import com.homeassignment.imageloader.internal.DrawableSource
import com.homeassignment.imageloader.internal.ImageRequest

/** Fluent request builder returned by [ImageLoader.load]. */
public class RequestBuilder internal constructor(
    private val loader: ImageLoader,
    private val url: String?,
) {
    private var placeholder: DrawableSource? = null
    private var error: DrawableSource? = null

    /** Shown while the image is loading. */
    public fun placeholder(@DrawableRes resId: Int): RequestBuilder = apply {
        placeholder = DrawableSource.of(resId)
    }

    /** Shown while the image is loading. */
    public fun placeholder(drawable: Drawable?): RequestBuilder = apply {
        placeholder = DrawableSource.of(drawable)
    }

    /** Shown if the image fails to load. If not set, the placeholder stays visible. */
    public fun error(@DrawableRes resId: Int): RequestBuilder = apply {
        error = DrawableSource.of(resId)
    }

    /** Shown if the image fails to load. If not set, the placeholder stays visible. */
    public fun error(drawable: Drawable?): RequestBuilder = apply {
        error = DrawableSource.of(drawable)
    }

    /**
     * Loads the image into [view], replacing any request previously bound to it. The request is
     * paused while the view is detached from its window and resumed when it is re-attached.
     */
    @MainThread
    public fun into(view: ImageView): Disposable =
        loader.enqueue(ImageRequest(url, view, placeholder, error))
}
