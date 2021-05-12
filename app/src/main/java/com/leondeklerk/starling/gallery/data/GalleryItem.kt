package com.leondeklerk.starling.gallery.data

import android.net.Uri
import androidx.recyclerview.widget.DiffUtil

/**
 * Class containing the needed information for an item in the gallery.
 * TODO: Make galleryItem sealed and create HeaderItem, ImageItem, VideoItem data classes
 */
data class GalleryItem(
    val id: Long,
    val displayName: String,
    //val dateAdded: Date,
    val contentUri: Uri
) {
    companion object {
        // Handles the DiffUtil callback for gallery items.
        val DiffCallback = object : DiffUtil.ItemCallback<GalleryItem>() {
            override fun areItemsTheSame(oldItem: GalleryItem, newItem: GalleryItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: GalleryItem, newItem: GalleryItem) =
                oldItem == newItem
        }
    }
}

