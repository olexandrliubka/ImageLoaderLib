package com.homeassignment.imageloader.internal

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

internal class ImageRequest(
    val url: String?,
    val target: ImageView,
    val placeholder: DrawableSource?,
    val error: DrawableSource?,
)

/** A drawable given either as a resource or as an instance. */
internal class DrawableSource private constructor(
    @param:DrawableRes private val resId: Int,
    private val drawable: Drawable?,
) {
    fun resolve(context: Context): Drawable? = when {
        // A drawable instance must not be shared between views, since it keeps per-view state.
        drawable != null -> drawable.constantState?.newDrawable(context.resources)?.mutate() ?: drawable
        resId != 0 -> ContextCompat.getDrawable(context, resId)
        else -> null
    }

    companion object {
        fun of(@DrawableRes resId: Int): DrawableSource? = if (resId != 0) DrawableSource(resId, null) else null
        fun of(drawable: Drawable?): DrawableSource? = drawable?.let { DrawableSource(0, it) }
    }
}
