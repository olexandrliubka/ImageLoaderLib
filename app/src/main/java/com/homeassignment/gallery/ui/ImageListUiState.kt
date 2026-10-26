package com.homeassignment.gallery.ui

import com.homeassignment.gallery.data.ImageItem

data class ImageListUiState(
    val isLoading: Boolean = false,
    val images: List<ImageItem> = emptyList(),
    val hasError: Boolean = false,
    val isClearingCache: Boolean = false,
)

sealed interface ImageListEvent {
    /** The image cache was cleared; visible images should be reloaded. */
    data object CacheCleared : ImageListEvent
}
