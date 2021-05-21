package com.leondeklerk.starling.gallery.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.leondeklerk.starling.databinding.GalleryItemViewBinding
import com.leondeklerk.starling.gallery.data.GalleryItem

/**
 * Basic [RecyclerView.ViewHolder] class that binds a [GalleryItem] and inflates the associated layout.
 * TODO: Extend this for the envisioned different GalleryItem data classes
 */
class GalleryItemViewHolder private constructor(private val binding: GalleryItemViewBinding) :
    RecyclerView.ViewHolder(binding.root) {

    /**
     * Bind a [GalleryItem] to the layout using databinding
     * @param item: the gallery item that should be displayed in this view.
     */
    fun bind(item: GalleryItem) {
        binding.galleryItem = item
        binding.executePendingBindings()
    }

    companion object {
        /**
         * Static function to create a new [GalleryItemViewHolder] and make sure its layout is inflated.
         * @param parent: The [ViewGroup] the context can be retrieved from
         * @return A [GalleryItemViewHolder] instance that can be populated with data.
         */
        fun from(parent: ViewGroup): GalleryItemViewHolder {
            val layoutInflater = LayoutInflater.from(parent.context)
            val binding = GalleryItemViewBinding.inflate(layoutInflater, parent, false)
            return GalleryItemViewHolder(binding)
        }
    }
}
