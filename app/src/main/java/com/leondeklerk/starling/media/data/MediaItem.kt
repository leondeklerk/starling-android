package com.leondeklerk.starling.media.data

import android.net.Uri
import android.os.Parcelable
import androidx.recyclerview.widget.DiffUtil
import java.util.Date
import java.util.Objects
import kotlinx.parcelize.Parcelize

@Parcelize
enum class MediaItemTypes : Parcelable {
    HEADER, IMAGE, VIDEO, FOLDER
}

/**
 * Base class for gallery items.
 * This handles the [DiffUtil.Callback] for all gallery items based on the id of the item.
 * @param id: the id of this object, based on the MediaStore id for videos and images, date for Header objects.
 * @param type: The [MediaItemTypes] type indicates what type of item this is.
 * @param uri: The uri of the media item or null (header)
 */
sealed class MediaItem(
    open val id: Long,
    open val type: MediaItemTypes,
    open val uri: Uri?,
    open val dateModified: Date
) : Parcelable {

    companion object {
        // Handles the DiffUtil callback for gallery items.
        val DiffCallback = object : DiffUtil.ItemCallback<MediaItem>() {
            override fun areItemsTheSame(oldItem: MediaItem, newItem: MediaItem) =
                oldItem.id == newItem.id

            // TODO revert to date modified for gallery adapter and use this for fragment adapter
            override fun areContentsTheSame(oldItem: MediaItem, newItem: MediaItem) =
                oldItem.id == newItem.id
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
@Parcelize
data class HeaderItem(
    override val id: Long,
    val date: Date,
    val zoomLevel: Int
) : MediaItem(id, MediaItemTypes.HEADER, null, Date())

/**
 * [MediaItem] specific for image type, indicated by [MediaItemTypes.IMAGE].
 * @param uri: The uri which points to the image in storage
 * @param displayName: The name given to this image by the MediaStore
 * @param dateAdded: The date this image was added to the system
 * @param width: The pixel width of the image
 * @param height: The pixel height of the image
 * @param mimeType: the MIME type of the media item
 */
@Parcelize
data class ImageItem(
    override val id: Long,
    override val uri: Uri,
    val displayName: String,
    val dateAdded: Date,
    val width: Int,
    val height: Int,
    val mimeType: String,
    override val dateModified: Date
) : MediaItem(id, MediaItemTypes.IMAGE, uri, dateModified)

/**
 * [MediaItem] specific for video type, indicated by [MediaItemTypes.VIDEO].
 * @param uri: The uri which points to the video in storage
 * @param displayName: The name given to this video by the MediaStore
 * @param dateAdded: The date this video was added to the system
 * @param duration: The length of this video in milli.
 * @param mimeType: the MIME type of the media item
 */
@Parcelize
data class VideoItem(
    override val id: Long,
    override val uri: Uri,
    val displayName: String,
    val dateAdded: Date,
    val duration: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    override val dateModified: Date
) : MediaItem(id, MediaItemTypes.VIDEO, uri, dateModified)

/**
 */
@Parcelize
data class FolderItem(
    override val id: Long,
    override val uri: Uri,
    val name: String,
    val thumbnailType: MediaItemTypes,
    override val dateModified: Date
) : MediaItem(id, MediaItemTypes.FOLDER, uri, dateModified) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as FolderItem
        return name == that.name && id == that.id
    }

    override fun hashCode(): Int {
        return Objects.hash(name, id)
    }

    override fun toString(): String {
        return name
    }
}
