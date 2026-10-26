package com.homeassignment.gallery.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.homeassignment.gallery.GalleryApplication
import com.homeassignment.gallery.data.ImageCacheInvalidator
import com.homeassignment.gallery.data.ImageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ImageListViewModel(
    private val repository: ImageRepository,
    private val imageCache: ImageCacheInvalidator,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageListUiState())
    val uiState: StateFlow<ImageListUiState> = _uiState.asStateFlow()

    private val _events = Channel<ImageListEvent>(Channel.BUFFERED)
    val events: Flow<ImageListEvent> = _events.receiveAsFlow()

    init {
        loadImages()
    }

    fun loadImages() {
        if (_uiState.value.isLoading) return
        _uiState.update { it.copy(isLoading = true, hasError = false) }
        viewModelScope.launch {
            try {
                val images = repository.getImages()
                _uiState.update { it.copy(isLoading = false, images = images) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load image list", e)
                _uiState.update { it.copy(isLoading = false, hasError = true) }
            }
        }
    }

    fun clearImageCache() {
        if (_uiState.value.isClearingCache) return
        _uiState.update { it.copy(isClearingCache = true) }
        viewModelScope.launch {
            try {
                imageCache.invalidateAll()
                _events.send(ImageListEvent.CacheCleared)
            } finally {
                _uiState.update { it.copy(isClearingCache = false) }
            }
        }
    }

    companion object {
        private const val TAG = "ImageListViewModel"

        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as GalleryApplication).container
                ImageListViewModel(container.imageRepository, container.imageCacheInvalidator)
            }
        }
    }
}
