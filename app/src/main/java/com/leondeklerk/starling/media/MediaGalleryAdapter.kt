package com.leondeklerk.starling.media

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.leondeklerk.starling.data.MediaItem

/**
 * [ListAdapter] instance responsible for populating a gallery with [MediaItem]s.
 * The adapter makes uses of the generic class [GalleryItemViewHolder] which has specific subtypes for each item type.
 * Responsible for creating the specific holders and binding the item to the holder.
 */
class MediaGalleryAdapter(private val onItemClick: ((MediaItem) -> Unit)) : ListAdapter<MediaItem,
    GalleryItemViewHolder>(
    MediaItem.DiffCallback
) {

    override fun onBindViewHolder(holder: GalleryItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryItemViewHolder {
        // Create one of the available viewHolders or throw an exception
        return when (viewType) {
            0 -> GalleryHeaderViewHolder.from(parent)
            1 -> GalleryImageViewHolder.from(parent, onItemClick)
            2 -> GalleryVideoViewHolder.from(parent, onItemClick)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        // Return the int value of the enum type MediaItemTypes
        return item.type.ordinal
    }
}
