package com.leondeklerk.starling.gallery

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.leondeklerk.starling.data.ImageItem
import com.leondeklerk.starling.data.MediaItem
import com.leondeklerk.starling.data.VideoItem
import com.leondeklerk.starling.gallery.ui.GalleryHeaderViewHolder
import com.leondeklerk.starling.gallery.ui.GalleryImageViewHolder
import com.leondeklerk.starling.gallery.ui.GalleryItemViewHolder
import com.leondeklerk.starling.gallery.ui.GalleryVideoViewHolder

/**
 * [ListAdapter] instance responsible for populating a gallery with [MediaItem]s.
 * The adapter makes uses of the generic class [GalleryItemViewHolder] which has specific subtypes for each item type.
 * Responsible for creating the specific holders and binding the item to the holder.
 */
class GalleryAdapter : ListAdapter<MediaItem, GalleryItemViewHolder>(MediaItem.DiffCallback) {
    lateinit var onImageClick: ((ImageItem) -> Unit)
    lateinit var onVideoClick: ((VideoItem) -> Unit)

    override fun onBindViewHolder(holder: GalleryItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryItemViewHolder {
        // Create one of the available viewHolders or throw an exception
        return when (viewType) {
            0 -> GalleryHeaderViewHolder.from(parent)
            1 -> GalleryImageViewHolder.from(parent, onImageClick)
            2 -> GalleryVideoViewHolder.from(parent, onVideoClick)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        // Return the int value of the enum type MediaItemTypes
        return item.type.ordinal
    }
}
