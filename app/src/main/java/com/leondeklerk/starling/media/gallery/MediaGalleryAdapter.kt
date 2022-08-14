package com.leondeklerk.starling.media.gallery

import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import com.leondeklerk.starling.media.data.MediaItem

/**
 * [ListAdapter] instance responsible for populating a gallery with [MediaItem]s.
 * The adapter makes uses of the generic class [MediaItemViewHolder] which has specific subtypes for each item type.
 * Responsible for creating the specific holders and binding the item to the holder.
 */
class MediaGalleryAdapter(private val onItemClick: ((MediaItem) -> Unit)) : ListAdapter<MediaItem,
    MediaItemViewHolder>(
    MediaItem.DiffCallback
) {

    override fun onBindViewHolder(holder: MediaItemViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaItemViewHolder {
        // Create one of the available viewHolders or throw an exception
        return when (viewType) {
            0 -> HeaderItemViewHolder.from(parent)
            1 -> ImageItemViewHolder.from(parent, onItemClick)
            2 -> VideoItemViewHolder.from(parent, onItemClick)
            3 -> FolderItemViewHolder.from(parent, onItemClick)
            else -> throw IllegalArgumentException()
        }
    }

    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        // Return the int value of the enum type MediaItemTypes
        return item.type.ordinal
    }
}
