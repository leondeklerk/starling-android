package com.leondeklerk.starling.data

import android.net.Uri
import androidx.recyclerview.widget.DiffUtil
import java.util.Date

enum class MediaItemTypes {
    HEADER, IMAGE, VIDEO
}

/**
 * Base class for gallery items.
 * This handles the [DiffUtil.Callback] for all gallery items based on the id of the item.
 * @param id: the id of this object, based on the MediaStore id for videos and images, date for Header objects.
 * @param type: The [MediaItemTypes] type indicates what type of item this is.
 */
sealed class MediaItem(
    open val id: Long,
    open val type: MediaItemTypes
) {

    companion object {
        // Handles the DiffUtil callback for gallery items.
        val DiffCallback = object : DiffUtil.ItemCallback<MediaItem>() {
            override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem) =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem) =
                oldItem == newItem
        }
    }
}

/**
 * [MediaItem] specific item class for header objects in a gallery, indicated by [MediaItemTypes.HEADER]
 * A header objects groups a set of media under a specific date.
 * Multiple zoom levels:
 * 0: Year view
 * 1: Month view
 * 2: Day view
 * 3: Zoomed day view (larger items)
 * @param date: The date set of media items this header represents
 * @param zoomLevel: The level of zoom that the application is currently in.
 */
data class HeaderItem(
    override val id: Long,
    val date: Date,
    val zoomLevel: Int
) : MediaItem(id, MediaItemTypes.HEADER)

/**
 * [MediaItem] specific for image type, indicated by [MediaItemTypes.IMAGE].
 * @param displayName: The name given to this image by the MediaStore
 * @param dateAdded: The date this image was added to the system
 * @param contentUri: The uri which points to the image in storage
 * @param width: The pixel width of the image
 * @param height: The pixel height of the image
 */
data class ImageItem(
    override val id: Long,
    val displayName: String,
    val dateAdded: Date,
    val contentUri: Uri,
    val width: Number,
    val height: Number
) : MediaItem(id, MediaItemTypes.IMAGE)

/**
 * [MediaItem] specific for video type, indicated by [MediaItemTypes.VIDEO].
 * @param displayName: The name given to this video by the MediaStore
 * @param dateAdded: The date this video was added to the system
 * @param duration: The length of this video in milli.
 * @param contentUri: The uri which points to the video in storage
 */
data class VideoItem(
    override val id: Long,
    val displayName: String,
    val dateAdded: Date,
    val duration: Int,
    val contentUri: Uri,
) : MediaItem(id, MediaItemTypes.VIDEO)
