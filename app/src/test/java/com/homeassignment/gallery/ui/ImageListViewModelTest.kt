package com.homeassignment.gallery.ui

import com.homeassignment.gallery.data.ImageCacheInvalidator
import com.homeassignment.gallery.data.ImageItem
import com.homeassignment.gallery.data.ImageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ImageListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val images = listOf(ImageItem(0, "https://example.com/0.jpg"), ImageItem(1, "https://example.com/1.jpg"))

    private var failNextLoad = false
    private val repository = object : ImageRepository {
        override suspend fun getImages(): List<ImageItem> {
            if (failNextLoad) {
                failNextLoad = false
                throw IOException("offline")
            }
            return images
        }
    }
    private var invalidations = 0
    private val invalidator = ImageCacheInvalidator { invalidations++ }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads images on creation`() = runTest(dispatcher) {
        val viewModel = ImageListViewModel(repository, invalidator)
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.hasError)
        assertEquals(images, state.images)
    }

    @Test
    fun `shows error and recovers on retry`() = runTest(dispatcher) {
        failNextLoad = true
        val viewModel = ImageListViewModel(repository, invalidator)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasError)

        viewModel.loadImages()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasError)
        assertEquals(images, viewModel.uiState.value.images)
    }

    @Test
    fun `clearing the cache invalidates images and notifies the ui`() = runTest(dispatcher) {
        val viewModel = ImageListViewModel(repository, invalidator)
        advanceUntilIdle()

        viewModel.clearImageCache()
        assertTrue(viewModel.uiState.value.isClearingCache)
        advanceUntilIdle()

        assertEquals(1, invalidations)
        assertFalse(viewModel.uiState.value.isClearingCache)
        assertEquals(ImageListEvent.CacheCleared, viewModel.events.first())
    }
}
