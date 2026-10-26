package com.homeassignment.gallery

import android.content.Context
import com.homeassignment.gallery.data.ImageCacheInvalidator
import com.homeassignment.gallery.data.ImageListApi
import com.homeassignment.gallery.data.ImageRepository
import com.homeassignment.gallery.data.RemoteImageRepository
import com.homeassignment.imageloader.ImageLoader

/** Manual dependency container scoped to the application. */
class AppContainer(context: Context) {

    private val imageLoader: ImageLoader = ImageLoader.get(context)

    val imageRepository: ImageRepository = RemoteImageRepository(ImageListApi())

    val imageCacheInvalidator = ImageCacheInvalidator { imageLoader.clearCache() }
}
