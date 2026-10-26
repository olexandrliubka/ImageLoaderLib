package com.homeassignment.gallery.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.homeassignment.gallery.R
import com.homeassignment.gallery.data.ImageItem
import com.homeassignment.gallery.databinding.ItemImageBinding
import com.homeassignment.imageloader.cancelImageLoad
import com.homeassignment.imageloader.load

class ImageAdapter : ListAdapter<ImageItem, ImageAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemImageBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: ViewHolder) {
        holder.binding.image.cancelImageLoad()
    }

    class ViewHolder(val binding: ItemImageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ImageItem) {
            binding.idText.text = binding.root.context.getString(R.string.image_id, item.id)
            binding.urlText.text = item.url.substringAfterLast('/')
            binding.image.load(item.url) {
                placeholder(R.drawable.image_placeholder)
                error(R.drawable.image_error)
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<ImageItem>() {
        override fun areItemsTheSame(oldItem: ImageItem, newItem: ImageItem) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: ImageItem, newItem: ImageItem) = oldItem == newItem
    }
}
