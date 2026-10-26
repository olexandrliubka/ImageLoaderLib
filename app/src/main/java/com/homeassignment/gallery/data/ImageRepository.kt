package com.homeassignment.gallery.data

interface ImageRepository {
    suspend fun getImages(): List<ImageItem>
}

class RemoteImageRepository(private val api: ImageListApi) : ImageRepository {
    override suspend fun getImages(): List<ImageItem> = api.fetchImages()
}

/** Clears every cached image, so the UI layer doesn't depend on the image loader directly. */
fun interface ImageCacheInvalidator {
    suspend fun invalidateAll()
}
