
package com.leondeklerk.starling.gallery

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.leondeklerk.starling.gallery.data.GalleryItem
import com.leondeklerk.starling.gallery.ui.GalleryItemViewHolder

class GalleryAdapter : ListAdapter<GalleryItem, GalleryItemViewHolder>(GalleryItem.DiffCallback) {
    override fun onBindViewHolder(holder: GalleryItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryItemViewHolder {
        return GalleryItemViewHolder.from(parent)
    }

    override fun getItemViewType(position: Int): Int {
        return super.getItemViewType(position)
    }
}
