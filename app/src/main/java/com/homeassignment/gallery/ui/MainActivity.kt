package com.homeassignment.gallery.ui

import android.graphics.Rect
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.homeassignment.gallery.R
import com.homeassignment.gallery.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val viewModel: ImageListViewModel by viewModels { ImageListViewModel.Factory }
    private lateinit var binding: ActivityMainBinding
    private val adapter = ImageAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.imageList.layoutManager =
            GridLayoutManager(this, resources.getInteger(R.integer.image_grid_span_count))
        binding.imageList.adapter = adapter
        binding.clearCacheButton.setOnClickListener { viewModel.clearImageCache() }
        binding.retryButton.setOnClickListener { viewModel.loadImages() }
        applyWindowInsets()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.uiState.collect(::render) }
                launch { viewModel.events.collect(::handleEvent) }
            }
        }
    }

    private fun render(state: ImageListUiState) {
        adapter.submitList(state.images)
        binding.progress.isVisible = state.isLoading && state.images.isEmpty()
        binding.errorGroup.isVisible = state.hasError && state.images.isEmpty()
        binding.clearCacheButton.isEnabled = !state.isClearingCache
    }

    private fun handleEvent(event: ImageListEvent) {
        when (event) {
            ImageListEvent.CacheCleared -> {
                // Rebinding makes every item request its image again, now from the network.
                adapter.notifyItemRangeChanged(0, adapter.itemCount)
                Snackbar.make(binding.root, R.string.cache_cleared, Snackbar.LENGTH_SHORT)
                    .setAnchorView(binding.clearCacheButton)
                    .show()
            }
        }
    }

    private fun applyWindowInsets() {
        val list = binding.imageList
        val listPadding = Rect(list.paddingLeft, list.paddingTop, list.paddingRight, list.paddingBottom)
        val fabMargin = (binding.clearCacheButton.layoutParams as ViewGroup.MarginLayoutParams).bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(list) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updatePadding(
                left = listPadding.left + bars.left,
                right = listPadding.right + bars.right,
                bottom = listPadding.bottom + bars.bottom,
            )
            insets
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.clearCacheButton) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = fabMargin + bars.bottom
                marginEnd = fabMargin + bars.right
            }
            insets
        }
    }
}
